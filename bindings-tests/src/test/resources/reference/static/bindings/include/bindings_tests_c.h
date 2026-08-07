#pragma once
#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef void (*IndexCallbackFunction)(void* userData, int index);

// Error information for every method that can throw. A non-zero code
// means the call failed and the return value can be ignored. Contents
// are library owned and only valid until the next call on this thread.
typedef struct {
    const char* message;
    const char* id;
    int32_t code;
} bindings_tests_ErrorInfo;

// A (pointer, length) view of a contiguous byte buffer, passed and returned
// by value. Binary data rather than a string: embedded zeros are data.
// Inputs are caller-owned and only read during the call. Returned spans are
// library-owned and stay valid until the calling thread's next library call,
// so they have to be copied immediately.
typedef struct {
    const uint8_t* data;
    size_t length;
} bindings_tests_ByteSpan;

// A (pointer, length) view of a contiguous double buffer, passed and returned
// by value. Inputs are caller-owned and only read during the call. Returned
// spans are library-owned and stay valid until the calling thread's next
// library call, so they have to be copied immediately.
typedef struct {
    const double* data;
    size_t length;
} bindings_tests_DoubleSpan;

// A (pointer, length) view of a contiguous float buffer, passed and returned
// by value. Inputs are caller-owned and only read during the call. Returned
// spans are library-owned and stay valid until the calling thread's next
// library call, so they have to be copied immediately.
typedef struct {
    const float* data;
    size_t length;
} bindings_tests_FloatSpan;

// A (pointer, length) view of a contiguous int32 buffer, passed and returned
// by value. Inputs are caller-owned and only read during the call. Returned
// spans are library-owned and stay valid until the calling thread's next
// library call, so they have to be copied immediately.
typedef struct {
    const int32_t* data;
    size_t length;
} bindings_tests_IntSpan;

typedef struct {
    double voltage;
    int32_t state;
    int32_t enabled;
} bindings_tests_StatusInfo;

typedef struct bindings_tests_Counter_* bindings_tests_CounterPtr;
typedef struct custom_prefix_Naming_* custom_prefix_NamingPtr;
typedef struct bindings_tests_Shape_* bindings_tests_ShapePtr;
typedef struct bindings_tests_Circle_* bindings_tests_CirclePtr;
typedef struct bindings_tests_Square_* bindings_tests_SquarePtr;

typedef enum bindings_tests_CircleColor {
  bindings_tests_CircleColor_Red = 0,
  bindings_tests_CircleColor_Green = 1,
  bindings_tests_CircleColor_Blue = 2
} bindings_tests_CircleColor;
#if defined(_MSC_VER) || defined(__cplusplus)
static_assert(sizeof(bindings_tests_CircleColor) == 4, "error: unexpected size for bindings_tests_CircleColor enum");
#else
_Static_assert(sizeof(bindings_tests_CircleColor) == 4, "error: unexpected size for bindings_tests_CircleColor enum");
#endif

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
bindings_tests_CounterPtr bindings_tests_Counter_create(bindings_tests_ErrorInfo* _error, int start);
int bindings_tests_Counter_getValue(bindings_tests_CounterPtr obj);
void bindings_tests_Counter_setValue(bindings_tests_CounterPtr obj, int value);
void bindings_tests_Counter_increment(bindings_tests_CounterPtr obj);
void bindings_tests_Counter_add(bindings_tests_CounterPtr obj, int amount);
int bindings_tests_Counter_isClosed(bindings_tests_CounterPtr obj);
int bindings_tests_Counter_getClosedCount();
void bindings_tests_Counter_resetClosedCount();
void bindings_tests_Counter_release(bindings_tests_CounterPtr obj);

// ==== Naming ====
custom_prefix_NamingPtr custom_prefix_Naming_create(bindings_tests_ErrorInfo* _error, int startValue);
int custom_prefix_Naming_get_start_value(custom_prefix_NamingPtr obj);
int custom_prefix_Naming_add_two_numbers(int first, int second);
void custom_prefix_Naming_release(custom_prefix_NamingPtr obj);

// ==== Shape ====
const char* bindings_tests_Shape_getName(bindings_tests_ShapePtr obj);
void bindings_tests_Shape_setName(bindings_tests_ShapePtr obj, const char* name);
double bindings_tests_Shape_area(bindings_tests_ShapePtr obj);
int bindings_tests_Shape_sideCount(bindings_tests_ShapePtr obj);
void bindings_tests_Shape_release(bindings_tests_ShapePtr obj);

// ==== Circle ====
bindings_tests_CirclePtr bindings_tests_Circle_create(bindings_tests_ErrorInfo* _error, double radius);
double bindings_tests_Circle_getRadius(bindings_tests_CirclePtr obj);
bindings_tests_CircleColor bindings_tests_Circle_getColor(bindings_tests_CirclePtr obj);
double bindings_tests_Circle_area(bindings_tests_CirclePtr obj);
int bindings_tests_Circle_sideCount(bindings_tests_CirclePtr obj);
bindings_tests_ShapePtr bindings_tests_Circle_to_Shape(bindings_tests_CirclePtr ptr);

// ==== Square ====
bindings_tests_SquarePtr bindings_tests_Square_create(bindings_tests_ErrorInfo* _error, double side);
double bindings_tests_Square_getSide(bindings_tests_SquarePtr obj);
double bindings_tests_Square_area(bindings_tests_SquarePtr obj);
int bindings_tests_Square_sideCount(bindings_tests_SquarePtr obj);
bindings_tests_ShapePtr bindings_tests_Square_to_Shape(bindings_tests_SquarePtr ptr);

// ==== Arrays ====
double bindings_tests_Arrays_sumFloat64(const double* values, size_t count);
int bindings_tests_Arrays_sumInt32(const int* values, size_t count);
void bindings_tests_Arrays_fillFloat64(double* target, size_t count, double value);
void bindings_tests_Arrays_cumulativeSum(const double* source, double* target, size_t count);
void bindings_tests_Arrays_reverseFloat64(double* values, size_t count);
double bindings_tests_Arrays_dotProduct(bindings_tests_ErrorInfo* _error, bindings_tests_DoubleSpan x, bindings_tests_DoubleSpan y);
bindings_tests_DoubleSpan bindings_tests_Arrays_addOffset(bindings_tests_DoubleSpan values, double offset);
bindings_tests_IntSpan bindings_tests_Arrays_addOffsetInt32(bindings_tests_IntSpan values, int offset);
bindings_tests_FloatSpan bindings_tests_Arrays_addOffsetFloat32(bindings_tests_FloatSpan values, float offset);
bindings_tests_ByteSpan bindings_tests_Arrays_addOffsetUint8(bindings_tests_ByteSpan values, char offset);
bindings_tests_DoubleSpan bindings_tests_Arrays_getSequence(size_t count);

// ==== Callbacks ====
void bindings_tests_Callbacks_invokeTimes(IndexCallbackFunction func, void* userData, size_t times);
void bindings_tests_Callbacks_invokeOnce(IndexCallbackFunction func, void* userData, int index);

// ==== Errors ====
void bindings_tests_Errors_requireNonNegative(bindings_tests_ErrorInfo* _error, int value);
int bindings_tests_Errors_divideInt32(bindings_tests_ErrorInfo* _error, int a, int b);
double bindings_tests_Errors_failingFloat64(bindings_tests_ErrorInfo* _error, int shouldFail);
int bindings_tests_Errors_failingBool(bindings_tests_ErrorInfo* _error, int shouldFail);
const char* bindings_tests_Errors_failingString(bindings_tests_ErrorInfo* _error, int shouldFail);
int bindings_tests_Errors_failWithoutMessage(bindings_tests_ErrorInfo* _error);
void bindings_tests_Errors_neverThrows();

// ==== Indices ====
int bindings_tests_Indices_echo(int index);
int bindings_tests_Indices_countUpTo(int index);
int bindings_tests_Indices_lastIndexOf(int count);
int bindings_tests_Indices_offsetFrom(int row, int col);
int bindings_tests_Indices_fromRow(int row);
size_t bindings_tests_Indices_echoSize(size_t index);

// ==== Isolated ====
void bindings_tests_Isolated_increment();
int bindings_tests_Isolated_getCounter();
void bindings_tests_Isolated_reset();
void bindings_tests_Isolated_alwaysFails(bindings_tests_ErrorInfo* _error);

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
double bindings_tests_Overloads_gain(int invert, double scale);
int bindings_tests_Overloads_echoIndex(int index);
int bindings_tests_Overloads_answer(int multiplier);
int bindings_tests_Overloads_renamed();

// ==== Palette ====
bindings_tests_Color bindings_tests_Palette_next(bindings_tests_Color color);
const char* bindings_tests_Palette_nameOf(bindings_tests_Color color);
int bindings_tests_Palette_ordinalOf(bindings_tests_Color color);
const char* bindings_tests_Palette_describe(bindings_tests_Color color, bindings_tests_Marker marker);
bindings_tests_Color bindings_tests_Palette_fromOrdinal(int ordinal);

// ==== Strings ====
const char* bindings_tests_Strings_echo(const char* value);
const char* bindings_tests_Strings_concat(const char* a, const char* b);
size_t bindings_tests_Strings_utf8Length(const char* value);
size_t bindings_tests_Strings_utf16Length(const char* value);
const char* bindings_tests_Strings_empty();
const char* bindings_tests_Strings_nullString();
const char* bindings_tests_Strings_join(const char** values, size_t count, const char* separator);
const char* bindings_tests_Strings_repeat(const char* value, size_t times);

// ==== Structs ====
double bindings_tests_Structs_totalPower(bindings_tests_StatusInfo status, double currentAmps);
double bindings_tests_Structs_checkedVoltage(bindings_tests_ErrorInfo* _error, bindings_tests_StatusInfo status);
bindings_tests_StatusInfo bindings_tests_Structs_getStatus(int index);
bindings_tests_StatusInfo bindings_tests_Structs_getCheckedStatus(bindings_tests_ErrorInfo* _error, int index);

void bindings_tests_getLibraryVersion(int* major, int* minor, int* patch, int* build);
static __inline void bindings_tests_getHeaderVersion(int* major, int* minor, int* patch, int* build) {
    *major = 1;
    *minor = 2;
    *patch = 3;
    *build = 42;
}



#ifdef __cplusplus
}
#endif
