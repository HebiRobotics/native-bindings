#include "mex.h"
#include <cstddef>
#include <cstdint>
#include <cstring>
#include <string>

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

template <typename T>
inline T getScalar(const mxArray* arg) {
    return static_cast<T>(mxGetScalar(arg));
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

inline mxArray* createEmpty() {
    // Returns a 0x0 double array, which is the standard MATLAB 'null'
    return mxCreateDoubleMatrix(0, 0, mxREAL);
}

// Layout-compatible with the by-value span struct in the C ABI. Returned spans are
// library-owned and only valid until the next call, so they get copied immediately.
struct DoubleSpan {
    const double* data;
    size_t length;
};

inline mxArray* createMexDoubleArray(DoubleSpan span) {
    if (!span.data || span.length == 0) {
        return createEmpty();
    }
    mxArray* array = mxCreateDoubleMatrix(1, span.length, mxREAL);
    std::memcpy(mxGetPr(array), span.data, span.length * sizeof(double));
    return array;
}

inline mxArray* createMexString(const char* c_str) {
    return c_str ? mxCreateString(c_str) : createEmpty();
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
    // cerate issues. This gives us 2^53 bits for integers before loss of precision,
    // which is fine for everything but pointers.
    return mxCreateDoubleScalar(static_cast<double>(value));
}

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
            static auto bindings_tests_Counter_create = libLoader.getFunc<void*(*)(int)>("bindings_tests_Counter_create");
            int start = getScalar<int>(prhs[1]);
            void* _value = bindings_tests_Counter_create(start);
            if (nlhs > 0) plhs[0] = createMexPointer(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Counter_getValue") */ 1593629966: {
            static auto bindings_tests_Counter_getValue = libLoader.getFunc<int(*)(void*)>("bindings_tests_Counter_getValue");
            int _value = bindings_tests_Counter_getValue(getPtr<void*>(prhs[1]));
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Counter_setValue") */ 646568658: {
            static auto bindings_tests_Counter_setValue = libLoader.getFunc<void(*)(void*, int)>("bindings_tests_Counter_setValue");
            int value = getScalar<int>(prhs[2]);
            bindings_tests_Counter_setValue(getPtr<void*>(prhs[1]), value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Counter_increment") */ 793169964: {
            static auto bindings_tests_Counter_increment = libLoader.getFunc<void(*)(void*)>("bindings_tests_Counter_increment");
            bindings_tests_Counter_increment(getPtr<void*>(prhs[1]));
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Counter_add") */ 3215488130: {
            static auto bindings_tests_Counter_add = libLoader.getFunc<void(*)(void*, int)>("bindings_tests_Counter_add");
            int amount = getScalar<int>(prhs[2]);
            bindings_tests_Counter_add(getPtr<void*>(prhs[1]), amount);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Counter_isClosed") */ 400893135: {
            static auto bindings_tests_Counter_isClosed = libLoader.getFunc<int(*)(void*)>("bindings_tests_Counter_isClosed");
            int _value = bindings_tests_Counter_isClosed(getPtr<void*>(prhs[1]));
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Counter_getClosedCount") */ 4282038152: {
            static auto bindings_tests_Counter_getClosedCount = libLoader.getFunc<int(*)()>("bindings_tests_Counter_getClosedCount");
            int _value = bindings_tests_Counter_getClosedCount();
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Counter_resetClosedCount") */ 1164449961: {
            static auto bindings_tests_Counter_resetClosedCount = libLoader.getFunc<void(*)()>("bindings_tests_Counter_resetClosedCount");
            bindings_tests_Counter_resetClosedCount();
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Counter_release") */ 1194776268: {
            static auto bindings_tests_Counter_release = libLoader.getFunc<void(*)(void*)>("bindings_tests_Counter_release");
            bindings_tests_Counter_release(getPtr<void*>(prhs[1]));
            break;
        }

        // Shape
        case /* hash32_fnv1a("bindings_tests_Shape_getName") */ 109245415: {
            static auto bindings_tests_Shape_getName = libLoader.getFunc<const char*(*)(void*)>("bindings_tests_Shape_getName");
            const char* _value = bindings_tests_Shape_getName(getPtr<void*>(prhs[1]));
            if (nlhs > 0) plhs[0] = createMexString(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Shape_setName") */ 1722972291: {
            static auto bindings_tests_Shape_setName = libLoader.getFunc<void(*)(void*, const char*)>("bindings_tests_Shape_setName");
            MatlabString name(prhs[2]);
            bindings_tests_Shape_setName(getPtr<void*>(prhs[1]), name);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Shape_area") */ 557141481: {
            static auto bindings_tests_Shape_area = libLoader.getFunc<double(*)(void*)>("bindings_tests_Shape_area");
            double _value = bindings_tests_Shape_area(getPtr<void*>(prhs[1]));
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Shape_sideCount") */ 89654474: {
            static auto bindings_tests_Shape_sideCount = libLoader.getFunc<int(*)(void*)>("bindings_tests_Shape_sideCount");
            int _value = bindings_tests_Shape_sideCount(getPtr<void*>(prhs[1]));
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Shape_release") */ 687567245: {
            static auto bindings_tests_Shape_release = libLoader.getFunc<void(*)(void*)>("bindings_tests_Shape_release");
            bindings_tests_Shape_release(getPtr<void*>(prhs[1]));
            break;
        }

        // Circle
        case /* hash32_fnv1a("bindings_tests_Circle_create") */ 1129006167: {
            static auto bindings_tests_Circle_create = libLoader.getFunc<void*(*)(double)>("bindings_tests_Circle_create");
            double radius = getScalar<double>(prhs[1]);
            void* _value = bindings_tests_Circle_create(radius);
            if (nlhs > 0) plhs[0] = createMexPointer(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Circle_getRadius") */ 1721594359: {
            static auto bindings_tests_Circle_getRadius = libLoader.getFunc<double(*)(void*)>("bindings_tests_Circle_getRadius");
            double _value = bindings_tests_Circle_getRadius(getPtr<void*>(prhs[1]));
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Circle_area") */ 1913477986: {
            static auto bindings_tests_Circle_area = libLoader.getFunc<double(*)(void*)>("bindings_tests_Circle_area");
            double _value = bindings_tests_Circle_area(getPtr<void*>(prhs[1]));
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Circle_sideCount") */ 1258038027: {
            static auto bindings_tests_Circle_sideCount = libLoader.getFunc<int(*)(void*)>("bindings_tests_Circle_sideCount");
            int _value = bindings_tests_Circle_sideCount(getPtr<void*>(prhs[1]));
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Circle_to_Shape") */ 615662008: {
            static auto bindings_tests_Circle_to_Shape = libLoader.getFunc<void*(*)(void*)>("bindings_tests_Circle_to_Shape");
            if (nlhs > 0) plhs[0] = createMexPointer(bindings_tests_Circle_to_Shape(getPtr<void*>(prhs[1])));
            break;
        }

        // Square
        case /* hash32_fnv1a("bindings_tests_Square_create") */ 3588199438: {
            static auto bindings_tests_Square_create = libLoader.getFunc<void*(*)(double)>("bindings_tests_Square_create");
            double side = getScalar<double>(prhs[1]);
            void* _value = bindings_tests_Square_create(side);
            if (nlhs > 0) plhs[0] = createMexPointer(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Square_getSide") */ 656689845: {
            static auto bindings_tests_Square_getSide = libLoader.getFunc<double(*)(void*)>("bindings_tests_Square_getSide");
            double _value = bindings_tests_Square_getSide(getPtr<void*>(prhs[1]));
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Square_area") */ 1574289823: {
            static auto bindings_tests_Square_area = libLoader.getFunc<double(*)(void*)>("bindings_tests_Square_area");
            double _value = bindings_tests_Square_area(getPtr<void*>(prhs[1]));
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Square_sideCount") */ 3215798272: {
            static auto bindings_tests_Square_sideCount = libLoader.getFunc<int(*)(void*)>("bindings_tests_Square_sideCount");
            int _value = bindings_tests_Square_sideCount(getPtr<void*>(prhs[1]));
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Square_to_Shape") */ 2776091829: {
            static auto bindings_tests_Square_to_Shape = libLoader.getFunc<void*(*)(void*)>("bindings_tests_Square_to_Shape");
            if (nlhs > 0) plhs[0] = createMexPointer(bindings_tests_Square_to_Shape(getPtr<void*>(prhs[1])));
            break;
        }

        // Arrays
        case /* hash32_fnv1a("bindings_tests_Arrays_sumFloat64") */ 770314080: {
            static auto bindings_tests_Arrays_sumFloat64 = libLoader.getFunc<double(*)(const double*, size_t)>("bindings_tests_Arrays_sumFloat64");
            MatlabArray<double, mxDOUBLE_CLASS> values(prhs[1]);
            size_t count = getScalar<size_t>(prhs[2]);
            double _value = bindings_tests_Arrays_sumFloat64(values, count);
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Arrays_sumInt32") */ 486597228: {
            static auto bindings_tests_Arrays_sumInt32 = libLoader.getFunc<int(*)(const int*, size_t)>("bindings_tests_Arrays_sumInt32");
            MatlabArray<int32_t, mxINT32_CLASS> values(prhs[1]);
            size_t count = getScalar<size_t>(prhs[2]);
            int _value = bindings_tests_Arrays_sumInt32(values, count);
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Arrays_fillFloat64") */ 3695237744: {
            static auto bindings_tests_Arrays_fillFloat64 = libLoader.getFunc<void(*)(double*, size_t, double)>("bindings_tests_Arrays_fillFloat64");
            MatlabArray<double, mxDOUBLE_CLASS> target(prhs[1]);
            size_t count = getScalar<size_t>(prhs[2]);
            double value = getScalar<double>(prhs[3]);
            bindings_tests_Arrays_fillFloat64(target, count, value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Arrays_cumulativeSum") */ 106931725: {
            static auto bindings_tests_Arrays_cumulativeSum = libLoader.getFunc<void(*)(const double*, double*, size_t)>("bindings_tests_Arrays_cumulativeSum");
            MatlabArray<double, mxDOUBLE_CLASS> source(prhs[1]);
            MatlabArray<double, mxDOUBLE_CLASS> target(prhs[2]);
            size_t count = getScalar<size_t>(prhs[3]);
            bindings_tests_Arrays_cumulativeSum(source, target, count);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Arrays_reverseFloat64") */ 331409391: {
            static auto bindings_tests_Arrays_reverseFloat64 = libLoader.getFunc<void(*)(double*, size_t)>("bindings_tests_Arrays_reverseFloat64");
            MatlabArray<double, mxDOUBLE_CLASS> values(prhs[1]);
            size_t count = getScalar<size_t>(prhs[2]);
            bindings_tests_Arrays_reverseFloat64(values, count);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Arrays_dotProduct") */ 4019140823: {
            static auto bindings_tests_Arrays_dotProduct = libLoader.getFunc<double(*)(DoubleSpan, DoubleSpan)>("bindings_tests_Arrays_dotProduct");
            MatlabArray<double, mxDOUBLE_CLASS> x_arr(prhs[1]);
            DoubleSpan x = {x_arr.ptr, mxGetNumberOfElements(prhs[1])};
            MatlabArray<double, mxDOUBLE_CLASS> y_arr(prhs[2]);
            DoubleSpan y = {y_arr.ptr, mxGetNumberOfElements(prhs[2])};
            double _value = bindings_tests_Arrays_dotProduct(x, y);
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Arrays_addOffset") */ 3807979063: {
            static auto bindings_tests_Arrays_addOffset = libLoader.getFunc<DoubleSpan(*)(DoubleSpan, double)>("bindings_tests_Arrays_addOffset");
            MatlabArray<double, mxDOUBLE_CLASS> values_arr(prhs[1]);
            DoubleSpan values = {values_arr.ptr, mxGetNumberOfElements(prhs[1])};
            double offset = getScalar<double>(prhs[2]);
            DoubleSpan _value = bindings_tests_Arrays_addOffset(values, offset);
            if (nlhs > 0) plhs[0] = createMexDoubleArray(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Arrays_getSequence") */ 3328946390: {
            static auto bindings_tests_Arrays_getSequence = libLoader.getFunc<DoubleSpan(*)(size_t)>("bindings_tests_Arrays_getSequence");
            size_t count = getScalar<size_t>(prhs[1]);
            DoubleSpan _value = bindings_tests_Arrays_getSequence(count);
            if (nlhs > 0) plhs[0] = createMexDoubleArray(_value);
            break;
        }

        // Callbacks
        case /* hash32_fnv1a("bindings_tests_Callbacks_invokeTimes") */ 3311763887: {
            static auto bindings_tests_Callbacks_invokeTimes = libLoader.getFunc<void(*)(void*, void*, size_t)>("bindings_tests_Callbacks_invokeTimes");
            void* func = getPtr<void*>(prhs[1]);
            void* userData = getPtr<void*>(prhs[2]);
            size_t times = getScalar<size_t>(prhs[3]);
            bindings_tests_Callbacks_invokeTimes(func, userData, times);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Callbacks_invokeOnce") */ 3234909522: {
            static auto bindings_tests_Callbacks_invokeOnce = libLoader.getFunc<void(*)(void*, void*, int)>("bindings_tests_Callbacks_invokeOnce");
            void* func = getPtr<void*>(prhs[1]);
            void* userData = getPtr<void*>(prhs[2]);
            int index = getScalar<int>(prhs[3]);
            bindings_tests_Callbacks_invokeOnce(func, userData, index);
            break;
        }

        // Errors
        case /* hash32_fnv1a("bindings_tests_Errors_requireNonNegative") */ 3634828815: {
            static auto bindings_tests_Errors_requireNonNegative = libLoader.getFunc<int(*)(int)>("bindings_tests_Errors_requireNonNegative");
            int value = getScalar<int>(prhs[1]);
            int _value = bindings_tests_Errors_requireNonNegative(value);
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Errors_divideInt32") */ 876481109: {
            static auto bindings_tests_Errors_divideInt32 = libLoader.getFunc<int(*)(int, int)>("bindings_tests_Errors_divideInt32");
            int a = getScalar<int>(prhs[1]);
            int b = getScalar<int>(prhs[2]);
            int _value = bindings_tests_Errors_divideInt32(a, b);
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Errors_failingFloat64") */ 2587869218: {
            static auto bindings_tests_Errors_failingFloat64 = libLoader.getFunc<double(*)(int)>("bindings_tests_Errors_failingFloat64");
            int shouldFail = getScalar<int>(prhs[1]);
            double _value = bindings_tests_Errors_failingFloat64(shouldFail);
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Errors_failingBool") */ 2739720686: {
            static auto bindings_tests_Errors_failingBool = libLoader.getFunc<int(*)(int)>("bindings_tests_Errors_failingBool");
            int shouldFail = getScalar<int>(prhs[1]);
            int _value = bindings_tests_Errors_failingBool(shouldFail);
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Errors_failingString") */ 3708443195: {
            static auto bindings_tests_Errors_failingString = libLoader.getFunc<const char*(*)(int)>("bindings_tests_Errors_failingString");
            int shouldFail = getScalar<int>(prhs[1]);
            const char* _value = bindings_tests_Errors_failingString(shouldFail);
            if (nlhs > 0) plhs[0] = createMexString(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Errors_neverThrows") */ 2677745337: {
            static auto bindings_tests_Errors_neverThrows = libLoader.getFunc<void(*)()>("bindings_tests_Errors_neverThrows");
            bindings_tests_Errors_neverThrows();
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Errors_clearLastError") */ 2925181135: {
            static auto bindings_tests_Errors_clearLastError = libLoader.getFunc<void(*)()>("bindings_tests_Errors_clearLastError");
            bindings_tests_Errors_clearLastError();
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Errors_getLastError") */ 3813753256: {
            static auto bindings_tests_Errors_getLastError = libLoader.getFunc<const char*(*)()>("bindings_tests_Errors_getLastError");
            const char* _value = bindings_tests_Errors_getLastError();
            if (nlhs > 0) plhs[0] = createMexString(_value);
            break;
        }

        // Isolated
        case /* hash32_fnv1a("bindings_tests_Isolated_increment") */ 2513394315: {
            static auto bindings_tests_Isolated_increment = libLoader.getFunc<void(*)()>("bindings_tests_Isolated_increment");
            bindings_tests_Isolated_increment();
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Isolated_getCounter") */ 4004172654: {
            static auto bindings_tests_Isolated_getCounter = libLoader.getFunc<int(*)()>("bindings_tests_Isolated_getCounter");
            int _value = bindings_tests_Isolated_getCounter();
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Isolated_reset") */ 988326821: {
            static auto bindings_tests_Isolated_reset = libLoader.getFunc<void(*)()>("bindings_tests_Isolated_reset");
            bindings_tests_Isolated_reset();
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Isolated_alwaysFails") */ 406300632: {
            static auto bindings_tests_Isolated_alwaysFails = libLoader.getFunc<int(*)()>("bindings_tests_Isolated_alwaysFails");
            int _value = bindings_tests_Isolated_alwaysFails();
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Isolated_getLastError") */ 3203481780: {
            static auto bindings_tests_Isolated_getLastError = libLoader.getFunc<const char*(*)()>("bindings_tests_Isolated_getLastError");
            const char* _value = bindings_tests_Isolated_getLastError();
            if (nlhs > 0) plhs[0] = createMexString(_value);
            break;
        }

        // Math
        case /* hash32_fnv1a("bindings_tests_Math_negate") */ 1195230251: {
            static auto bindings_tests_Math_negate = libLoader.getFunc<int(*)(int)>("bindings_tests_Math_negate");
            int value = getScalar<int>(prhs[1]);
            int _value = bindings_tests_Math_negate(value);
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Math_nextChar") */ 2357386308: {
            static auto bindings_tests_Math_nextChar = libLoader.getFunc<unsigned short(*)(unsigned short)>("bindings_tests_Math_nextChar");
            unsigned short value = getScalar<unsigned short>(prhs[1]);
            unsigned short _value = bindings_tests_Math_nextChar(value);
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Math_addInt8") */ 427473055: {
            static auto bindings_tests_Math_addInt8 = libLoader.getFunc<char(*)(char, char)>("bindings_tests_Math_addInt8");
            char a = getScalar<char>(prhs[1]);
            char b = getScalar<char>(prhs[2]);
            char _value = bindings_tests_Math_addInt8(a, b);
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Math_addInt16") */ 3007438550: {
            static auto bindings_tests_Math_addInt16 = libLoader.getFunc<short(*)(short, short)>("bindings_tests_Math_addInt16");
            short a = getScalar<short>(prhs[1]);
            short b = getScalar<short>(prhs[2]);
            short _value = bindings_tests_Math_addInt16(a, b);
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Math_addInt32") */ 1061632104: {
            static auto bindings_tests_Math_addInt32 = libLoader.getFunc<int(*)(int, int)>("bindings_tests_Math_addInt32");
            int a = getScalar<int>(prhs[1]);
            int b = getScalar<int>(prhs[2]);
            int _value = bindings_tests_Math_addInt32(a, b);
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Math_addInt64") */ 3007291455: {
            static auto bindings_tests_Math_addInt64 = libLoader.getFunc<long long(*)(long long, long long)>("bindings_tests_Math_addInt64");
            long long a = getScalar<long long>(prhs[1]);
            long long b = getScalar<long long>(prhs[2]);
            long long _value = bindings_tests_Math_addInt64(a, b);
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Math_addFloat32") */ 524086999: {
            static auto bindings_tests_Math_addFloat32 = libLoader.getFunc<float(*)(float, float)>("bindings_tests_Math_addFloat32");
            float a = getScalar<float>(prhs[1]);
            float b = getScalar<float>(prhs[2]);
            float _value = bindings_tests_Math_addFloat32(a, b);
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Math_addFloat64") */ 2268120732: {
            static auto bindings_tests_Math_addFloat64 = libLoader.getFunc<double(*)(double, double)>("bindings_tests_Math_addFloat64");
            double a = getScalar<double>(prhs[1]);
            double b = getScalar<double>(prhs[2]);
            double _value = bindings_tests_Math_addFloat64(a, b);
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Math_addSize") */ 2854157469: {
            static auto bindings_tests_Math_addSize = libLoader.getFunc<size_t(*)(size_t, size_t)>("bindings_tests_Math_addSize");
            size_t a = getScalar<size_t>(prhs[1]);
            size_t b = getScalar<size_t>(prhs[2]);
            size_t _value = bindings_tests_Math_addSize(a, b);
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Math_sizeToInt32") */ 3376500095: {
            static auto bindings_tests_Math_sizeToInt32 = libLoader.getFunc<int(*)(size_t)>("bindings_tests_Math_sizeToInt32");
            size_t value = getScalar<size_t>(prhs[1]);
            int _value = bindings_tests_Math_sizeToInt32(value);
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Math_maxInt32") */ 2890558683: {
            static auto bindings_tests_Math_maxInt32 = libLoader.getFunc<int(*)()>("bindings_tests_Math_maxInt32");
            int _value = bindings_tests_Math_maxInt32();
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Math_maxSize") */ 677022296: {
            static auto bindings_tests_Math_maxSize = libLoader.getFunc<size_t(*)()>("bindings_tests_Math_maxSize");
            size_t _value = bindings_tests_Math_maxSize();
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }

        // Overloads
        case /* hash32_fnv1a("bindings_tests_Overloads_scale") */ 3663307748: {
            static auto bindings_tests_Overloads_scale = libLoader.getFunc<int(*)(int, int)>("bindings_tests_Overloads_scale");
            int value = getScalar<int>(prhs[1]);
            int factor = getScalar<int>(prhs[2]);
            int _value = bindings_tests_Overloads_scale(value, factor);
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Overloads_greet") */ 3053209571: {
            static auto bindings_tests_Overloads_greet = libLoader.getFunc<const char*(*)(const char*, const char*)>("bindings_tests_Overloads_greet");
            MatlabString name(prhs[1]);
            MatlabString greeting(prhs[2]);
            const char* _value = bindings_tests_Overloads_greet(name, greeting);
            if (nlhs > 0) plhs[0] = createMexString(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Overloads_total") */ 161722184: {
            static auto bindings_tests_Overloads_total = libLoader.getFunc<int(*)(int, int)>("bindings_tests_Overloads_total");
            int sum = getScalar<int>(prhs[1]);
            int factor = getScalar<int>(prhs[2]);
            int _value = bindings_tests_Overloads_total(sum, factor);
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Overloads_gain") */ 1095741721: {
            static auto bindings_tests_Overloads_gain = libLoader.getFunc<double(*)(int, double)>("bindings_tests_Overloads_gain");
            int invert = getScalar<int>(prhs[1]);
            double scale = getScalar<double>(prhs[2]);
            double _value = bindings_tests_Overloads_gain(invert, scale);
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Overloads_echoIndex") */ 2239329933: {
            static auto bindings_tests_Overloads_echoIndex = libLoader.getFunc<int(*)(int)>("bindings_tests_Overloads_echoIndex");
            int index = getScalar<int>(prhs[1]);
            int _value = bindings_tests_Overloads_echoIndex(index);
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Overloads_answer") */ 340247328: {
            static auto bindings_tests_Overloads_answer = libLoader.getFunc<int(*)(int)>("bindings_tests_Overloads_answer");
            int multiplier = getScalar<int>(prhs[1]);
            int _value = bindings_tests_Overloads_answer(multiplier);
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Overloads_renamed") */ 3144181804: {
            static auto bindings_tests_Overloads_renamed = libLoader.getFunc<int(*)()>("bindings_tests_Overloads_renamed");
            int _value = bindings_tests_Overloads_renamed();
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }

        // Palette
        case /* hash32_fnv1a("bindings_tests_Palette_next") */ 980723931: {
            static auto bindings_tests_Palette_next = libLoader.getFunc<int(*)(int)>("bindings_tests_Palette_next");
            int color = getScalar<int>(prhs[1]);
            int _value = bindings_tests_Palette_next(color);
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Palette_nameOf") */ 1270025800: {
            static auto bindings_tests_Palette_nameOf = libLoader.getFunc<const char*(*)(int)>("bindings_tests_Palette_nameOf");
            int color = getScalar<int>(prhs[1]);
            const char* _value = bindings_tests_Palette_nameOf(color);
            if (nlhs > 0) plhs[0] = createMexString(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Palette_ordinalOf") */ 3002810686: {
            static auto bindings_tests_Palette_ordinalOf = libLoader.getFunc<int(*)(int)>("bindings_tests_Palette_ordinalOf");
            int color = getScalar<int>(prhs[1]);
            int _value = bindings_tests_Palette_ordinalOf(color);
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Palette_describe") */ 3040758575: {
            static auto bindings_tests_Palette_describe = libLoader.getFunc<const char*(*)(int, int)>("bindings_tests_Palette_describe");
            int color = getScalar<int>(prhs[1]);
            int marker = getScalar<int>(prhs[2]);
            const char* _value = bindings_tests_Palette_describe(color, marker);
            if (nlhs > 0) plhs[0] = createMexString(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Palette_fromOrdinal") */ 3004775343: {
            static auto bindings_tests_Palette_fromOrdinal = libLoader.getFunc<int(*)(int)>("bindings_tests_Palette_fromOrdinal");
            int ordinal = getScalar<int>(prhs[1]);
            int _value = bindings_tests_Palette_fromOrdinal(ordinal);
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }

        // Strings
        case /* hash32_fnv1a("bindings_tests_Strings_echo") */ 606482612: {
            static auto bindings_tests_Strings_echo = libLoader.getFunc<const char*(*)(const char*)>("bindings_tests_Strings_echo");
            MatlabString value(prhs[1]);
            const char* _value = bindings_tests_Strings_echo(value);
            if (nlhs > 0) plhs[0] = createMexString(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Strings_concat") */ 2506590541: {
            static auto bindings_tests_Strings_concat = libLoader.getFunc<const char*(*)(const char*, const char*)>("bindings_tests_Strings_concat");
            MatlabString a(prhs[1]);
            MatlabString b(prhs[2]);
            const char* _value = bindings_tests_Strings_concat(a, b);
            if (nlhs > 0) plhs[0] = createMexString(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Strings_utf8Length") */ 3321146102: {
            static auto bindings_tests_Strings_utf8Length = libLoader.getFunc<size_t(*)(const char*)>("bindings_tests_Strings_utf8Length");
            MatlabString value(prhs[1]);
            size_t _value = bindings_tests_Strings_utf8Length(value);
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Strings_utf16Length") */ 724693977: {
            static auto bindings_tests_Strings_utf16Length = libLoader.getFunc<size_t(*)(const char*)>("bindings_tests_Strings_utf16Length");
            MatlabString value(prhs[1]);
            size_t _value = bindings_tests_Strings_utf16Length(value);
            if (nlhs > 0) plhs[0] = createMexScalar(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Strings_empty") */ 2645586654: {
            static auto bindings_tests_Strings_empty = libLoader.getFunc<const char*(*)()>("bindings_tests_Strings_empty");
            const char* _value = bindings_tests_Strings_empty();
            if (nlhs > 0) plhs[0] = createMexString(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Strings_nullString") */ 1680242997: {
            static auto bindings_tests_Strings_nullString = libLoader.getFunc<const char*(*)()>("bindings_tests_Strings_nullString");
            const char* _value = bindings_tests_Strings_nullString();
            if (nlhs > 0) plhs[0] = createMexString(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Strings_join") */ 3337485289: {
            static auto bindings_tests_Strings_join = libLoader.getFunc<const char*(*)(const char**, size_t, const char*)>("bindings_tests_Strings_join");
            MatlabStringArray values(prhs[1]);
            size_t count = getScalar<size_t>(prhs[2]);
            MatlabString separator(prhs[3]);
            const char* _value = bindings_tests_Strings_join(values, count, separator);
            if (nlhs > 0) plhs[0] = createMexString(_value);
            break;
        }
        case /* hash32_fnv1a("bindings_tests_Strings_repeat") */ 1382401850: {
            static auto bindings_tests_Strings_repeat = libLoader.getFunc<const char*(*)(const char*, size_t)>("bindings_tests_Strings_repeat");
            MatlabString value(prhs[1]);
            size_t times = getScalar<size_t>(prhs[2]);
            const char* _value = bindings_tests_Strings_repeat(value, times);
            if (nlhs > 0) plhs[0] = createMexString(_value);
            break;
        }


        default:
            mexErrMsgIdAndTxt("bindings_tests:NotFound", "Function hash %08X not found.", funcHash);
    }

}

#ifdef __cplusplus
}
#endif
