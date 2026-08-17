#include "mex.h"
#include <array>
#include <atomic>
#include <cstddef>
#include <cstdint>
#include <cstring>
#include <string>
#include <thread>
#include <utility>

#ifdef _WIN32
  #include <windows.h>
#else
  #include <dlfcn.h>
#endif

// We set symbols to be hidden by default, but there are some macros in mex.h that break the
// MSVC macro. Thus, we manually fix MSVC in the cmake, and use the gnu macro for mingw.
#if defined(__GNUC__) || defined(__clang__)
#define MEX_EXPORT __attribute__((visibility("default")))
#else
#define MEX_EXPORT // msvc: __declspec(dllexport)
#endif

// Hashing function for turning names to integers (FNV-1a). The seed and prime are chosen
// such that there are no hash collisions within the known set.
// The hash is calculated directly on the raw UTF-16 data. C functions are limited to ASCII,
// so the upper bits are always zero and can be ignored.
inline uint32_t hash32_fnv1a(const mxChar* str, size_t len) {
    uint32_t hash = 0x811C9DC5;
    for (size_t i = 0; i < len; ++i) {
        hash = (hash ^ (uint32_t)((uint16_t) str[i] & 0xFF)) * 0x01000193;
    }
    return hash;
}

// mxGetScalar is undefined on an empty array and unpredictable on a struct
// or cell, so only classes it is actually defined for get through.
inline bool isNumericScalar(const mxArray* arg) {
    return (mxIsNumeric(arg) || mxIsLogical(arg) || mxIsChar(arg))
            && !mxIsComplex(arg) && mxGetNumberOfElements(arg) == 1;
}

template <typename T>
inline T getScalar(const mxArray* arg, const char* name) {
    if (!isNumericScalar(arg)) {
        mexErrMsgIdAndTxt("bindings_tests:TypeError", "Argument '%s' must be a numeric scalar.", name);
    }
    return static_cast<T>(mxGetScalar(arg));
}

// Only the generated wrappers can reach the gateway, so this guards against a generator
// bug rather than user error. Every case indexes prhs directly.
inline void checkArgCount(int nrhs, int expected, const char* name) {
    if (nrhs - 1 != expected) {
        mexErrMsgIdAndTxt("bindings_tests:InvalidInput", "'%s' expects %d argument(s), but received %d.",
                          name, expected, nrhs - 1);
    }
}

// Struct for getting error return information. Every call that can throw fills it.
// The char pointers are only valid until the current thread calls any other lib function.
struct ErrorInfo {
    const char* message;
    const char* id;
    int32_t code;
};

// mexErrMsgIdAndTxt immediately reports an error and jumps over the rest of the function.
inline void checkError(const ErrorInfo& error) {
    if (error.code != 0) {
        mexErrMsgIdAndTxt(error.id != nullptr ? error.id : "bindings_tests:Error",
                          "%s", error.message != nullptr ? error.message : "unknown error");
    }
}

template <typename T, typename S>
inline void copyData(T* dest, const void* src, size_t n) {
    auto s = static_cast<const S*>(src);
    for (size_t i = 0; i < n; ++i) dest[i] = static_cast<T>(s[i]);
}

template <typename T, mxClassID Category>
struct MatlabArray {
    T* ptr;
    mxArray* tmp = nullptr;

    explicit MatlabArray(const mxArray* arg) {
        if (mxIsComplex(arg)) {
            mexErrMsgIdAndTxt("bindings_tests:TypeError", "Complex arrays are not supported.");
        }

        if (mxGetClassID(arg) == Category) {
            // Perfect match: Zero-copy
            ptr = (T*)mxGetData(arg);
        } else {
            // Mismatch: Create a converted copy (like calllib does)
            tmp = mxCreateNumericMatrix(mxGetM(arg), mxGetN(arg), Category, mxREAL);
            ptr = (T*)mxGetData(tmp);
            size_t n = mxGetNumberOfElements(arg);

            // Convert types individually
            const void* srcData = mxGetData(arg);
            switch (mxGetClassID(arg)) {
                case mxDOUBLE_CLASS:  copyData<T, double>(ptr, srcData, n); break;
                case mxSINGLE_CLASS:  copyData<T, float>(ptr, srcData, n);  break;
                case mxINT8_CLASS:    copyData<T, int8_t>(ptr, srcData, n); break;
                case mxUINT8_CLASS:   copyData<T, uint8_t>(ptr, srcData, n); break;
                case mxINT16_CLASS:   copyData<T, int16_t>(ptr, srcData, n); break;
                case mxUINT16_CLASS:  copyData<T, uint16_t>(ptr, srcData, n); break;
                case mxINT32_CLASS:   copyData<T, int32_t>(ptr, srcData, n); break;
                case mxUINT32_CLASS:  copyData<T, uint32_t>(ptr, srcData, n); break;
                case mxINT64_CLASS:   copyData<T, int64_t>(ptr, srcData, n); break;
                case mxUINT64_CLASS:  copyData<T, uint64_t>(ptr, srcData, n); break;
                case mxLOGICAL_CLASS: copyData<T, bool>(ptr, srcData, n);    break;
                default: mexErrMsgTxt("Unsupported conversion type");
            }

        }
    }

    ~MatlabArray() { if (tmp) mxDestroyArray(tmp); }
    operator T*() { return ptr; }
};

inline char* charsToUtf8String(const mxArray* arg) {
    if (mxIsClass(arg, "string")) {
        mexErrMsgIdAndTxt("bindings_tests:TypeError", "Double-quoted strings are not supported. Use single quotes 'char' arrays.");
    } else if(!mxIsChar(arg)) {
        mexErrMsgIdAndTxt("bindings_tests:TypeError", "Expected single quotes 'char' array");
    }
    return mxArrayToUTF8String(arg);
}

// RAII cleanup for temporary utf8 strings
struct MatlabString {
    char* ptr;
    explicit MatlabString(const mxArray* arg) : ptr(charsToUtf8String(arg)) {}
    ~MatlabString() { if (ptr) mxFree(ptr); }
    operator const char*() const { return ptr; }
    MatlabString(const MatlabString&) = delete;
};

// RAII cleanup for temporary utf8 string arrays
struct MatlabStringArray {
    char** ptrs;
    size_t count;
    explicit MatlabStringArray(const mxArray* arg) {
        if (!mxIsCell(arg)) mexErrMsgIdAndTxt("bindings_tests:InvalidInput", "Expected cell array of strings.");
        count = mxGetNumberOfElements(arg);
        if (count == 0) {
            ptrs = nullptr;
            return;
        }
        ptrs = (char**)mxCalloc(count, sizeof(char*));
        for (size_t i = 0; i < count; ++i) {
            ptrs[i] = charsToUtf8String(mxGetCell(arg, i));
        }
    }
    ~MatlabStringArray() {
        if (ptrs) {
            for (size_t i = 0; i < count; ++i) if (ptrs[i]) mxFree(ptrs[i]);
            mxFree(ptrs);
        }
    }
    operator char**() const { return ptrs; }
    operator const char**() const { return (const char**)ptrs; }
    MatlabStringArray(const MatlabStringArray&) = delete;
};

// The MEX file is loaded by MATLAB, so static initialization runs on the one thread that
// may enter the Matrix API. Any other thread has to skip rather than corrupt it.
static const std::thread::id matlabThread = std::this_thread::get_id();

inline void checkFunctionHandle(const mxArray* arg) {
    if (!mxIsClass(arg, "function_handle")) {
        mexErrMsgIdAndTxt("bindings_tests:TypeError", "Expected a function handle.");
    }
}

// A library calls a plain function pointer and passes back only what its own signature
// declares, so the gateway hands out one distinct function per record and never reads
// anything that crossed the ABI. A record lives for the length of the one call that
// registered it.
struct CallbackRecord {
    const mxArray* handle;    // the function handle to invoke
    mxArray* error;           // what a failed invocation trapped, raised after the call
    std::atomic<int> dropped; // invocations that never reached MATLAB
    bool inUse;
};

// Bounds how many callbacks of one type may be live at once, which is the nesting depth
// of callbacks re-entering the library. A slot costs one record.
static const size_t MAX_CALLBACKS = 16;

struct CallbackRegistry {
    CallbackRecord records[MAX_CALLBACKS];
};

static CallbackRegistry doubleCallbackFuncRegistry;
static CallbackRegistry doubleSpanToScalarCallbackFuncRegistry;
static CallbackRegistry plainIndexCallbackFunctionRegistry;
static CallbackRegistry plainNotifyCallbackFunctionRegistry;

// The index selects which of the type's trampolines the library is handed, so a nested
// or repeated registration only needs a slot of its own rather than a lookup key.
inline size_t acquireCallbackSlot(CallbackRegistry& registry, const mxArray* arg) {
    checkFunctionHandle(arg);
    for (size_t i = 0; i < MAX_CALLBACKS; i++) {
        CallbackRecord& record = registry.records[i];
        if (record.inUse) {
            continue;
        }
        // std::atomic is not copyable, so the record is filled field by field
        record.handle = arg;
        record.error = nullptr;
        record.dropped.store(0, std::memory_order_relaxed);
        record.inUse = true;
        return i;
    }
    mexErrMsgIdAndTxt("bindings_tests:TooManyCallbacks",
                      "More than %d callbacks of this type are live at once.", (int) MAX_CALLBACKS);
    return 0; // not reached, mexErrMsgIdAndTxt longjmps to MATLAB's prompt
}

// What a record produced, copied out before its slot is released. The trapped
// error is heap allocated by mexCallMATLABWithTrap and MATLAB owns it until the end of
// the mexFunction, so it stays valid once the record is gone.
struct CallbackOutcome {
    mxArray* error; // what a failed invocation trapped
    int dropped;    // invocations that never reached MATLAB
};

// RAII registration of the function handle one call passes. The record borrows the
// caller's prhs, which stays alive for exactly as long as the call does.
// mexErrMsgIdAndTxt longjmps to MATLAB's prompt without running C++ destructors, so no
// raise may happen while the slot is registered. The caller copies the outcome out and
// raises after the scope has closed.
struct MatlabCallback {
    CallbackRegistry& registry;
    const size_t index;
    MatlabCallback(CallbackRegistry& registry, const mxArray* arg)
            : registry(registry), index(acquireCallbackSlot(registry, arg)) {}
    ~MatlabCallback() { registry.records[index].inUse = false; }
    MatlabCallback(const MatlabCallback&) = delete;
    // The count is read after the library call has joined its threads, so relaxed is enough
    CallbackOutcome outcome() const {
        const CallbackRecord& record = registry.records[index];
        return {record.error, record.dropped.load(std::memory_order_relaxed)};
    }
};

// Raises what a trampoline trapped, once the library has unwound its own frames and the
// record has been released. Throwing the MException keeps the original identifier and
// message, and it longjmps like any other error. rethrow is the wrong verb here, it
// rejects an exception that was never caught in MATLAB code. A trapped error wins over
// the skip count, it is the more specific failure.
inline void checkCallbackError(const CallbackOutcome& outcome) {
    if (outcome.error != nullptr) {
        mxArray* error = outcome.error;
        mexCallMATLAB(0, nullptr, 1, &error, "throw");
    }
    if (outcome.dropped != 0) {
        mexErrMsgIdAndTxt("bindings_tests:NotSupported",
                          "%d callback invocation(s) fired on a thread that cannot reach MATLAB and were skipped.", outcome.dropped);
    }
}

// A result of the wrong shape cannot be raised from inside the library's frames any more
// than a failing feval can, so the check produces the same trapped MException by letting
// MATLAB's own error() format it. Returns nullptr when the result is usable.
inline mxArray* checkCallbackResult(const mxArray* result, const char* callback) {
    if (isNumericScalar(result)) {
        return nullptr;
    }
    mxArray* args[3] = {mxCreateString("bindings_tests:TypeError"),
                        mxCreateString("Callback '%s' must return a numeric scalar."),
                        mxCreateString(callback)};
    mxArray* error = mexCallMATLABWithTrap(0, nullptr, 3, args, "error");
    for (mxArray* arg : args) mxDestroyArray(arg);
    return error;
}

inline mxArray* createEmpty() {
    // Returns a 0x0 double array, which is the standard MATLAB 'null'
    return mxCreateDoubleMatrix(0, 0, mxREAL);
}

// Reads one field of a MATLAB struct argument. mxGetField returns null for a field
// that does not exist, which the scalar check cannot tell apart from a bad value.
template <typename T>
inline T getStructField(const mxArray* arg, const char* structName, const char* fieldName) {
    const mxArray* field = mxGetField(arg, 0, fieldName);
    if (!field) {
        mexErrMsgIdAndTxt("bindings_tests:InvalidInput", "Struct '%s' is missing field '%s'.", structName, fieldName);
    }
    return getScalar<T>(field, fieldName);
}

// Layout-compatible with the by-value ByteSpan struct in the C ABI
struct ByteSpan {
    const uint8_t* data;
    size_t length;
};

// Returned spans are library-owned and only valid until the next call,
// so they get copied immediately
inline mxArray* createMexByteArray(ByteSpan span) {
    if (!span.data || span.length == 0) {
        return createEmpty();
    }
    mxArray* array = mxCreateNumericMatrix(1, span.length, mxUINT8_CLASS, mxREAL);
    std::memcpy(mxGetData(array), span.data, span.length * sizeof(uint8_t));
    return array;
}

// Layout-compatible with the by-value DoubleSpan struct in the C ABI
struct DoubleSpan {
    const double* data;
    size_t length;
};

// Returned spans are library-owned and only valid until the next call,
// so they get copied immediately
inline mxArray* createMexDoubleArray(DoubleSpan span) {
    if (!span.data || span.length == 0) {
        return createEmpty();
    }
    mxArray* array = mxCreateDoubleMatrix(1, span.length, mxREAL);
    std::memcpy(mxGetPr(array), span.data, span.length * sizeof(double));
    return array;
}

// Layout-compatible with the by-value FieldTypes struct in the C ABI
struct FieldTypes {
    uint16_t unit;
    const int16_t* shorts;
    int64_t* longs;
    void* context;
};

inline FieldTypes getFieldTypes(const mxArray* arg) {
    // mxIsStruct is not part of the clean-room SDK subset, and is documented as this check
    if (mxGetClassID(arg) != mxSTRUCT_CLASS) {
        mexErrMsgIdAndTxt("bindings_tests:TypeError", "Argument must be a 'FieldTypes' struct.");
    }
    return FieldTypes{
        getStructField<uint16_t>(arg, "FieldTypes", "unit"),
        nullptr,
        nullptr,
        nullptr
    };
}

// Layout-compatible with the by-value FloatSpan struct in the C ABI
struct FloatSpan {
    const float* data;
    size_t length;
};

// Returned spans are library-owned and only valid until the next call,
// so they get copied immediately
inline mxArray* createMexFloatArray(FloatSpan span) {
    if (!span.data || span.length == 0) {
        return createEmpty();
    }
    mxArray* array = mxCreateNumericMatrix(1, span.length, mxSINGLE_CLASS, mxREAL);
    std::memcpy(mxGetData(array), span.data, span.length * sizeof(float));
    return array;
}

// Layout-compatible with the by-value IntSpan struct in the C ABI
struct IntSpan {
    const int32_t* data;
    size_t length;
};

// Returned spans are library-owned and only valid until the next call,
// so they get copied immediately
inline mxArray* createMexIntArray(IntSpan span) {
    if (!span.data || span.length == 0) {
        return createEmpty();
    }
    mxArray* array = mxCreateNumericMatrix(1, span.length, mxINT32_CLASS, mxREAL);
    std::memcpy(mxGetData(array), span.data, span.length * sizeof(int32_t));
    return array;
}

// Layout-compatible with the by-value StatusInfo struct in the C ABI
struct StatusInfo {
    double voltage;
    int32_t state;
    int32_t enabled;
};

inline StatusInfo getStatusInfo(const mxArray* arg) {
    // mxIsStruct is not part of the clean-room SDK subset, and is documented as this check
    if (mxGetClassID(arg) != mxSTRUCT_CLASS) {
        mexErrMsgIdAndTxt("bindings_tests:TypeError", "Argument must be a 'StatusInfo' struct.");
    }
    return StatusInfo{
        getStructField<double>(arg, "StatusInfo", "voltage"),
        getStructField<int32_t>(arg, "StatusInfo", "state"),
        getStructField<int32_t>(arg, "StatusInfo", "enabled")
    };
}

typedef double (*bindings_tests_DoubleCallbackFunc)(double a, double b);
typedef double (*bindings_tests_DoubleSpanToScalarCallbackFunc)(const DoubleSpan* a, const DoubleSpan* b);
typedef void (*bindings_tests_IndexCallbackFunction)(void* userData, int index);
typedef void (*bindings_tests_PlainIndexCallbackFunction)(int index);
typedef void (*bindings_tests_PlainNotifyCallbackFunction)();
typedef void (*bindings_tests_TransformCallbackFunction)(void* userData, const double* input, double* output);

// MATLAB char data is UTF-16, and the Matrix API has no UTF-8 aware way to build it.
// mxCreateString decodes in MATLAB's default character set, which its own header describes
// as "either UTF-8 encoding or the local codepage encoding". The UTF-8 default was added in
// R2020a, but we want to support back down to R2018b. Counting the utf8 code units keeps
// this allocation free.
inline mxArray* createMexString(const char* c_str) {
    if (!c_str) {
        return createEmpty();
    }

    // Continuation bytes never start a character, a four byte sequence needs a surrogate pair
    const auto* utf8 = reinterpret_cast<const unsigned char*>(c_str);
    mwSize units = 0;
    for (const unsigned char* b = utf8; *b; ++b) {
        if ((*b & 0xC0) != 0x80) units += (*b >= 0xF0) ? 2 : 1;
    }

    const mwSize dims[2] = {1, units};
    mxArray* array = mxCreateCharArray(2, dims);
    auto* out = static_cast<mxChar*>(mxGetData(array));
    const mxChar* const end = out + units; // malformed input must not go beyond the count

    for (const unsigned char* b = utf8; *b && out < end; ) {
        uint32_t cp = *b++;
        if (cp >= 0x80) {
            // Mask the length bits off the lead byte, then fold in what follows
            cp &= (cp >= 0xF0) ? 0x07 : (cp >= 0xE0) ? 0x0F : 0x1F;
            while ((*b & 0xC0) == 0x80) cp = (cp << 6) | (*b++ & 0x3F);
        }

        if (cp <= 0xFFFF) {
            *out++ = static_cast<mxChar>(cp);
        } else {
            cp -= 0x10000; // outside the BMP, so it takes a surrogate pair
            *out++ = static_cast<mxChar>(0xD800 | (cp >> 10));
            if (out < end) *out++ = static_cast<mxChar>(0xDC00 | (cp & 0x3FF));
        }
    }
    return array;
}

template <typename T>
inline T getPtr(const mxArray* array) {
    if (mxIsEmpty(array)) {
        return nullptr; // C-Style nullptr
    }
    if (!mxIsUint64(array)) {
        mexErrMsgIdAndTxt("bindings_tests:TypeError", "Pointer must be a uint64 scalar.");
    }
    return reinterpret_cast<T>(((uint64_t*)mxGetData(array))[0]);
}

inline mxArray* createPtr(uint64_t value) {
    auto array = mxCreateNumericMatrix(1, 1, mxUINT64_CLASS, mxREAL);
    ((uint64_t*)mxGetData(array))[0] = value;
    return array;
}

inline mxArray* createMexPointer(const void* c_ptr) {
    // Cast pointer to uintptr_t to safely convert to double without precision loss
    return c_ptr ? createPtr((uint64_t)(uintptr_t)c_ptr) : createEmpty();
}

template <typename T>
inline mxArray* createMexScalar(T value) {
    // MATLAB saves all numbers as double by default. Going with something else can
    // create issues. This gives us 2^53 bits for integers before loss of precision,
    // which is fine for everything up to 32 bit. Wider types get their own class below.
    return mxCreateDoubleScalar(static_cast<double>(value));
}

// 2^53, the largest integer a double holds exactly
static const int64_t MAX_EXACT_DOUBLE_INT = 9007199254740992LL;

// 64 bit integers stay double while they fit the mantissa
inline mxArray* createMexInt64(int64_t value) {
    if (value <= MAX_EXACT_DOUBLE_INT && value >= -MAX_EXACT_DOUBLE_INT) {
        return mxCreateDoubleScalar(static_cast<double>(value));
    }
    mxArray* array = mxCreateNumericMatrix(1, 1, mxINT64_CLASS, mxREAL);
    ((int64_t*)mxGetData(array))[0] = value;
    return array;
}

inline mxArray* createMexSize(size_t value) {
    if ((uint64_t) value <= (uint64_t) MAX_EXACT_DOUBLE_INT) {
        return mxCreateDoubleScalar(static_cast<double>(value));
    }
    mxArray* array = mxCreateNumericMatrix(1, 1, mxUINT64_CLASS, mxREAL);
    ((uint64_t*)mxGetData(array))[0] = (uint64_t) value;
    return array;
}

// The 1x1 MATLAB struct a returned FieldTypes maps to
inline mxArray* createMexFieldTypes(const FieldTypes& value) {
    const char* fields[] = {"unit"};
    mxArray* result = mxCreateStructMatrix(1, 1, 1, fields);
    mxSetField(result, 0, "unit", createMexScalar(value.unit));
    return result;
}


// The 1x1 MATLAB struct a returned StatusInfo maps to
inline mxArray* createMexStatusInfo(const StatusInfo& value) {
    const char* fields[] = {"voltage", "state", "enabled"};
    mxArray* result = mxCreateStructMatrix(1, 1, 3, fields);
    mxSetField(result, 0, "voltage", createMexScalar(value.voltage));
    mxSetField(result, 0, "state", createMexScalar(value.state));
    mxSetField(result, 0, "enabled", mxCreateLogicalScalar(value.enabled != 0));
    return result;
}

// Invokes the function handle registered in slot N for DoubleCallbackFunc. Which of these
// the library was handed is the whole channel, so nothing arriving back from it is
// dereferenced. An error is trapped rather than raised, because a longjmp from here
// would tear down the library's own frames. One mxArray per argument per call is
// inherent to MATLAB.
template <size_t N>
static double invokeDoubleCallbackFunc(double a, double b) {
    CallbackRecord& record = doubleCallbackFuncRegistry.records[N];
    // The thread comes first, because everything below reaches into MATLAB. The
    // count is read after the library call has joined its threads, so relaxed is
    // enough, and the caller sees it as an error or a warning afterwards.
    if (std::this_thread::get_id() != matlabThread) {
        record.dropped.fetch_add(1, std::memory_order_relaxed);
        return double{};
    }
    // A released slot means the library kept the pointer past the call that passed
    // it, which is not supported, and a slot whose earlier invocation failed has that
    // error waiting to be raised. Neither reaches MATLAB.
    if (!record.inUse || record.error != nullptr) {
        return double{};
    }
    mxArray* args[3] = {const_cast<mxArray*>(record.handle), createMexScalar(a), createMexScalar(b)};
    double value{};
    mxArray* result = nullptr;
    mxArray* error = mexCallMATLABWithTrap(1, &result, 3, args, "feval");
    if (error == nullptr) {
        error = checkCallbackResult(result, "DoubleCallbackFunc");
        if (error == nullptr) {
            value = static_cast<double>(mxGetScalar(result));
        }
        mxDestroyArray(result);
    }
    record.error = error;
    mxDestroyArray(args[1]);
    mxDestroyArray(args[2]);
    return value;
}

// Each entry is a separate instantiation, so the index cannot be a runtime value and
// the table has to be expanded from a pack rather than filled in a loop.
template <size_t... Is>
static std::array<bindings_tests_DoubleCallbackFunc, sizeof...(Is)> makeDoubleCallbackFuncTable(std::index_sequence<Is...>) {
    return {{&invokeDoubleCallbackFunc<Is>...}};
}
static const auto doubleCallbackFuncTable = makeDoubleCallbackFuncTable(std::make_index_sequence<MAX_CALLBACKS>{});

// Invokes the function handle registered in slot N for DoubleSpanToScalarCallbackFunc. Which of these
// the library was handed is the whole channel, so nothing arriving back from it is
// dereferenced. An error is trapped rather than raised, because a longjmp from here
// would tear down the library's own frames. One mxArray per argument per call is
// inherent to MATLAB.
template <size_t N>
static double invokeDoubleSpanToScalarCallbackFunc(const DoubleSpan* a, const DoubleSpan* b) {
    CallbackRecord& record = doubleSpanToScalarCallbackFuncRegistry.records[N];
    // The thread comes first, because everything below reaches into MATLAB. The
    // count is read after the library call has joined its threads, so relaxed is
    // enough, and the caller sees it as an error or a warning afterwards.
    if (std::this_thread::get_id() != matlabThread) {
        record.dropped.fetch_add(1, std::memory_order_relaxed);
        return double{};
    }
    // A released slot means the library kept the pointer past the call that passed
    // it, which is not supported, and a slot whose earlier invocation failed has that
    // error waiting to be raised. Neither reaches MATLAB.
    if (!record.inUse || record.error != nullptr) {
        return double{};
    }
    mxArray* args[3] = {const_cast<mxArray*>(record.handle), createMexDoubleArray(*a), createMexDoubleArray(*b)};
    double value{};
    mxArray* result = nullptr;
    mxArray* error = mexCallMATLABWithTrap(1, &result, 3, args, "feval");
    if (error == nullptr) {
        error = checkCallbackResult(result, "DoubleSpanToScalarCallbackFunc");
        if (error == nullptr) {
            value = static_cast<double>(mxGetScalar(result));
        }
        mxDestroyArray(result);
    }
    record.error = error;
    mxDestroyArray(args[1]);
    mxDestroyArray(args[2]);
    return value;
}

// Each entry is a separate instantiation, so the index cannot be a runtime value and
// the table has to be expanded from a pack rather than filled in a loop.
template <size_t... Is>
static std::array<bindings_tests_DoubleSpanToScalarCallbackFunc, sizeof...(Is)> makeDoubleSpanToScalarCallbackFuncTable(std::index_sequence<Is...>) {
    return {{&invokeDoubleSpanToScalarCallbackFunc<Is>...}};
}
static const auto doubleSpanToScalarCallbackFuncTable = makeDoubleSpanToScalarCallbackFuncTable(std::make_index_sequence<MAX_CALLBACKS>{});

// Invokes the function handle registered in slot N for PlainIndexCallbackFunction. Which of these
// the library was handed is the whole channel, so nothing arriving back from it is
// dereferenced. An error is trapped rather than raised, because a longjmp from here
// would tear down the library's own frames. One mxArray per argument per call is
// inherent to MATLAB.
template <size_t N>
static void invokePlainIndexCallbackFunction(int index) {
    CallbackRecord& record = plainIndexCallbackFunctionRegistry.records[N];
    // The thread comes first, because everything below reaches into MATLAB. The
    // count is read after the library call has joined its threads, so relaxed is
    // enough, and the caller sees it as an error or a warning afterwards.
    if (std::this_thread::get_id() != matlabThread) {
        record.dropped.fetch_add(1, std::memory_order_relaxed);
        return;
    }
    // A released slot means the library kept the pointer past the call that passed
    // it, which is not supported, and a slot whose earlier invocation failed has that
    // error waiting to be raised. Neither reaches MATLAB.
    if (!record.inUse || record.error != nullptr) {
        return;
    }
    mxArray* args[2] = {const_cast<mxArray*>(record.handle), createMexScalar(index)};
    mxArray* error = mexCallMATLABWithTrap(0, nullptr, 2, args, "feval");
    record.error = error;
    mxDestroyArray(args[1]);
}

// Each entry is a separate instantiation, so the index cannot be a runtime value and
// the table has to be expanded from a pack rather than filled in a loop.
template <size_t... Is>
static std::array<bindings_tests_PlainIndexCallbackFunction, sizeof...(Is)> makePlainIndexCallbackFunctionTable(std::index_sequence<Is...>) {
    return {{&invokePlainIndexCallbackFunction<Is>...}};
}
static const auto plainIndexCallbackFunctionTable = makePlainIndexCallbackFunctionTable(std::make_index_sequence<MAX_CALLBACKS>{});

// Invokes the function handle registered in slot N for PlainNotifyCallbackFunction. Which of these
// the library was handed is the whole channel, so nothing arriving back from it is
// dereferenced. An error is trapped rather than raised, because a longjmp from here
// would tear down the library's own frames. One mxArray per argument per call is
// inherent to MATLAB.
template <size_t N>
static void invokePlainNotifyCallbackFunction() {
    CallbackRecord& record = plainNotifyCallbackFunctionRegistry.records[N];
    // The thread comes first, because everything below reaches into MATLAB. The
    // count is read after the library call has joined its threads, so relaxed is
    // enough, and the caller sees it as an error or a warning afterwards.
    if (std::this_thread::get_id() != matlabThread) {
        record.dropped.fetch_add(1, std::memory_order_relaxed);
        return;
    }
    // A released slot means the library kept the pointer past the call that passed
    // it, which is not supported, and a slot whose earlier invocation failed has that
    // error waiting to be raised. Neither reaches MATLAB.
    if (!record.inUse || record.error != nullptr) {
        return;
    }
    mxArray* args[1] = {const_cast<mxArray*>(record.handle)};
    mxArray* error = mexCallMATLABWithTrap(0, nullptr, 1, args, "feval");
    record.error = error;
}

// Each entry is a separate instantiation, so the index cannot be a runtime value and
// the table has to be expanded from a pack rather than filled in a loop.
template <size_t... Is>
static std::array<bindings_tests_PlainNotifyCallbackFunction, sizeof...(Is)> makePlainNotifyCallbackFunctionTable(std::index_sequence<Is...>) {
    return {{&invokePlainNotifyCallbackFunction<Is>...}};
}
static const auto plainNotifyCallbackFunctionTable = makePlainNotifyCallbackFunctionTable(std::make_index_sequence<MAX_CALLBACKS>{});

// Determine the path to the library by calling into MATLAB (may auto-download)
std::string getLibraryPath() {
    mxArray* infoStruct = nullptr;
    if (mexCallMATLAB(1, &infoStruct, 0, nullptr, "locate_binary") != 0) {
        mexErrMsgIdAndTxt("bindings_tests:PathError", "Failed to call locate_binary");
    }

    mxArray* pathArray = mxGetField(infoStruct, 0, "binPath");
    if (!pathArray) {
        mxDestroyArray(infoStruct);
        mexErrMsgIdAndTxt("bindings_tests:PathError", "locate_binary did not return 'binPath'");
    }

    char* pathCStr = charsToUtf8String(pathArray);
    std::string path(pathCStr);

    mxFree(pathCStr);
    mxDestroyArray(infoStruct);
    return path;
}

class LibLoader {
public:
    explicit LibLoader(const std::string& path) {
#ifdef _WIN32
        // Support UTF-8 paths on Windows
        int wsize = MultiByteToWideChar(CP_UTF8, 0, path.c_str(), (int)path.size(), NULL, 0);
        std::wstring wpath(wsize, 0);
        MultiByteToWideChar(CP_UTF8, 0, path.c_str(), (int)path.size(), &wpath[0], wsize);
        handle_ = LoadLibraryW(wpath.c_str());
#else
        handle_ = dlopen(path.c_str(), RTLD_LAZY | RTLD_GLOBAL);
#endif
        if (!handle_) mexErrMsgIdAndTxt("bindings_tests:LoadError", "Could not load library: %s", path.c_str());
    }

    template<typename T>
    T getFunc(const char* name) const {
#ifdef _WIN32
        FARPROC symbol = GetProcAddress((HMODULE)handle_, name);
#else
        void* symbol = dlsym(handle_, name);
#endif
        if(!symbol) {
            mexErrMsgIdAndTxt("bindings_tests:LoadError", "Could not load symbol: %s", name);
        }
        return reinterpret_cast<T>(symbol);
    }

    ~LibLoader() {
        if (handle_) {
#ifdef _WIN32
            FreeLibrary((HMODULE)handle_);
#else
            dlclose(handle_);
#endif
        }
    }

private:
    void* handle_ = nullptr;
};

#ifdef __cplusplus
extern "C" {
#endif

MEX_EXPORT void mexFunction(int nlhs, mxArray *plhs[], int nrhs, const mxArray *prhs[]) {
     uint32_t funcHash;
     if (nrhs >= 1 && mxIsChar(prhs[0])) {
         // Zero-allocation hash MATLAB's 16 bit chars.
         const mxChar *funcName = (const mxChar *) mxGetData(prhs[0]);
         size_t nameLen = mxGetNumberOfElements(prhs[0]);
         funcHash = hash32_fnv1a(funcName, nameLen);
     } else if (nrhs >= 1 && mxGetClassID(prhs[0]) == mxUINT32_CLASS && mxGetNumberOfElements(prhs[0]) == 1) {
         // Raw function hash
         funcHash = ((const uint32_t *) mxGetData(prhs[0]))[0];
     } else {
         mexErrMsgIdAndTxt("bindings_tests:InvalidInput", "First argument must be a function identifier.");
     }

    // Bind to the binary once per MATLAB session/clear
    static std::string binPath = getLibraryPath();
    static LibLoader libLoader(binPath);

    // Switch on a Perfect Hash (seeds are chosen until there are no collisions)
    switch (funcHash) {

        // Counter
        case /* hash32_fnv1a("bindings_tests_Counter_create") */ 3878812671: {
            static auto bindings_tests_Counter_create = libLoader.getFunc<void*(*)(ErrorInfo*, int)>("bindings_tests_Counter_create");
            checkArgCount(nrhs, 1, "bindings_tests_Counter_create");
            ErrorInfo _error = {};
            int start = getScalar<int>(prhs[1], "start");
            void* _value = bindings_tests_Counter_create(&_error, start);
            checkError(_error);
            if (nlhs > 0) plhs[0] = createMexPointer(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Counter_getValue") */ 1593629966: {
            static auto bindings_tests_Counter_getValue = libLoader.getFunc<int(*)(void*)>("bindings_tests_Counter_getValue");
            checkArgCount(nrhs, 1, "bindings_tests_Counter_getValue");
            int _value = bindings_tests_Counter_getValue(getPtr<void*>(prhs[1]));
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Counter_setValue") */ 646568658: {
            static auto bindings_tests_Counter_setValue = libLoader.getFunc<void(*)(void*, int)>("bindings_tests_Counter_setValue");
            checkArgCount(nrhs, 2, "bindings_tests_Counter_setValue");
            int value = getScalar<int>(prhs[2], "value");
            bindings_tests_Counter_setValue(getPtr<void*>(prhs[1]), value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Counter_increment") */ 793169964: {
            static auto bindings_tests_Counter_increment = libLoader.getFunc<void(*)(void*)>("bindings_tests_Counter_increment");
            checkArgCount(nrhs, 1, "bindings_tests_Counter_increment");
            bindings_tests_Counter_increment(getPtr<void*>(prhs[1]));
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Counter_add") */ 3215488130: {
            static auto bindings_tests_Counter_add = libLoader.getFunc<void(*)(void*, int)>("bindings_tests_Counter_add");
            checkArgCount(nrhs, 2, "bindings_tests_Counter_add");
            int amount = getScalar<int>(prhs[2], "amount");
            bindings_tests_Counter_add(getPtr<void*>(prhs[1]), amount);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Counter_isClosed") */ 400893135: {
            static auto bindings_tests_Counter_isClosed = libLoader.getFunc<int(*)(void*)>("bindings_tests_Counter_isClosed");
            checkArgCount(nrhs, 1, "bindings_tests_Counter_isClosed");
            int _value = bindings_tests_Counter_isClosed(getPtr<void*>(prhs[1]));
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Counter_getClosedCount") */ 4282038152: {
            static auto bindings_tests_Counter_getClosedCount = libLoader.getFunc<int(*)()>("bindings_tests_Counter_getClosedCount");
            checkArgCount(nrhs, 0, "bindings_tests_Counter_getClosedCount");
            int _value = bindings_tests_Counter_getClosedCount();
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Counter_resetClosedCount") */ 1164449961: {
            static auto bindings_tests_Counter_resetClosedCount = libLoader.getFunc<void(*)()>("bindings_tests_Counter_resetClosedCount");
            checkArgCount(nrhs, 0, "bindings_tests_Counter_resetClosedCount");
            bindings_tests_Counter_resetClosedCount();
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Counter_release") */ 1194776268: {
            static auto bindings_tests_Counter_release = libLoader.getFunc<void(*)(void*)>("bindings_tests_Counter_release");
            checkArgCount(nrhs, 1, "bindings_tests_Counter_release");
            bindings_tests_Counter_release(getPtr<void*>(prhs[1]));
            break;
        }

        // Naming
        case /* hash32_fnv1a("custom_prefix_Naming_create") */ 2391897161: {
            static auto custom_prefix_Naming_create = libLoader.getFunc<void*(*)(ErrorInfo*, int)>("custom_prefix_Naming_create");
            checkArgCount(nrhs, 1, "custom_prefix_Naming_create");
            ErrorInfo _error = {};
            int startValue = getScalar<int>(prhs[1], "startValue");
            void* _value = custom_prefix_Naming_create(&_error, startValue);
            checkError(_error);
            if (nlhs > 0) plhs[0] = createMexPointer(_value);
            break;
        }
        case /* hash32_fnv1a("custom_prefix_Naming_get_start_value") */ 4190131410: {
            static auto custom_prefix_Naming_get_start_value = libLoader.getFunc<int(*)(void*)>("custom_prefix_Naming_get_start_value");
            checkArgCount(nrhs, 1, "custom_prefix_Naming_get_start_value");
            int _value = custom_prefix_Naming_get_start_value(getPtr<void*>(prhs[1]));
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }
        case /* hash32_fnv1a("custom_prefix_Naming_add_two_numbers") */ 1497470220: {
            static auto custom_prefix_Naming_add_two_numbers = libLoader.getFunc<int(*)(int, int)>("custom_prefix_Naming_add_two_numbers");
            checkArgCount(nrhs, 2, "custom_prefix_Naming_add_two_numbers");
            int first = getScalar<int>(prhs[1], "first");
            int second = getScalar<int>(prhs[2], "second");
            int _value = custom_prefix_Naming_add_two_numbers(first, second);
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }
        case /* hash32_fnv1a("custom_prefix_Naming_release") */ 2972985146: {
            static auto custom_prefix_Naming_release = libLoader.getFunc<void(*)(void*)>("custom_prefix_Naming_release");
            checkArgCount(nrhs, 1, "custom_prefix_Naming_release");
            custom_prefix_Naming_release(getPtr<void*>(prhs[1]));
            break;
        }

        // Shape
        case /* hash32_fnv1a("bindings_tests_Shape_getName") */ 109245415: {
            static auto bindings_tests_Shape_getName = libLoader.getFunc<const char*(*)(void*)>("bindings_tests_Shape_getName");
            checkArgCount(nrhs, 1, "bindings_tests_Shape_getName");
            const char* _value = bindings_tests_Shape_getName(getPtr<void*>(prhs[1]));
            if (nlhs > 0) plhs[0] = createMexString(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Shape_setName") */ 1722972291: {
            static auto bindings_tests_Shape_setName = libLoader.getFunc<void(*)(void*, const char*)>("bindings_tests_Shape_setName");
            checkArgCount(nrhs, 2, "bindings_tests_Shape_setName");
            MatlabString name(prhs[2]);
            bindings_tests_Shape_setName(getPtr<void*>(prhs[1]), name);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Shape_area") */ 557141481: {
            static auto bindings_tests_Shape_area = libLoader.getFunc<double(*)(void*)>("bindings_tests_Shape_area");
            checkArgCount(nrhs, 1, "bindings_tests_Shape_area");
            double _value = bindings_tests_Shape_area(getPtr<void*>(prhs[1]));
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Shape_sideCount") */ 89654474: {
            static auto bindings_tests_Shape_sideCount = libLoader.getFunc<int(*)(void*)>("bindings_tests_Shape_sideCount");
            checkArgCount(nrhs, 1, "bindings_tests_Shape_sideCount");
            int _value = bindings_tests_Shape_sideCount(getPtr<void*>(prhs[1]));
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Shape_release") */ 687567245: {
            static auto bindings_tests_Shape_release = libLoader.getFunc<void(*)(void*)>("bindings_tests_Shape_release");
            checkArgCount(nrhs, 1, "bindings_tests_Shape_release");
            bindings_tests_Shape_release(getPtr<void*>(prhs[1]));
            break;
        }

        // Circle
        case /* hash32_fnv1a("bindings_tests_Circle_create") */ 1129006167: {
            static auto bindings_tests_Circle_create = libLoader.getFunc<void*(*)(ErrorInfo*, double)>("bindings_tests_Circle_create");
            checkArgCount(nrhs, 1, "bindings_tests_Circle_create");
            ErrorInfo _error = {};
            double radius = getScalar<double>(prhs[1], "radius");
            void* _value = bindings_tests_Circle_create(&_error, radius);
            checkError(_error);
            if (nlhs > 0) plhs[0] = createMexPointer(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Circle_getRadius") */ 1721594359: {
            static auto bindings_tests_Circle_getRadius = libLoader.getFunc<double(*)(void*)>("bindings_tests_Circle_getRadius");
            checkArgCount(nrhs, 1, "bindings_tests_Circle_getRadius");
            double _value = bindings_tests_Circle_getRadius(getPtr<void*>(prhs[1]));
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Circle_getColor") */ 1712892204: {
            static auto bindings_tests_Circle_getColor = libLoader.getFunc<int(*)(void*)>("bindings_tests_Circle_getColor");
            checkArgCount(nrhs, 1, "bindings_tests_Circle_getColor");
            int _value = bindings_tests_Circle_getColor(getPtr<void*>(prhs[1]));
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Circle_area") */ 1913477986: {
            static auto bindings_tests_Circle_area = libLoader.getFunc<double(*)(void*)>("bindings_tests_Circle_area");
            checkArgCount(nrhs, 1, "bindings_tests_Circle_area");
            double _value = bindings_tests_Circle_area(getPtr<void*>(prhs[1]));
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Circle_sideCount") */ 1258038027: {
            static auto bindings_tests_Circle_sideCount = libLoader.getFunc<int(*)(void*)>("bindings_tests_Circle_sideCount");
            checkArgCount(nrhs, 1, "bindings_tests_Circle_sideCount");
            int _value = bindings_tests_Circle_sideCount(getPtr<void*>(prhs[1]));
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Circle_to_Shape") */ 615662008: {
            static auto bindings_tests_Circle_to_Shape = libLoader.getFunc<void*(*)(void*)>("bindings_tests_Circle_to_Shape");
            checkArgCount(nrhs, 1, "bindings_tests_Circle_to_Shape");
            if (nlhs > 0) plhs[0] = createMexPointer(bindings_tests_Circle_to_Shape(getPtr<void*>(prhs[1])));
            break;
        }

        // Square
        case /* hash32_fnv1a("bindings_tests_Square_create") */ 3588199438: {
            static auto bindings_tests_Square_create = libLoader.getFunc<void*(*)(ErrorInfo*, double)>("bindings_tests_Square_create");
            checkArgCount(nrhs, 1, "bindings_tests_Square_create");
            ErrorInfo _error = {};
            double side = getScalar<double>(prhs[1], "side");
            void* _value = bindings_tests_Square_create(&_error, side);
            checkError(_error);
            if (nlhs > 0) plhs[0] = createMexPointer(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Square_getSide") */ 656689845: {
            static auto bindings_tests_Square_getSide = libLoader.getFunc<double(*)(void*)>("bindings_tests_Square_getSide");
            checkArgCount(nrhs, 1, "bindings_tests_Square_getSide");
            double _value = bindings_tests_Square_getSide(getPtr<void*>(prhs[1]));
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Square_area") */ 1574289823: {
            static auto bindings_tests_Square_area = libLoader.getFunc<double(*)(void*)>("bindings_tests_Square_area");
            checkArgCount(nrhs, 1, "bindings_tests_Square_area");
            double _value = bindings_tests_Square_area(getPtr<void*>(prhs[1]));
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Square_sideCount") */ 3215798272: {
            static auto bindings_tests_Square_sideCount = libLoader.getFunc<int(*)(void*)>("bindings_tests_Square_sideCount");
            checkArgCount(nrhs, 1, "bindings_tests_Square_sideCount");
            int _value = bindings_tests_Square_sideCount(getPtr<void*>(prhs[1]));
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Square_to_Shape") */ 2776091829: {
            static auto bindings_tests_Square_to_Shape = libLoader.getFunc<void*(*)(void*)>("bindings_tests_Square_to_Shape");
            checkArgCount(nrhs, 1, "bindings_tests_Square_to_Shape");
            if (nlhs > 0) plhs[0] = createMexPointer(bindings_tests_Square_to_Shape(getPtr<void*>(prhs[1])));
            break;
        }

        // Arrays
        case /* hash32_fnv1a("bindings_tests_Arrays_sumFloat64") */ 770314080: {
            static auto bindings_tests_Arrays_sumFloat64 = libLoader.getFunc<double(*)(const double*, size_t)>("bindings_tests_Arrays_sumFloat64");
            checkArgCount(nrhs, 2, "bindings_tests_Arrays_sumFloat64");
            MatlabArray<double, mxDOUBLE_CLASS> values(prhs[1]);
            size_t count = getScalar<size_t>(prhs[2], "count");
            double _value = bindings_tests_Arrays_sumFloat64(values, count);
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Arrays_sumInt32") */ 486597228: {
            static auto bindings_tests_Arrays_sumInt32 = libLoader.getFunc<int(*)(const int*, size_t)>("bindings_tests_Arrays_sumInt32");
            checkArgCount(nrhs, 2, "bindings_tests_Arrays_sumInt32");
            MatlabArray<int32_t, mxINT32_CLASS> values(prhs[1]);
            size_t count = getScalar<size_t>(prhs[2], "count");
            int _value = bindings_tests_Arrays_sumInt32(values, count);
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Arrays_fillFloat64") */ 3695237744: {
            static auto bindings_tests_Arrays_fillFloat64 = libLoader.getFunc<void(*)(double*, size_t, double)>("bindings_tests_Arrays_fillFloat64");
            checkArgCount(nrhs, 3, "bindings_tests_Arrays_fillFloat64");
            MatlabArray<double, mxDOUBLE_CLASS> target(prhs[1]);
            size_t count = getScalar<size_t>(prhs[2], "count");
            double value = getScalar<double>(prhs[3], "value");
            bindings_tests_Arrays_fillFloat64(target, count, value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Arrays_cumulativeSum") */ 106931725: {
            static auto bindings_tests_Arrays_cumulativeSum = libLoader.getFunc<void(*)(const double*, double*, size_t)>("bindings_tests_Arrays_cumulativeSum");
            checkArgCount(nrhs, 3, "bindings_tests_Arrays_cumulativeSum");
            MatlabArray<double, mxDOUBLE_CLASS> source(prhs[1]);
            MatlabArray<double, mxDOUBLE_CLASS> target(prhs[2]);
            size_t count = getScalar<size_t>(prhs[3], "count");
            bindings_tests_Arrays_cumulativeSum(source, target, count);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Arrays_reverseFloat64") */ 331409391: {
            static auto bindings_tests_Arrays_reverseFloat64 = libLoader.getFunc<void(*)(double*, size_t)>("bindings_tests_Arrays_reverseFloat64");
            checkArgCount(nrhs, 2, "bindings_tests_Arrays_reverseFloat64");
            MatlabArray<double, mxDOUBLE_CLASS> values(prhs[1]);
            size_t count = getScalar<size_t>(prhs[2], "count");
            bindings_tests_Arrays_reverseFloat64(values, count);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Arrays_dotProduct") */ 4019140823: {
            static auto bindings_tests_Arrays_dotProduct = libLoader.getFunc<double(*)(ErrorInfo*, DoubleSpan, DoubleSpan)>("bindings_tests_Arrays_dotProduct");
            checkArgCount(nrhs, 2, "bindings_tests_Arrays_dotProduct");
            ErrorInfo _error = {};
            MatlabArray<double, mxDOUBLE_CLASS> x_arr(prhs[1]);
            DoubleSpan x = {x_arr.ptr, mxGetNumberOfElements(prhs[1])};
            MatlabArray<double, mxDOUBLE_CLASS> y_arr(prhs[2]);
            DoubleSpan y = {y_arr.ptr, mxGetNumberOfElements(prhs[2])};
            double _value = bindings_tests_Arrays_dotProduct(&_error, x, y);
            checkError(_error);
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Arrays_addOffset") */ 3807979063: {
            static auto bindings_tests_Arrays_addOffset = libLoader.getFunc<DoubleSpan(*)(DoubleSpan, double)>("bindings_tests_Arrays_addOffset");
            checkArgCount(nrhs, 2, "bindings_tests_Arrays_addOffset");
            MatlabArray<double, mxDOUBLE_CLASS> values_arr(prhs[1]);
            DoubleSpan values = {values_arr.ptr, mxGetNumberOfElements(prhs[1])};
            double offset = getScalar<double>(prhs[2], "offset");
            DoubleSpan _value = bindings_tests_Arrays_addOffset(values, offset);
            if (nlhs > 0) plhs[0] = createMexDoubleArray(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Arrays_addOffsetInt32") */ 1137316889: {
            static auto bindings_tests_Arrays_addOffsetInt32 = libLoader.getFunc<IntSpan(*)(IntSpan, int)>("bindings_tests_Arrays_addOffsetInt32");
            checkArgCount(nrhs, 2, "bindings_tests_Arrays_addOffsetInt32");
            MatlabArray<int32_t, mxINT32_CLASS> values_arr(prhs[1]);
            IntSpan values = {values_arr.ptr, mxGetNumberOfElements(prhs[1])};
            int offset = getScalar<int>(prhs[2], "offset");
            IntSpan _value = bindings_tests_Arrays_addOffsetInt32(values, offset);
            if (nlhs > 0) plhs[0] = createMexIntArray(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Arrays_addOffsetFloat32") */ 2272846934: {
            static auto bindings_tests_Arrays_addOffsetFloat32 = libLoader.getFunc<FloatSpan(*)(FloatSpan, float)>("bindings_tests_Arrays_addOffsetFloat32");
            checkArgCount(nrhs, 2, "bindings_tests_Arrays_addOffsetFloat32");
            MatlabArray<float, mxSINGLE_CLASS> values_arr(prhs[1]);
            FloatSpan values = {values_arr.ptr, mxGetNumberOfElements(prhs[1])};
            float offset = getScalar<float>(prhs[2], "offset");
            FloatSpan _value = bindings_tests_Arrays_addOffsetFloat32(values, offset);
            if (nlhs > 0) plhs[0] = createMexFloatArray(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Arrays_sumUint8") */ 4236390872: {
            static auto bindings_tests_Arrays_sumUint8 = libLoader.getFunc<int(*)(uint8_t*, size_t)>("bindings_tests_Arrays_sumUint8");
            checkArgCount(nrhs, 2, "bindings_tests_Arrays_sumUint8");
            MatlabArray<uint8_t, mxUINT8_CLASS> values(prhs[1]);
            size_t count = getScalar<size_t>(prhs[2], "count");
            int _value = bindings_tests_Arrays_sumUint8(values, count);
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Arrays_addOffsetUint8") */ 1126113101: {
            static auto bindings_tests_Arrays_addOffsetUint8 = libLoader.getFunc<ByteSpan(*)(ByteSpan, char)>("bindings_tests_Arrays_addOffsetUint8");
            checkArgCount(nrhs, 2, "bindings_tests_Arrays_addOffsetUint8");
            MatlabArray<uint8_t, mxUINT8_CLASS> values_arr(prhs[1]);
            ByteSpan values = {values_arr.ptr, mxGetNumberOfElements(prhs[1])};
            char offset = getScalar<char>(prhs[2], "offset");
            ByteSpan _value = bindings_tests_Arrays_addOffsetUint8(values, offset);
            if (nlhs > 0) plhs[0] = createMexByteArray(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Arrays_getSequence") */ 3328946390: {
            static auto bindings_tests_Arrays_getSequence = libLoader.getFunc<DoubleSpan(*)(size_t)>("bindings_tests_Arrays_getSequence");
            checkArgCount(nrhs, 1, "bindings_tests_Arrays_getSequence");
            size_t count = getScalar<size_t>(prhs[1], "count");
            DoubleSpan _value = bindings_tests_Arrays_getSequence(count);
            if (nlhs > 0) plhs[0] = createMexDoubleArray(_value);
            break;
        }

        // Callbacks
        case /* hash32_fnv1a("bindings_tests_Callbacks_invokeDoubleCallback") */ 3965007693: {
            static auto bindings_tests_Callbacks_invokeDoubleCallback = libLoader.getFunc<double(*)(bindings_tests_DoubleCallbackFunc, double, double)>("bindings_tests_Callbacks_invokeDoubleCallback");
            checkArgCount(nrhs, 3, "bindings_tests_Callbacks_invokeDoubleCallback");
            double a = getScalar<double>(prhs[2], "a");
            double b = getScalar<double>(prhs[3], "b");
            double _value;
            CallbackOutcome funcOutcome;
            // mexErrMsgIdAndTxt longjmps without running destructors, so nothing inside
            // the scope may raise. The outcome is checked once the slot has been released.
            {
                MatlabCallback func(doubleCallbackFuncRegistry, prhs[1]);
                _value = bindings_tests_Callbacks_invokeDoubleCallback(doubleCallbackFuncTable[func.index], a, b);
                funcOutcome = func.outcome();
            }
            checkCallbackError(funcOutcome);
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Callbacks_invokeDoubleSpanToScalarCallback") */ 728431532: {
            static auto bindings_tests_Callbacks_invokeDoubleSpanToScalarCallback = libLoader.getFunc<double(*)(bindings_tests_DoubleSpanToScalarCallbackFunc, DoubleSpan, DoubleSpan)>("bindings_tests_Callbacks_invokeDoubleSpanToScalarCallback");
            checkArgCount(nrhs, 3, "bindings_tests_Callbacks_invokeDoubleSpanToScalarCallback");
            MatlabArray<double, mxDOUBLE_CLASS> a_arr(prhs[2]);
            DoubleSpan a = {a_arr.ptr, mxGetNumberOfElements(prhs[2])};
            MatlabArray<double, mxDOUBLE_CLASS> b_arr(prhs[3]);
            DoubleSpan b = {b_arr.ptr, mxGetNumberOfElements(prhs[3])};
            double _value;
            CallbackOutcome funcOutcome;
            // mexErrMsgIdAndTxt longjmps without running destructors, so nothing inside
            // the scope may raise. The outcome is checked once the slot has been released.
            {
                MatlabCallback func(doubleSpanToScalarCallbackFuncRegistry, prhs[1]);
                _value = bindings_tests_Callbacks_invokeDoubleSpanToScalarCallback(doubleSpanToScalarCallbackFuncTable[func.index], a, b);
                funcOutcome = func.outcome();
            }
            checkCallbackError(funcOutcome);
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Callbacks_invokePlainTimes") */ 1177968689: {
            static auto bindings_tests_Callbacks_invokePlainTimes = libLoader.getFunc<void(*)(bindings_tests_PlainIndexCallbackFunction, size_t)>("bindings_tests_Callbacks_invokePlainTimes");
            checkArgCount(nrhs, 2, "bindings_tests_Callbacks_invokePlainTimes");
            size_t times = getScalar<size_t>(prhs[2], "times");
            CallbackOutcome funcOutcome;
            // mexErrMsgIdAndTxt longjmps without running destructors, so nothing inside
            // the scope may raise. The outcome is checked once the slot has been released.
            {
                MatlabCallback func(plainIndexCallbackFunctionRegistry, prhs[1]);
                bindings_tests_Callbacks_invokePlainTimes(plainIndexCallbackFunctionTable[func.index], times);
                funcOutcome = func.outcome();
            }
            checkCallbackError(funcOutcome);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Callbacks_invokePlainChecked") */ 1568763650: {
            static auto bindings_tests_Callbacks_invokePlainChecked = libLoader.getFunc<void(*)(ErrorInfo*, bindings_tests_PlainIndexCallbackFunction, int)>("bindings_tests_Callbacks_invokePlainChecked");
            checkArgCount(nrhs, 2, "bindings_tests_Callbacks_invokePlainChecked");
            ErrorInfo _error = {};
            int index = getScalar<int>(prhs[2], "index");
            CallbackOutcome funcOutcome;
            // mexErrMsgIdAndTxt longjmps without running destructors, so nothing inside
            // the scope may raise. The outcome is checked once the slot has been released.
            {
                MatlabCallback func(plainIndexCallbackFunctionRegistry, prhs[1]);
                bindings_tests_Callbacks_invokePlainChecked(&_error, plainIndexCallbackFunctionTable[func.index], index);
                funcOutcome = func.outcome();
            }
            checkCallbackError(funcOutcome);
            checkError(_error);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Callbacks_invokePlainOnWorkerThread") */ 3138948006: {
            static auto bindings_tests_Callbacks_invokePlainOnWorkerThread = libLoader.getFunc<void(*)(bindings_tests_PlainIndexCallbackFunction, int)>("bindings_tests_Callbacks_invokePlainOnWorkerThread");
            checkArgCount(nrhs, 2, "bindings_tests_Callbacks_invokePlainOnWorkerThread");
            int index = getScalar<int>(prhs[2], "index");
            CallbackOutcome funcOutcome;
            // mexErrMsgIdAndTxt longjmps without running destructors, so nothing inside
            // the scope may raise. The outcome is checked once the slot has been released.
            {
                MatlabCallback func(plainIndexCallbackFunctionRegistry, prhs[1]);
                bindings_tests_Callbacks_invokePlainOnWorkerThread(plainIndexCallbackFunctionTable[func.index], index);
                funcOutcome = func.outcome();
            }
            checkCallbackError(funcOutcome);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Callbacks_notifyPlainOnce") */ 203427955: {
            static auto bindings_tests_Callbacks_notifyPlainOnce = libLoader.getFunc<void(*)(bindings_tests_PlainNotifyCallbackFunction)>("bindings_tests_Callbacks_notifyPlainOnce");
            checkArgCount(nrhs, 1, "bindings_tests_Callbacks_notifyPlainOnce");
            CallbackOutcome funcOutcome;
            // mexErrMsgIdAndTxt longjmps without running destructors, so nothing inside
            // the scope may raise. The outcome is checked once the slot has been released.
            {
                MatlabCallback func(plainNotifyCallbackFunctionRegistry, prhs[1]);
                bindings_tests_Callbacks_notifyPlainOnce(plainNotifyCallbackFunctionTable[func.index]);
                funcOutcome = func.outcome();
            }
            checkCallbackError(funcOutcome);
            break;
        }

        // Errors
        case /* hash32_fnv1a("bindings_tests_Errors_requireNonNegative") */ 3634828815: {
            static auto bindings_tests_Errors_requireNonNegative = libLoader.getFunc<void(*)(ErrorInfo*, int)>("bindings_tests_Errors_requireNonNegative");
            checkArgCount(nrhs, 1, "bindings_tests_Errors_requireNonNegative");
            ErrorInfo _error = {};
            int value = getScalar<int>(prhs[1], "value");
            bindings_tests_Errors_requireNonNegative(&_error, value);
            checkError(_error);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Errors_divideInt32") */ 876481109: {
            static auto bindings_tests_Errors_divideInt32 = libLoader.getFunc<int(*)(ErrorInfo*, int, int)>("bindings_tests_Errors_divideInt32");
            checkArgCount(nrhs, 2, "bindings_tests_Errors_divideInt32");
            ErrorInfo _error = {};
            int a = getScalar<int>(prhs[1], "a");
            int b = getScalar<int>(prhs[2], "b");
            int _value = bindings_tests_Errors_divideInt32(&_error, a, b);
            checkError(_error);
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Errors_failingFloat64") */ 2587869218: {
            static auto bindings_tests_Errors_failingFloat64 = libLoader.getFunc<double(*)(ErrorInfo*, int)>("bindings_tests_Errors_failingFloat64");
            checkArgCount(nrhs, 1, "bindings_tests_Errors_failingFloat64");
            ErrorInfo _error = {};
            int shouldFail = getScalar<int>(prhs[1], "shouldFail");
            double _value = bindings_tests_Errors_failingFloat64(&_error, shouldFail);
            checkError(_error);
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Errors_failingBool") */ 2739720686: {
            static auto bindings_tests_Errors_failingBool = libLoader.getFunc<int(*)(ErrorInfo*, int)>("bindings_tests_Errors_failingBool");
            checkArgCount(nrhs, 1, "bindings_tests_Errors_failingBool");
            ErrorInfo _error = {};
            int shouldFail = getScalar<int>(prhs[1], "shouldFail");
            int _value = bindings_tests_Errors_failingBool(&_error, shouldFail);
            checkError(_error);
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Errors_failingString") */ 3708443195: {
            static auto bindings_tests_Errors_failingString = libLoader.getFunc<const char*(*)(ErrorInfo*, int)>("bindings_tests_Errors_failingString");
            checkArgCount(nrhs, 1, "bindings_tests_Errors_failingString");
            ErrorInfo _error = {};
            int shouldFail = getScalar<int>(prhs[1], "shouldFail");
            const char* _value = bindings_tests_Errors_failingString(&_error, shouldFail);
            checkError(_error);
            if (nlhs > 0) plhs[0] = createMexString(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Errors_failWithoutMessage") */ 2220498109: {
            static auto bindings_tests_Errors_failWithoutMessage = libLoader.getFunc<int(*)(ErrorInfo*)>("bindings_tests_Errors_failWithoutMessage");
            checkArgCount(nrhs, 0, "bindings_tests_Errors_failWithoutMessage");
            ErrorInfo _error = {};
            int _value = bindings_tests_Errors_failWithoutMessage(&_error);
            checkError(_error);
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Errors_neverThrows") */ 2677745337: {
            static auto bindings_tests_Errors_neverThrows = libLoader.getFunc<void(*)()>("bindings_tests_Errors_neverThrows");
            checkArgCount(nrhs, 0, "bindings_tests_Errors_neverThrows");
            bindings_tests_Errors_neverThrows();
            break;
        }

        // Indices
        case /* hash32_fnv1a("bindings_tests_Indices_echo") */ 2992836537: {
            static auto bindings_tests_Indices_echo = libLoader.getFunc<int(*)(int)>("bindings_tests_Indices_echo");
            checkArgCount(nrhs, 1, "bindings_tests_Indices_echo");
            int index = getScalar<int>(prhs[1], "index");
            int _value = bindings_tests_Indices_echo(index);
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Indices_countUpTo") */ 624477207: {
            static auto bindings_tests_Indices_countUpTo = libLoader.getFunc<int(*)(int)>("bindings_tests_Indices_countUpTo");
            checkArgCount(nrhs, 1, "bindings_tests_Indices_countUpTo");
            int index = getScalar<int>(prhs[1], "index");
            int _value = bindings_tests_Indices_countUpTo(index);
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Indices_lastIndexOf") */ 1713794949: {
            static auto bindings_tests_Indices_lastIndexOf = libLoader.getFunc<int(*)(int)>("bindings_tests_Indices_lastIndexOf");
            checkArgCount(nrhs, 1, "bindings_tests_Indices_lastIndexOf");
            int count = getScalar<int>(prhs[1], "count");
            int _value = bindings_tests_Indices_lastIndexOf(count);
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Indices_offsetFrom") */ 1670565191: {
            static auto bindings_tests_Indices_offsetFrom = libLoader.getFunc<int(*)(int, int)>("bindings_tests_Indices_offsetFrom");
            checkArgCount(nrhs, 2, "bindings_tests_Indices_offsetFrom");
            int row = getScalar<int>(prhs[1], "row");
            int col = getScalar<int>(prhs[2], "col");
            int _value = bindings_tests_Indices_offsetFrom(row, col);
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Indices_fromRow") */ 4090672972: {
            static auto bindings_tests_Indices_fromRow = libLoader.getFunc<int(*)(int)>("bindings_tests_Indices_fromRow");
            checkArgCount(nrhs, 1, "bindings_tests_Indices_fromRow");
            int row = getScalar<int>(prhs[1], "row");
            int _value = bindings_tests_Indices_fromRow(row);
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Indices_echoSize") */ 4168124296: {
            static auto bindings_tests_Indices_echoSize = libLoader.getFunc<size_t(*)(size_t)>("bindings_tests_Indices_echoSize");
            checkArgCount(nrhs, 1, "bindings_tests_Indices_echoSize");
            size_t index = getScalar<size_t>(prhs[1], "index");
            size_t _value = bindings_tests_Indices_echoSize(index);
            if (nlhs > 0) plhs[0] = createMexSize(_value);
            break;
        }

        // Isolated
        case /* hash32_fnv1a("bindings_tests_Isolated_increment") */ 2513394315: {
            static auto bindings_tests_Isolated_increment = libLoader.getFunc<void(*)()>("bindings_tests_Isolated_increment");
            checkArgCount(nrhs, 0, "bindings_tests_Isolated_increment");
            bindings_tests_Isolated_increment();
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Isolated_getCounter") */ 4004172654: {
            static auto bindings_tests_Isolated_getCounter = libLoader.getFunc<int(*)()>("bindings_tests_Isolated_getCounter");
            checkArgCount(nrhs, 0, "bindings_tests_Isolated_getCounter");
            int _value = bindings_tests_Isolated_getCounter();
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Isolated_reset") */ 988326821: {
            static auto bindings_tests_Isolated_reset = libLoader.getFunc<void(*)()>("bindings_tests_Isolated_reset");
            checkArgCount(nrhs, 0, "bindings_tests_Isolated_reset");
            bindings_tests_Isolated_reset();
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Isolated_alwaysFails") */ 406300632: {
            static auto bindings_tests_Isolated_alwaysFails = libLoader.getFunc<void(*)(ErrorInfo*)>("bindings_tests_Isolated_alwaysFails");
            checkArgCount(nrhs, 0, "bindings_tests_Isolated_alwaysFails");
            ErrorInfo _error = {};
            bindings_tests_Isolated_alwaysFails(&_error);
            checkError(_error);
            break;
        }

        // Math
        case /* hash32_fnv1a("bindings_tests_Math_negate") */ 1195230251: {
            static auto bindings_tests_Math_negate = libLoader.getFunc<int(*)(int)>("bindings_tests_Math_negate");
            checkArgCount(nrhs, 1, "bindings_tests_Math_negate");
            int value = getScalar<int>(prhs[1], "value");
            int _value = bindings_tests_Math_negate(value);
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Math_nextChar") */ 2357386308: {
            static auto bindings_tests_Math_nextChar = libLoader.getFunc<unsigned short(*)(unsigned short)>("bindings_tests_Math_nextChar");
            checkArgCount(nrhs, 1, "bindings_tests_Math_nextChar");
            unsigned short value = getScalar<unsigned short>(prhs[1], "value");
            unsigned short _value = bindings_tests_Math_nextChar(value);
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Math_addInt8") */ 427473055: {
            static auto bindings_tests_Math_addInt8 = libLoader.getFunc<char(*)(char, char)>("bindings_tests_Math_addInt8");
            checkArgCount(nrhs, 2, "bindings_tests_Math_addInt8");
            char a = getScalar<char>(prhs[1], "a");
            char b = getScalar<char>(prhs[2], "b");
            char _value = bindings_tests_Math_addInt8(a, b);
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Math_addInt16") */ 3007438550: {
            static auto bindings_tests_Math_addInt16 = libLoader.getFunc<short(*)(short, short)>("bindings_tests_Math_addInt16");
            checkArgCount(nrhs, 2, "bindings_tests_Math_addInt16");
            short a = getScalar<short>(prhs[1], "a");
            short b = getScalar<short>(prhs[2], "b");
            short _value = bindings_tests_Math_addInt16(a, b);
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Math_addInt32") */ 1061632104: {
            static auto bindings_tests_Math_addInt32 = libLoader.getFunc<int(*)(int, int)>("bindings_tests_Math_addInt32");
            checkArgCount(nrhs, 2, "bindings_tests_Math_addInt32");
            int a = getScalar<int>(prhs[1], "a");
            int b = getScalar<int>(prhs[2], "b");
            int _value = bindings_tests_Math_addInt32(a, b);
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Math_addInt64") */ 3007291455: {
            static auto bindings_tests_Math_addInt64 = libLoader.getFunc<long long(*)(long long, long long)>("bindings_tests_Math_addInt64");
            checkArgCount(nrhs, 2, "bindings_tests_Math_addInt64");
            long long a = getScalar<long long>(prhs[1], "a");
            long long b = getScalar<long long>(prhs[2], "b");
            long long _value = bindings_tests_Math_addInt64(a, b);
            if (nlhs > 0) plhs[0] = createMexInt64(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Math_addFloat32") */ 524086999: {
            static auto bindings_tests_Math_addFloat32 = libLoader.getFunc<float(*)(float, float)>("bindings_tests_Math_addFloat32");
            checkArgCount(nrhs, 2, "bindings_tests_Math_addFloat32");
            float a = getScalar<float>(prhs[1], "a");
            float b = getScalar<float>(prhs[2], "b");
            float _value = bindings_tests_Math_addFloat32(a, b);
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Math_addFloat64") */ 2268120732: {
            static auto bindings_tests_Math_addFloat64 = libLoader.getFunc<double(*)(double, double)>("bindings_tests_Math_addFloat64");
            checkArgCount(nrhs, 2, "bindings_tests_Math_addFloat64");
            double a = getScalar<double>(prhs[1], "a");
            double b = getScalar<double>(prhs[2], "b");
            double _value = bindings_tests_Math_addFloat64(a, b);
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Math_addSize") */ 2854157469: {
            static auto bindings_tests_Math_addSize = libLoader.getFunc<size_t(*)(size_t, size_t)>("bindings_tests_Math_addSize");
            checkArgCount(nrhs, 2, "bindings_tests_Math_addSize");
            size_t a = getScalar<size_t>(prhs[1], "a");
            size_t b = getScalar<size_t>(prhs[2], "b");
            size_t _value = bindings_tests_Math_addSize(a, b);
            if (nlhs > 0) plhs[0] = createMexSize(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Math_sizeToInt32") */ 3376500095: {
            static auto bindings_tests_Math_sizeToInt32 = libLoader.getFunc<int(*)(size_t)>("bindings_tests_Math_sizeToInt32");
            checkArgCount(nrhs, 1, "bindings_tests_Math_sizeToInt32");
            size_t value = getScalar<size_t>(prhs[1], "value");
            int _value = bindings_tests_Math_sizeToInt32(value);
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Math_maxInt32") */ 2890558683: {
            static auto bindings_tests_Math_maxInt32 = libLoader.getFunc<int(*)()>("bindings_tests_Math_maxInt32");
            checkArgCount(nrhs, 0, "bindings_tests_Math_maxInt32");
            int _value = bindings_tests_Math_maxInt32();
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Math_maxSize") */ 677022296: {
            static auto bindings_tests_Math_maxSize = libLoader.getFunc<size_t(*)()>("bindings_tests_Math_maxSize");
            checkArgCount(nrhs, 0, "bindings_tests_Math_maxSize");
            size_t _value = bindings_tests_Math_maxSize();
            if (nlhs > 0) plhs[0] = createMexSize(_value);
            break;
        }

        // Overloads
        case /* hash32_fnv1a("bindings_tests_Overloads_scale") */ 3663307748: {
            static auto bindings_tests_Overloads_scale = libLoader.getFunc<int(*)(int, int)>("bindings_tests_Overloads_scale");
            checkArgCount(nrhs, 2, "bindings_tests_Overloads_scale");
            int value = getScalar<int>(prhs[1], "value");
            int factor = getScalar<int>(prhs[2], "factor");
            int _value = bindings_tests_Overloads_scale(value, factor);
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Overloads_greet") */ 3053209571: {
            static auto bindings_tests_Overloads_greet = libLoader.getFunc<const char*(*)(const char*, const char*)>("bindings_tests_Overloads_greet");
            checkArgCount(nrhs, 2, "bindings_tests_Overloads_greet");
            MatlabString name(prhs[1]);
            MatlabString greeting(prhs[2]);
            const char* _value = bindings_tests_Overloads_greet(name, greeting);
            if (nlhs > 0) plhs[0] = createMexString(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Overloads_total") */ 161722184: {
            static auto bindings_tests_Overloads_total = libLoader.getFunc<int(*)(int, int)>("bindings_tests_Overloads_total");
            checkArgCount(nrhs, 2, "bindings_tests_Overloads_total");
            int sum = getScalar<int>(prhs[1], "sum");
            int factor = getScalar<int>(prhs[2], "factor");
            int _value = bindings_tests_Overloads_total(sum, factor);
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Overloads_gain") */ 1095741721: {
            static auto bindings_tests_Overloads_gain = libLoader.getFunc<double(*)(int, double)>("bindings_tests_Overloads_gain");
            checkArgCount(nrhs, 2, "bindings_tests_Overloads_gain");
            int invert = getScalar<int>(prhs[1], "invert");
            double scale = getScalar<double>(prhs[2], "scale");
            double _value = bindings_tests_Overloads_gain(invert, scale);
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Overloads_echoIndex") */ 2239329933: {
            static auto bindings_tests_Overloads_echoIndex = libLoader.getFunc<int(*)(int)>("bindings_tests_Overloads_echoIndex");
            checkArgCount(nrhs, 1, "bindings_tests_Overloads_echoIndex");
            int index = getScalar<int>(prhs[1], "index");
            int _value = bindings_tests_Overloads_echoIndex(index);
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Overloads_answer") */ 340247328: {
            static auto bindings_tests_Overloads_answer = libLoader.getFunc<int(*)(int)>("bindings_tests_Overloads_answer");
            checkArgCount(nrhs, 1, "bindings_tests_Overloads_answer");
            int multiplier = getScalar<int>(prhs[1], "multiplier");
            int _value = bindings_tests_Overloads_answer(multiplier);
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Overloads_renamed") */ 3144181804: {
            static auto bindings_tests_Overloads_renamed = libLoader.getFunc<int(*)()>("bindings_tests_Overloads_renamed");
            checkArgCount(nrhs, 0, "bindings_tests_Overloads_renamed");
            int _value = bindings_tests_Overloads_renamed();
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }

        // Palette
        case /* hash32_fnv1a("bindings_tests_Palette_next") */ 980723931: {
            static auto bindings_tests_Palette_next = libLoader.getFunc<int(*)(int)>("bindings_tests_Palette_next");
            checkArgCount(nrhs, 1, "bindings_tests_Palette_next");
            int color = getScalar<int>(prhs[1], "color");
            int _value = bindings_tests_Palette_next(color);
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Palette_nameOf") */ 1270025800: {
            static auto bindings_tests_Palette_nameOf = libLoader.getFunc<const char*(*)(int)>("bindings_tests_Palette_nameOf");
            checkArgCount(nrhs, 1, "bindings_tests_Palette_nameOf");
            int color = getScalar<int>(prhs[1], "color");
            const char* _value = bindings_tests_Palette_nameOf(color);
            if (nlhs > 0) plhs[0] = createMexString(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Palette_ordinalOf") */ 3002810686: {
            static auto bindings_tests_Palette_ordinalOf = libLoader.getFunc<int(*)(int)>("bindings_tests_Palette_ordinalOf");
            checkArgCount(nrhs, 1, "bindings_tests_Palette_ordinalOf");
            int color = getScalar<int>(prhs[1], "color");
            int _value = bindings_tests_Palette_ordinalOf(color);
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Palette_describe") */ 3040758575: {
            static auto bindings_tests_Palette_describe = libLoader.getFunc<const char*(*)(int, int)>("bindings_tests_Palette_describe");
            checkArgCount(nrhs, 2, "bindings_tests_Palette_describe");
            int color = getScalar<int>(prhs[1], "color");
            int marker = getScalar<int>(prhs[2], "marker");
            const char* _value = bindings_tests_Palette_describe(color, marker);
            if (nlhs > 0) plhs[0] = createMexString(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Palette_fromOrdinal") */ 3004775343: {
            static auto bindings_tests_Palette_fromOrdinal = libLoader.getFunc<int(*)(int)>("bindings_tests_Palette_fromOrdinal");
            checkArgCount(nrhs, 1, "bindings_tests_Palette_fromOrdinal");
            int ordinal = getScalar<int>(prhs[1], "ordinal");
            int _value = bindings_tests_Palette_fromOrdinal(ordinal);
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }

        // Strings
        case /* hash32_fnv1a("bindings_tests_Strings_echo") */ 606482612: {
            static auto bindings_tests_Strings_echo = libLoader.getFunc<const char*(*)(const char*)>("bindings_tests_Strings_echo");
            checkArgCount(nrhs, 1, "bindings_tests_Strings_echo");
            MatlabString value(prhs[1]);
            const char* _value = bindings_tests_Strings_echo(value);
            if (nlhs > 0) plhs[0] = createMexString(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Strings_concat") */ 2506590541: {
            static auto bindings_tests_Strings_concat = libLoader.getFunc<const char*(*)(const char*, const char*)>("bindings_tests_Strings_concat");
            checkArgCount(nrhs, 2, "bindings_tests_Strings_concat");
            MatlabString a(prhs[1]);
            MatlabString b(prhs[2]);
            const char* _value = bindings_tests_Strings_concat(a, b);
            if (nlhs > 0) plhs[0] = createMexString(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Strings_utf8Length") */ 3321146102: {
            static auto bindings_tests_Strings_utf8Length = libLoader.getFunc<size_t(*)(const char*)>("bindings_tests_Strings_utf8Length");
            checkArgCount(nrhs, 1, "bindings_tests_Strings_utf8Length");
            MatlabString value(prhs[1]);
            size_t _value = bindings_tests_Strings_utf8Length(value);
            if (nlhs > 0) plhs[0] = createMexSize(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Strings_utf16Length") */ 724693977: {
            static auto bindings_tests_Strings_utf16Length = libLoader.getFunc<size_t(*)(const char*)>("bindings_tests_Strings_utf16Length");
            checkArgCount(nrhs, 1, "bindings_tests_Strings_utf16Length");
            MatlabString value(prhs[1]);
            size_t _value = bindings_tests_Strings_utf16Length(value);
            if (nlhs > 0) plhs[0] = createMexSize(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Strings_empty") */ 2645586654: {
            static auto bindings_tests_Strings_empty = libLoader.getFunc<const char*(*)()>("bindings_tests_Strings_empty");
            checkArgCount(nrhs, 0, "bindings_tests_Strings_empty");
            const char* _value = bindings_tests_Strings_empty();
            if (nlhs > 0) plhs[0] = createMexString(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Strings_nullString") */ 1680242997: {
            static auto bindings_tests_Strings_nullString = libLoader.getFunc<const char*(*)()>("bindings_tests_Strings_nullString");
            checkArgCount(nrhs, 0, "bindings_tests_Strings_nullString");
            const char* _value = bindings_tests_Strings_nullString();
            if (nlhs > 0) plhs[0] = createMexString(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Strings_join") */ 3337485289: {
            static auto bindings_tests_Strings_join = libLoader.getFunc<const char*(*)(const char**, size_t, const char*)>("bindings_tests_Strings_join");
            checkArgCount(nrhs, 3, "bindings_tests_Strings_join");
            MatlabStringArray values(prhs[1]);
            size_t count = getScalar<size_t>(prhs[2], "count");
            MatlabString separator(prhs[3]);
            const char* _value = bindings_tests_Strings_join(values, count, separator);
            if (nlhs > 0) plhs[0] = createMexString(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Strings_repeat") */ 1382401850: {
            static auto bindings_tests_Strings_repeat = libLoader.getFunc<const char*(*)(const char*, size_t)>("bindings_tests_Strings_repeat");
            checkArgCount(nrhs, 2, "bindings_tests_Strings_repeat");
            MatlabString value(prhs[1]);
            size_t times = getScalar<size_t>(prhs[2], "times");
            const char* _value = bindings_tests_Strings_repeat(value, times);
            if (nlhs > 0) plhs[0] = createMexString(_value);
            break;
        }

        // Structs
        case /* hash32_fnv1a("bindings_tests_Structs_fillFieldTypes") */ 467930633: {
            static auto bindings_tests_Structs_fillFieldTypes = libLoader.getFunc<FieldTypes(*)(unsigned short, short*, long long int*, void*)>("bindings_tests_Structs_fillFieldTypes");
            checkArgCount(nrhs, 4, "bindings_tests_Structs_fillFieldTypes");
            unsigned short unit = getScalar<unsigned short>(prhs[1], "unit");
            MatlabArray<int16_t, mxINT16_CLASS> shorts(prhs[2]);
            MatlabArray<int64_t, mxINT64_CLASS> longs(prhs[3]);
            void* context = getPtr<void*>(prhs[4]);
            FieldTypes _value = bindings_tests_Structs_fillFieldTypes(unit, shorts, longs, context);
            if (nlhs > 0) plhs[0] = createMexFieldTypes(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Structs_totalPower") */ 3391500522: {
            static auto bindings_tests_Structs_totalPower = libLoader.getFunc<double(*)(StatusInfo, double)>("bindings_tests_Structs_totalPower");
            checkArgCount(nrhs, 2, "bindings_tests_Structs_totalPower");
            StatusInfo status = getStatusInfo(prhs[1]);
            double currentAmps = getScalar<double>(prhs[2], "currentAmps");
            double _value = bindings_tests_Structs_totalPower(status, currentAmps);
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Structs_checkedVoltage") */ 2009819672: {
            static auto bindings_tests_Structs_checkedVoltage = libLoader.getFunc<double(*)(ErrorInfo*, StatusInfo)>("bindings_tests_Structs_checkedVoltage");
            checkArgCount(nrhs, 1, "bindings_tests_Structs_checkedVoltage");
            ErrorInfo _error = {};
            StatusInfo status = getStatusInfo(prhs[1]);
            double _value = bindings_tests_Structs_checkedVoltage(&_error, status);
            checkError(_error);
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Structs_getStatus") */ 1750292541: {
            static auto bindings_tests_Structs_getStatus = libLoader.getFunc<StatusInfo(*)(int)>("bindings_tests_Structs_getStatus");
            checkArgCount(nrhs, 1, "bindings_tests_Structs_getStatus");
            int index = getScalar<int>(prhs[1], "index");
            StatusInfo _value = bindings_tests_Structs_getStatus(index);
            if (nlhs > 0) plhs[0] = createMexStatusInfo(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Structs_getCheckedStatus") */ 903593326: {
            static auto bindings_tests_Structs_getCheckedStatus = libLoader.getFunc<StatusInfo(*)(ErrorInfo*, int)>("bindings_tests_Structs_getCheckedStatus");
            checkArgCount(nrhs, 1, "bindings_tests_Structs_getCheckedStatus");
            ErrorInfo _error = {};
            int index = getScalar<int>(prhs[1], "index");
            StatusInfo _value = bindings_tests_Structs_getCheckedStatus(&_error, index);
            checkError(_error);
            if (nlhs > 0) plhs[0] = createMexStatusInfo(_value);
            break;
        }


        default:
            mexErrMsgIdAndTxt("bindings_tests:NotFound", "Function hash %08X not found.", funcHash);
    }

}

#ifdef __cplusplus
}
#endif
