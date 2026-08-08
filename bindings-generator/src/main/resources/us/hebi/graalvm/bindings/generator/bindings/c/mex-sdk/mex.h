// Minimal MEX and Matrix API declarations for building the generated MEX gateway
// without a MATLAB installation. Written from the documented MathWorks C API
// reference, not derived from MathWorks header files. Covers exactly the subset
// the generated gateway uses, targeting the 64-bit interleaved-complex API
// introduced with R2018a (API version 800, hence the _800 export names).
//
// A MEX file built against these declarations is link-compatible with one built
// against a real MATLAB tree with MATLAB_DEFAULT_RELEASE=R2018a.
#ifndef MEX_SDK_MINIMAL_H
#define MEX_SDK_MINIMAL_H

#include <stddef.h>
#include <stdint.h>

#ifndef __cplusplus
#include <stdbool.h>
#endif

#ifdef __cplusplus
extern "C" {
#endif

// The mxArray is opaque, all access goes through the accessor functions.
typedef struct mxArray_tag mxArray;

// MATLAB char data is UTF-16 code units.
typedef uint16_t mxChar;

// MATLAB logical data is one byte per element.
typedef bool mxLogical;

// 64-bit MATLAB uses pointer-width sizes and indices.
typedef size_t mwSize;
typedef size_t mwIndex;

typedef enum {
    mxUNKNOWN_CLASS = 0,
    mxCELL_CLASS,
    mxSTRUCT_CLASS,
    mxLOGICAL_CLASS,
    mxCHAR_CLASS,
    mxVOID_CLASS,
    mxDOUBLE_CLASS,
    mxSINGLE_CLASS,
    mxINT8_CLASS,
    mxUINT8_CLASS,
    mxINT16_CLASS,
    mxUINT16_CLASS,
    mxINT32_CLASS,
    mxUINT32_CLASS,
    mxINT64_CLASS,
    mxUINT64_CLASS
} mxClassID;

typedef enum {
    mxREAL = 0,
    mxCOMPLEX
} mxComplexity;

// Matrix API (libmx)
char* mxArrayToUTF8String_800(const mxArray* pa);
void* mxCalloc_800(mwSize n, mwSize size);
mxArray* mxCreateCharArray_800(mwSize ndim, const mwSize* dims);
mxArray* mxCreateDoubleMatrix_800(mwSize m, mwSize n, mxComplexity flag);
mxArray* mxCreateDoubleScalar_800(double value);
mxArray* mxCreateLogicalScalar_800(mxLogical value);
mxArray* mxCreateNumericMatrix_800(mwSize m, mwSize n, mxClassID classid, mxComplexity flag);
mxArray* mxCreateStructMatrix_800(mwSize m, mwSize n, int nfields, const char** fieldnames);
void mxDestroyArray_800(mxArray* pa);
mxArray* mxDuplicateArray_800(const mxArray* in);
void mxFree_800(void* ptr);
mxArray* mxGetCell_800(const mxArray* pa, mwIndex index);
mxClassID mxGetClassID_800(const mxArray* pa);
void* mxGetData_800(const mxArray* pa);
mxArray* mxGetField_800(const mxArray* pa, mwIndex index, const char* fieldname);
size_t mxGetM_800(const mxArray* pa);
size_t mxGetN_800(const mxArray* pa);
size_t mxGetNumberOfElements_800(const mxArray* pa);
double* mxGetPr_800(const mxArray* pa);
double mxGetScalar_800(const mxArray* pa);
bool mxIsCell_800(const mxArray* pa);
bool mxIsChar_800(const mxArray* pa);
bool mxIsClass_800(const mxArray* pa, const char* classname);
bool mxIsComplex_800(const mxArray* pa);
bool mxIsEmpty_800(const mxArray* pa);
bool mxIsLogical_800(const mxArray* pa);
bool mxIsNumeric_800(const mxArray* pa);
bool mxIsUint64_800(const mxArray* pa);
void mxSetField_800(mxArray* pa, mwIndex index, const char* fieldname, mxArray* value);

// MEX API (libmex)
int mexAtExit_800(void (*exitFcn)(void));
int mexCallMATLAB_800(int nlhs, mxArray* plhs[], int nrhs, mxArray* prhs[], const char* functionName);
mxArray* mexCallMATLABWithTrap_800(int nlhs, mxArray* plhs[], int nrhs, mxArray* prhs[], const char* functionName);
void mexErrMsgIdAndTxt_800(const char* errorid, const char* errormsg, ...);
void mexErrMsgTxt_800(const char* errormsg);
void mexMakeArrayPersistent_800(mxArray* pa);
void mexWarnMsgIdAndTxt_800(const char* warningid, const char* warningmsg, ...);

// The unsuffixed documented names map onto the versioned exports.
#define mxArrayToUTF8String mxArrayToUTF8String_800
#define mxCalloc mxCalloc_800
#define mxCreateCharArray mxCreateCharArray_800
#define mxCreateDoubleMatrix mxCreateDoubleMatrix_800
#define mxCreateDoubleScalar mxCreateDoubleScalar_800
#define mxCreateLogicalScalar mxCreateLogicalScalar_800
#define mxCreateNumericMatrix mxCreateNumericMatrix_800
#define mxCreateStructMatrix mxCreateStructMatrix_800
#define mxDestroyArray mxDestroyArray_800
#define mxDuplicateArray mxDuplicateArray_800
#define mxFree mxFree_800
#define mxGetCell mxGetCell_800
#define mxGetClassID mxGetClassID_800
#define mxGetData mxGetData_800
#define mxGetField mxGetField_800
#define mxGetM mxGetM_800
#define mxGetN mxGetN_800
#define mxGetNumberOfElements mxGetNumberOfElements_800
#define mxGetPr mxGetPr_800
#define mxGetScalar mxGetScalar_800
#define mxIsCell mxIsCell_800
#define mxIsChar mxIsChar_800
#define mxIsClass mxIsClass_800
#define mxIsComplex mxIsComplex_800
#define mxIsEmpty mxIsEmpty_800
#define mxIsLogical mxIsLogical_800
#define mxIsNumeric mxIsNumeric_800
#define mxIsUint64 mxIsUint64_800
#define mxSetField mxSetField_800
#define mexAtExit mexAtExit_800
#define mexCallMATLAB mexCallMATLAB_800
#define mexCallMATLABWithTrap mexCallMATLABWithTrap_800
#define mexErrMsgIdAndTxt mexErrMsgIdAndTxt_800
#define mexErrMsgTxt mexErrMsgTxt_800
#define mexMakeArrayPersistent mexMakeArrayPersistent_800
#define mexWarnMsgIdAndTxt mexWarnMsgIdAndTxt_800

#ifdef __cplusplus
}
#endif

#endif // MEX_SDK_MINIMAL_H
