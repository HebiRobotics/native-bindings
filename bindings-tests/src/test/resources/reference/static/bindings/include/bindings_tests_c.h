#pragma once
#include <stddef.h>

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


// ==== Counter ====
bindings_tests_CounterPtr bindings_tests_Counter_create(int start);
int bindings_tests_Counter_getValue(bindings_tests_CounterPtr obj);
void bindings_tests_Counter_setValue(bindings_tests_CounterPtr obj, int value);
void bindings_tests_Counter_increment(bindings_tests_CounterPtr obj);
void bindings_tests_Counter_add(bindings_tests_CounterPtr obj, int amount);
int bindings_tests_Counter_isClosed(bindings_tests_CounterPtr obj);
int bindings_tests_Counter_getClosedCount();
void bindings_tests_Counter_resetClosedCount();
void bindings_tests_Counter_release(bindings_tests_CounterPtr obj);

// ==== Shape ====
const char* bindings_tests_Shape_getName(bindings_tests_ShapePtr obj);
void bindings_tests_Shape_setName(bindings_tests_ShapePtr obj, const char* name);
double bindings_tests_Shape_area(bindings_tests_ShapePtr obj);
int bindings_tests_Shape_sideCount(bindings_tests_ShapePtr obj);
void bindings_tests_Shape_release(bindings_tests_ShapePtr obj);

// ==== Circle ====
bindings_tests_CirclePtr bindings_tests_Circle_create(double radius);
double bindings_tests_Circle_getRadius(bindings_tests_CirclePtr obj);
double bindings_tests_Circle_area(bindings_tests_CirclePtr obj);
int bindings_tests_Circle_sideCount(bindings_tests_CirclePtr obj);
bindings_tests_ShapePtr bindings_tests_Circle_to_Shape(bindings_tests_CirclePtr ptr);

// ==== Square ====
bindings_tests_SquarePtr bindings_tests_Square_create(double side);
double bindings_tests_Square_getSide(bindings_tests_SquarePtr obj);
double bindings_tests_Square_area(bindings_tests_SquarePtr obj);
int bindings_tests_Square_sideCount(bindings_tests_SquarePtr obj);
bindings_tests_ShapePtr bindings_tests_Square_to_Shape(bindings_tests_SquarePtr ptr);

// ==== Arrays ====
double bindings_tests_Arrays_sumFloat64(double* values, size_t count);
int bindings_tests_Arrays_sumInt32(int* values, size_t count);
void bindings_tests_Arrays_fillFloat64(double* target, size_t count, double value);
void bindings_tests_Arrays_cumulativeSum(double* source, double* target, size_t count);
void bindings_tests_Arrays_reverseFloat64(double* values, size_t count);
double bindings_tests_Arrays_dotProduct(bindings_tests_double_span x, bindings_tests_double_span y);
bindings_tests_double_span bindings_tests_Arrays_addOffset(bindings_tests_double_span values, double offset);
bindings_tests_double_span bindings_tests_Arrays_getSequence(size_t count);

// ==== Callbacks ====
void bindings_tests_Callbacks_invokeTimes(IndexCallbackFunction func, void* userData, size_t times);
void bindings_tests_Callbacks_invokeOnce(IndexCallbackFunction func, void* userData, int index);

// ==== Errors ====
int bindings_tests_Errors_requireNonNegative(int value);
int bindings_tests_Errors_divideInt32(int a, int b);
double bindings_tests_Errors_failingFloat64(int shouldFail);
int bindings_tests_Errors_failingBool(int shouldFail);
const char* bindings_tests_Errors_failingString(int shouldFail);
void bindings_tests_Errors_neverThrows();
void bindings_tests_Errors_clearLastError();
const char* bindings_tests_Errors_getLastError();

// ==== Isolated ====
void bindings_tests_Isolated_increment();
int bindings_tests_Isolated_getCounter();
void bindings_tests_Isolated_reset();
int bindings_tests_Isolated_alwaysFails();
const char* bindings_tests_Isolated_getLastError();

// ==== Math ====
int bindings_tests_Math_negate(int value);
unsigned short bindings_tests_Math_nextChar(unsigned short value);
char bindings_tests_Math_addInt8(char a, char b);
short bindings_tests_Math_addInt16(short a, short b);
int bindings_tests_Math_addInt32(int a, int b);
long long bindings_tests_Math_addInt64(long long a, long long b);
float bindings_tests_Math_addFloat32(float a, float b);
double bindings_tests_Math_addFloat64(double a, double b);
size_t bindings_tests_Math_addSize(size_t a, size_t b);
int bindings_tests_Math_sizeToInt32(size_t value);
int bindings_tests_Math_maxInt32();
size_t bindings_tests_Math_maxSize();

// ==== Overloads ====
int bindings_tests_Overloads_scale(int value, int factor);
const char* bindings_tests_Overloads_greet(const char* name, const char* greeting);
int bindings_tests_Overloads_total(int sum, int factor);
int bindings_tests_Overloads_answer(int multiplier);
int bindings_tests_Overloads_renamed();

// ==== Palette ====
bindings_tests_Color bindings_tests_Palette_next(bindings_tests_Color color);
const char* bindings_tests_Palette_nameOf(bindings_tests_Color color);
int bindings_tests_Palette_ordinalOf(bindings_tests_Color color);
bindings_tests_Color bindings_tests_Palette_fromOrdinal(int ordinal);

// ==== Strings ====
const char* bindings_tests_Strings_echo(const char* value);
const char* bindings_tests_Strings_concat(const char* a, const char* b);
size_t bindings_tests_Strings_utf8Length(const char* value);
size_t bindings_tests_Strings_utf16Length(const char* value);
const char* bindings_tests_Strings_empty();
const char* bindings_tests_Strings_nullString();
const char* bindings_tests_Strings_join(char** values, size_t count, const char* separator);
const char* bindings_tests_Strings_repeat(const char* value, size_t times);

void bindings_tests_getLibraryVersion(int* major, int* minor, int* patch, int* build);
void bindings_tests_getHeaderVersion(int* major, int* minor, int* patch, int* build) {
    *major = 1;
    *minor = 2;
    *patch = 3;
    *build = 42;
}



#ifdef __cplusplus
}
#endif
