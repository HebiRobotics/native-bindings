#pragma once
#include <stddef.h>

#if !defined(_WIN32) && (defined(__unix__) || defined(__unix) || (defined(__APPLE__) && defined(__MACH__)))
# ifndef DLL_PUBLIC
#  define DLL_PUBLIC __attribute__ ((visibility ("default")))
# endif
# ifndef DLL_LOCAL
#  define DLL_LOCAL __attribute__ ((visibility ("hidden")))
# endif
#else
# ifndef DLL_PUBLIC
#  define DLL_PUBLIC __declspec( dllexport )
# endif
# ifndef DLL_LOCAL
#  define DLL_LOCAL
# endif
#endif

#ifdef __cplusplus
extern "C" {
#endif

#ifndef MATLAB
typedef void (*IndexCallbackFunction)(void* userData, int index);
#else
typedef void* IndexCallbackFunction; // Callbacks are not supported
#endif

typedef struct bindings_tests_Counter_* bindings_tests_CounterPtr;
typedef struct bindings_tests_Shape_* bindings_tests_ShapePtr;
typedef struct bindings_tests_Circle_* bindings_tests_CirclePtr;
typedef struct bindings_tests_Square_* bindings_tests_SquarePtr;

// A (pointer, length) view of a contiguous double buffer, passed and returned
// by value. Inputs are caller-owned and only read during the call. Returned
// spans are library-owned and stay valid until the calling thread's next
// library call, so they have to be copied immediately.
typedef struct {
    const double* data;
    size_t length;
} bindings_tests_double_span;

typedef enum bindings_tests_Color {
  bindings_tests_Color_Red = 0,
  bindings_tests_Color_Green = 1,
  bindings_tests_Color_Blue = 2
} bindings_tests_Color;
#if defined(_MSC_VER) || defined(__cplusplus)
static_assert(sizeof(bindings_tests_Color) == 4, "error: unexpected size for bindings_tests_Color enum");
#else
_Static_assert(sizeof(bindings_tests_Color) == 4, "error: unexpected size for bindings_tests_Color enum");
#endif

typedef enum bindings_tests_Marker {
  bindings_tests_Marker_Dot = 0,
  bindings_tests_Marker_Cross = 1
} bindings_tests_Marker;
#if defined(_MSC_VER) || defined(__cplusplus)
static_assert(sizeof(bindings_tests_Marker) == 4, "error: unexpected size for bindings_tests_Marker enum");
#else
_Static_assert(sizeof(bindings_tests_Marker) == 4, "error: unexpected size for bindings_tests_Marker enum");
#endif


// ==== Counter ====
DLL_PUBLIC bindings_tests_CounterPtr bindings_tests_Counter_create(int start);
DLL_PUBLIC int bindings_tests_Counter_getValue(bindings_tests_CounterPtr obj);
DLL_PUBLIC void bindings_tests_Counter_setValue(bindings_tests_CounterPtr obj, int value);
DLL_PUBLIC void bindings_tests_Counter_increment(bindings_tests_CounterPtr obj);
DLL_PUBLIC void bindings_tests_Counter_add(bindings_tests_CounterPtr obj, int amount);
DLL_PUBLIC int bindings_tests_Counter_isClosed(bindings_tests_CounterPtr obj);
DLL_PUBLIC int bindings_tests_Counter_getClosedCount();
DLL_PUBLIC void bindings_tests_Counter_resetClosedCount();
DLL_PUBLIC void bindings_tests_Counter_release(bindings_tests_CounterPtr obj);

// ==== Shape ====
DLL_PUBLIC const char* bindings_tests_Shape_getName(bindings_tests_ShapePtr obj);
DLL_PUBLIC void bindings_tests_Shape_setName(bindings_tests_ShapePtr obj, const char* name);
DLL_PUBLIC double bindings_tests_Shape_area(bindings_tests_ShapePtr obj);
DLL_PUBLIC int bindings_tests_Shape_sideCount(bindings_tests_ShapePtr obj);
DLL_PUBLIC void bindings_tests_Shape_release(bindings_tests_ShapePtr obj);

// ==== Circle ====
DLL_PUBLIC bindings_tests_CirclePtr bindings_tests_Circle_create(double radius);
DLL_PUBLIC double bindings_tests_Circle_getRadius(bindings_tests_CirclePtr obj);
DLL_PUBLIC double bindings_tests_Circle_area(bindings_tests_CirclePtr obj);
DLL_PUBLIC int bindings_tests_Circle_sideCount(bindings_tests_CirclePtr obj);
DLL_PUBLIC bindings_tests_ShapePtr bindings_tests_Circle_to_Shape(bindings_tests_CirclePtr ptr);

// ==== Square ====
DLL_PUBLIC bindings_tests_SquarePtr bindings_tests_Square_create(double side);
DLL_PUBLIC double bindings_tests_Square_getSide(bindings_tests_SquarePtr obj);
DLL_PUBLIC double bindings_tests_Square_area(bindings_tests_SquarePtr obj);
DLL_PUBLIC int bindings_tests_Square_sideCount(bindings_tests_SquarePtr obj);
DLL_PUBLIC bindings_tests_ShapePtr bindings_tests_Square_to_Shape(bindings_tests_SquarePtr ptr);

// ==== Arrays ====
DLL_PUBLIC double bindings_tests_Arrays_sumFloat64(const double* values, size_t count);
DLL_PUBLIC int bindings_tests_Arrays_sumInt32(const int* values, size_t count);
DLL_PUBLIC void bindings_tests_Arrays_fillFloat64(double* target, size_t count, double value);
DLL_PUBLIC void bindings_tests_Arrays_cumulativeSum(const double* source, double* target, size_t count);
DLL_PUBLIC void bindings_tests_Arrays_reverseFloat64(double* values, size_t count);
DLL_PUBLIC double bindings_tests_Arrays_dotProduct(bindings_tests_double_span x, bindings_tests_double_span y);
DLL_PUBLIC bindings_tests_double_span bindings_tests_Arrays_addOffset(bindings_tests_double_span values, double offset);
DLL_PUBLIC bindings_tests_double_span bindings_tests_Arrays_getSequence(size_t count);

// ==== Callbacks ====
DLL_PUBLIC void bindings_tests_Callbacks_invokeTimes(IndexCallbackFunction func, void* userData, size_t times);
DLL_PUBLIC void bindings_tests_Callbacks_invokeOnce(IndexCallbackFunction func, void* userData, int index);

// ==== Errors ====
DLL_PUBLIC int bindings_tests_Errors_requireNonNegative(int value);
DLL_PUBLIC int bindings_tests_Errors_divideInt32(int a, int b);
DLL_PUBLIC double bindings_tests_Errors_failingFloat64(int shouldFail);
DLL_PUBLIC int bindings_tests_Errors_failingBool(int shouldFail);
DLL_PUBLIC const char* bindings_tests_Errors_failingString(int shouldFail);
DLL_PUBLIC void bindings_tests_Errors_neverThrows();
DLL_PUBLIC void bindings_tests_Errors_clearLastError();
DLL_PUBLIC const char* bindings_tests_Errors_getLastError();

// ==== Indices ====
DLL_PUBLIC int bindings_tests_Indices_echo(int index);
DLL_PUBLIC int bindings_tests_Indices_countUpTo(int index);
DLL_PUBLIC int bindings_tests_Indices_lastIndexOf(int count);
DLL_PUBLIC int bindings_tests_Indices_offsetFrom(int row, int col);
DLL_PUBLIC int bindings_tests_Indices_fromRow(int row);
DLL_PUBLIC size_t bindings_tests_Indices_echoSize(size_t index);

// ==== Isolated ====
DLL_PUBLIC void bindings_tests_Isolated_increment();
DLL_PUBLIC int bindings_tests_Isolated_getCounter();
DLL_PUBLIC void bindings_tests_Isolated_reset();
DLL_PUBLIC int bindings_tests_Isolated_alwaysFails();
DLL_PUBLIC const char* bindings_tests_Isolated_getLastError();

// ==== Math ====
DLL_PUBLIC int bindings_tests_Math_negate(int value);
DLL_PUBLIC unsigned short bindings_tests_Math_nextChar(unsigned short value);
DLL_PUBLIC char bindings_tests_Math_addInt8(char a, char b);
DLL_PUBLIC short bindings_tests_Math_addInt16(short a, short b);
DLL_PUBLIC int bindings_tests_Math_addInt32(int a, int b);
DLL_PUBLIC long long bindings_tests_Math_addInt64(long long a, long long b);
DLL_PUBLIC float bindings_tests_Math_addFloat32(float a, float b);
DLL_PUBLIC double bindings_tests_Math_addFloat64(double a, double b);
DLL_PUBLIC size_t bindings_tests_Math_addSize(size_t a, size_t b);
DLL_PUBLIC int bindings_tests_Math_sizeToInt32(size_t value);
DLL_PUBLIC int bindings_tests_Math_maxInt32();
DLL_PUBLIC size_t bindings_tests_Math_maxSize();

// ==== Overloads ====
DLL_PUBLIC int bindings_tests_Overloads_scale(int value, int factor);
DLL_PUBLIC const char* bindings_tests_Overloads_greet(const char* name, const char* greeting);
DLL_PUBLIC int bindings_tests_Overloads_total(int sum, int factor);
DLL_PUBLIC double bindings_tests_Overloads_gain(int invert, double scale);
DLL_PUBLIC int bindings_tests_Overloads_echoIndex(int index);
DLL_PUBLIC int bindings_tests_Overloads_answer(int multiplier);
DLL_PUBLIC int bindings_tests_Overloads_renamed();

// ==== Palette ====
DLL_PUBLIC bindings_tests_Color bindings_tests_Palette_next(bindings_tests_Color color);
DLL_PUBLIC const char* bindings_tests_Palette_nameOf(bindings_tests_Color color);
DLL_PUBLIC int bindings_tests_Palette_ordinalOf(bindings_tests_Color color);
DLL_PUBLIC const char* bindings_tests_Palette_describe(bindings_tests_Color color, bindings_tests_Marker marker);
DLL_PUBLIC bindings_tests_Color bindings_tests_Palette_fromOrdinal(int ordinal);

// ==== Strings ====
DLL_PUBLIC const char* bindings_tests_Strings_echo(const char* value);
DLL_PUBLIC const char* bindings_tests_Strings_concat(const char* a, const char* b);
DLL_PUBLIC size_t bindings_tests_Strings_utf8Length(const char* value);
DLL_PUBLIC size_t bindings_tests_Strings_utf16Length(const char* value);
DLL_PUBLIC const char* bindings_tests_Strings_empty();
DLL_PUBLIC const char* bindings_tests_Strings_nullString();
DLL_PUBLIC const char* bindings_tests_Strings_join(const char** values, size_t count, const char* separator);
DLL_PUBLIC const char* bindings_tests_Strings_repeat(const char* value, size_t times);

DLL_PUBLIC void bindings_tests_getLibraryVersion(int* major, int* minor, int* patch, int* build);
void bindings_tests_getHeaderVersion(int* major, int* minor, int* patch, int* build) {
    *major = 1;
    *minor = 2;
    *patch = 3;
    *build = 42;
}



#ifdef __cplusplus
}
#endif
