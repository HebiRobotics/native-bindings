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
    uint32_t hash = %%HASH_SEED%%;
    for (size_t i = 0; i < len; ++i) {
        hash = (hash ^ (uint32_t)((uint16_t) str[i] & 0xFF)) * %%HASH_PRIME%%;
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
            mexErrMsgIdAndTxt("%%LIB_NAME%%:TypeError", "Complex arrays are not supported.");
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
        mexErrMsgIdAndTxt("%%LIB_NAME%%:TypeError", "Double-quoted strings are not supported. Use single quotes 'char' arrays.");
    } else if(!mxIsChar(arg)) {
        mexErrMsgIdAndTxt("%%LIB_NAME%%:TypeError", "Expected single quotes 'char' array");
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
        if (!mxIsCell(arg)) mexErrMsgIdAndTxt("%%LIB_NAME%%:InvalidInput", "Expected cell array of strings.");
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
        mexErrMsgIdAndTxt("%%LIB_NAME%%:TypeError", "Pointer must be a uint64 scalar.");
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
        mexErrMsgIdAndTxt("%%LIB_NAME%%:PathError", "Failed to call locate_binary");
    }

    mxArray* pathArray = mxGetField(infoStruct, 0, "binPath");
    if (!pathArray) {
        mxDestroyArray(infoStruct);
        mexErrMsgIdAndTxt("%%LIB_NAME%%:PathError", "locate_binary did not return 'binPath'");
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
        if (!handle_) mexErrMsgIdAndTxt("%%LIB_NAME%%:LoadError", "Could not load library: %s", path.c_str());
    }

    template<typename T>
    T getFunc(const char* name) const {
#ifdef _WIN32
        FARPROC symbol = GetProcAddress((HMODULE)handle_, name);
#else
        void* symbol = dlsym(handle_, name);
#endif
        if(!symbol) {
            mexErrMsgIdAndTxt("%%LIB_NAME%%:LoadError", "Could not load symbol: %s", name);
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
         mexErrMsgIdAndTxt("%%LIB_NAME%%:InvalidInput", "First argument must be a function identifier.");
     }

    // Bind to the binary once per MATLAB session/clear
    static std::string binPath = getLibraryPath();
    static LibLoader libLoader(binPath);

    // Switch on a Perfect Hash (seeds are chosen until there are no collisions)
    switch (funcHash) {
        %%SWITCH_CASES%%

        default:
            mexErrMsgIdAndTxt("%%LIB_NAME%%:NotFound", "Function hash %08X not found.", funcHash);
    }

}

#ifdef __cplusplus
}
#endif