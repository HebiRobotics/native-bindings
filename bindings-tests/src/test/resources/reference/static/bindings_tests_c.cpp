#include "bindings_tests_c.h"

// Aliases for the built-in GraalVM isolate API to avoid naming conflicts
extern "C" {
typedef struct bindings_tests_isolate_s bindings_tests_isolate_t;
typedef struct bindings_tests_isolatethread_s bindings_tests_isolatethread_t;

bindings_tests_isolatethread_t* bindings_tests_createIsolate();
bindings_tests_isolate_t* bindings_tests_getIsolate(bindings_tests_isolatethread_t* thread);
bindings_tests_isolatethread_t* bindings_tests_attachThread(bindings_tests_isolate_t* isolate);
bindings_tests_isolatethread_t* bindings_tests_getCurrentThread(bindings_tests_isolate_t* isolate);
int bindings_tests_detachThread(bindings_tests_isolatethread_t* thread);
int bindings_tests_tearDownIsolate(bindings_tests_isolatethread_t* thread);
}

namespace {
namespace isolate {

// Initial static creation. We attach the thread in the
// constructor, so we detach immediately to get an even
// number of operations.
bindings_tests_isolate_t* createIsolate() {
    bindings_tests_isolatethread_t* thread = bindings_tests_createIsolate();
    if (!thread) {
        return (bindings_tests_isolate_t*) nullptr;
    }
    bindings_tests_isolate_t* iso = bindings_tests_getIsolate(thread);
    bindings_tests_detachThread(thread);
    return iso;
}

// Thread-local attachment that lives for the lifetime of the thread
struct ThreadHandle {
    bindings_tests_isolatethread_t* ptr = nullptr;

    explicit ThreadHandle(bindings_tests_isolate_t* iso) {
        if (iso) ptr = bindings_tests_attachThread(iso);
    }

    ~ThreadHandle() {
        if (ptr) bindings_tests_detachThread(ptr);
    }
};

// Default global isolate
bindings_tests_isolatethread_t* global() {
    static bindings_tests_isolate_t* isolate = createIsolate();
    static thread_local ThreadHandle thread(isolate);
    return thread.ptr;
}

bindings_tests_isolatethread_t* secondary() {
     static bindings_tests_isolate_t* isolate = createIsolate();
     static thread_local ThreadHandle thread(isolate);
     return thread.ptr;
}

}
}

#ifdef __cplusplus
extern "C" {
#endif


// ==== Counter ====
bindings_tests_CounterPtr __bindings_tests_Counter_create(bindings_tests_isolatethread_t* isolate, int start);
bindings_tests_CounterPtr bindings_tests_Counter_create(int start) {
    return __bindings_tests_Counter_create(isolate::global(), start);
}
int __bindings_tests_Counter_getValue(bindings_tests_isolatethread_t* isolate, bindings_tests_CounterPtr obj);
int bindings_tests_Counter_getValue(bindings_tests_CounterPtr obj) {
    return __bindings_tests_Counter_getValue(isolate::global(), obj);
}
void __bindings_tests_Counter_setValue(bindings_tests_isolatethread_t* isolate, bindings_tests_CounterPtr obj, int value);
void bindings_tests_Counter_setValue(bindings_tests_CounterPtr obj, int value) {
    __bindings_tests_Counter_setValue(isolate::global(), obj, value);
}
void __bindings_tests_Counter_increment(bindings_tests_isolatethread_t* isolate, bindings_tests_CounterPtr obj);
void bindings_tests_Counter_increment(bindings_tests_CounterPtr obj) {
    __bindings_tests_Counter_increment(isolate::global(), obj);
}
void __bindings_tests_Counter_add(bindings_tests_isolatethread_t* isolate, bindings_tests_CounterPtr obj, int amount);
void bindings_tests_Counter_add(bindings_tests_CounterPtr obj, int amount) {
    __bindings_tests_Counter_add(isolate::global(), obj, amount);
}
int __bindings_tests_Counter_isClosed(bindings_tests_isolatethread_t* isolate, bindings_tests_CounterPtr obj);
int bindings_tests_Counter_isClosed(bindings_tests_CounterPtr obj) {
    return __bindings_tests_Counter_isClosed(isolate::global(), obj);
}
int __bindings_tests_Counter_getClosedCount(bindings_tests_isolatethread_t* isolate);
int bindings_tests_Counter_getClosedCount() {
    return __bindings_tests_Counter_getClosedCount(isolate::global());
}
void __bindings_tests_Counter_resetClosedCount(bindings_tests_isolatethread_t* isolate);
void bindings_tests_Counter_resetClosedCount() {
    __bindings_tests_Counter_resetClosedCount(isolate::global());
}
void __bindings_tests_Counter_release(bindings_tests_isolatethread_t* isolate, bindings_tests_CounterPtr obj);
void bindings_tests_Counter_release(bindings_tests_CounterPtr obj) {
    __bindings_tests_Counter_release(isolate::global(), obj);
}

// ==== Shape ====
const char* __bindings_tests_Shape_getName(bindings_tests_isolatethread_t* isolate, bindings_tests_ShapePtr obj);
const char* bindings_tests_Shape_getName(bindings_tests_ShapePtr obj) {
    return __bindings_tests_Shape_getName(isolate::global(), obj);
}
void __bindings_tests_Shape_setName(bindings_tests_isolatethread_t* isolate, bindings_tests_ShapePtr obj, const char* name);
void bindings_tests_Shape_setName(bindings_tests_ShapePtr obj, const char* name) {
    __bindings_tests_Shape_setName(isolate::global(), obj, name);
}
double __bindings_tests_Shape_area(bindings_tests_isolatethread_t* isolate, bindings_tests_ShapePtr obj);
double bindings_tests_Shape_area(bindings_tests_ShapePtr obj) {
    return __bindings_tests_Shape_area(isolate::global(), obj);
}
int __bindings_tests_Shape_sideCount(bindings_tests_isolatethread_t* isolate, bindings_tests_ShapePtr obj);
int bindings_tests_Shape_sideCount(bindings_tests_ShapePtr obj) {
    return __bindings_tests_Shape_sideCount(isolate::global(), obj);
}
void __bindings_tests_Shape_release(bindings_tests_isolatethread_t* isolate, bindings_tests_ShapePtr obj);
void bindings_tests_Shape_release(bindings_tests_ShapePtr obj) {
    __bindings_tests_Shape_release(isolate::global(), obj);
}

// ==== Circle ====
bindings_tests_CirclePtr __bindings_tests_Circle_create(bindings_tests_isolatethread_t* isolate, double radius);
bindings_tests_CirclePtr bindings_tests_Circle_create(double radius) {
    return __bindings_tests_Circle_create(isolate::global(), radius);
}
double __bindings_tests_Circle_getRadius(bindings_tests_isolatethread_t* isolate, bindings_tests_CirclePtr obj);
double bindings_tests_Circle_getRadius(bindings_tests_CirclePtr obj) {
    return __bindings_tests_Circle_getRadius(isolate::global(), obj);
}
bindings_tests_CircleColor __bindings_tests_Circle_getColor(bindings_tests_isolatethread_t* isolate, bindings_tests_CirclePtr obj);
bindings_tests_CircleColor bindings_tests_Circle_getColor(bindings_tests_CirclePtr obj) {
    return __bindings_tests_Circle_getColor(isolate::global(), obj);
}
double __bindings_tests_Circle_area(bindings_tests_isolatethread_t* isolate, bindings_tests_CirclePtr obj);
double bindings_tests_Circle_area(bindings_tests_CirclePtr obj) {
    return __bindings_tests_Circle_area(isolate::global(), obj);
}
int __bindings_tests_Circle_sideCount(bindings_tests_isolatethread_t* isolate, bindings_tests_CirclePtr obj);
int bindings_tests_Circle_sideCount(bindings_tests_CirclePtr obj) {
    return __bindings_tests_Circle_sideCount(isolate::global(), obj);
}
bindings_tests_ShapePtr bindings_tests_Circle_to_Shape(bindings_tests_CirclePtr ptr) {
  return reinterpret_cast<bindings_tests_ShapePtr>(ptr);
}

// ==== Square ====
bindings_tests_SquarePtr __bindings_tests_Square_create(bindings_tests_isolatethread_t* isolate, double side);
bindings_tests_SquarePtr bindings_tests_Square_create(double side) {
    return __bindings_tests_Square_create(isolate::global(), side);
}
double __bindings_tests_Square_getSide(bindings_tests_isolatethread_t* isolate, bindings_tests_SquarePtr obj);
double bindings_tests_Square_getSide(bindings_tests_SquarePtr obj) {
    return __bindings_tests_Square_getSide(isolate::global(), obj);
}
double __bindings_tests_Square_area(bindings_tests_isolatethread_t* isolate, bindings_tests_SquarePtr obj);
double bindings_tests_Square_area(bindings_tests_SquarePtr obj) {
    return __bindings_tests_Square_area(isolate::global(), obj);
}
int __bindings_tests_Square_sideCount(bindings_tests_isolatethread_t* isolate, bindings_tests_SquarePtr obj);
int bindings_tests_Square_sideCount(bindings_tests_SquarePtr obj) {
    return __bindings_tests_Square_sideCount(isolate::global(), obj);
}
bindings_tests_ShapePtr bindings_tests_Square_to_Shape(bindings_tests_SquarePtr ptr) {
  return reinterpret_cast<bindings_tests_ShapePtr>(ptr);
}

// ==== Arrays ====
double __bindings_tests_Arrays_sumFloat64(bindings_tests_isolatethread_t* isolate, const double* values, long long count);
double bindings_tests_Arrays_sumFloat64(const double* values, size_t count) {
    return __bindings_tests_Arrays_sumFloat64(isolate::global(), values, static_cast<long long>(count));
}
int __bindings_tests_Arrays_sumInt32(bindings_tests_isolatethread_t* isolate, const int* values, long long count);
int bindings_tests_Arrays_sumInt32(const int* values, size_t count) {
    return __bindings_tests_Arrays_sumInt32(isolate::global(), values, static_cast<long long>(count));
}
void __bindings_tests_Arrays_fillFloat64(bindings_tests_isolatethread_t* isolate, double* target, long long count, double value);
void bindings_tests_Arrays_fillFloat64(double* target, size_t count, double value) {
    __bindings_tests_Arrays_fillFloat64(isolate::global(), target, static_cast<long long>(count), value);
}
void __bindings_tests_Arrays_cumulativeSum(bindings_tests_isolatethread_t* isolate, const double* source, double* target, long long count);
void bindings_tests_Arrays_cumulativeSum(const double* source, double* target, size_t count) {
    __bindings_tests_Arrays_cumulativeSum(isolate::global(), source, target, static_cast<long long>(count));
}
void __bindings_tests_Arrays_reverseFloat64(bindings_tests_isolatethread_t* isolate, double* values, long long count);
void bindings_tests_Arrays_reverseFloat64(double* values, size_t count) {
    __bindings_tests_Arrays_reverseFloat64(isolate::global(), values, static_cast<long long>(count));
}
double __bindings_tests_Arrays_dotProduct(bindings_tests_isolatethread_t* isolate, const bindings_tests_double_span* x, const bindings_tests_double_span* y);
double bindings_tests_Arrays_dotProduct(bindings_tests_double_span x, bindings_tests_double_span y) {
    return __bindings_tests_Arrays_dotProduct(isolate::global(), &x, &y);
}
void __bindings_tests_Arrays_addOffset(bindings_tests_isolatethread_t* isolate, const bindings_tests_double_span* values, double offset, bindings_tests_double_span* spanOut);
bindings_tests_double_span bindings_tests_Arrays_addOffset(bindings_tests_double_span values, double offset) {
    bindings_tests_double_span result_;
    __bindings_tests_Arrays_addOffset(isolate::global(), &values, offset, &result_);
    return result_;
}
void __bindings_tests_Arrays_getSequence(bindings_tests_isolatethread_t* isolate, long long count, bindings_tests_double_span* spanOut);
bindings_tests_double_span bindings_tests_Arrays_getSequence(size_t count) {
    bindings_tests_double_span result_;
    __bindings_tests_Arrays_getSequence(isolate::global(), static_cast<long long>(count), &result_);
    return result_;
}

// ==== Callbacks ====
void __bindings_tests_Callbacks_invokeTimes(bindings_tests_isolatethread_t* isolate, IndexCallbackFunction func, void* userData, long long times);
void bindings_tests_Callbacks_invokeTimes(IndexCallbackFunction func, void* userData, size_t times) {
    __bindings_tests_Callbacks_invokeTimes(isolate::global(), func, userData, static_cast<long long>(times));
}
void __bindings_tests_Callbacks_invokeOnce(bindings_tests_isolatethread_t* isolate, IndexCallbackFunction func, void* userData, int index);
void bindings_tests_Callbacks_invokeOnce(IndexCallbackFunction func, void* userData, int index) {
    __bindings_tests_Callbacks_invokeOnce(isolate::global(), func, userData, index);
}

// ==== Errors ====
int __bindings_tests_Errors_requireNonNegative(bindings_tests_isolatethread_t* isolate, int value);
int bindings_tests_Errors_requireNonNegative(int value) {
    return __bindings_tests_Errors_requireNonNegative(isolate::global(), value);
}
int __bindings_tests_Errors_divideInt32(bindings_tests_isolatethread_t* isolate, int a, int b);
int bindings_tests_Errors_divideInt32(int a, int b) {
    return __bindings_tests_Errors_divideInt32(isolate::global(), a, b);
}
double __bindings_tests_Errors_failingFloat64(bindings_tests_isolatethread_t* isolate, int shouldFail);
double bindings_tests_Errors_failingFloat64(int shouldFail) {
    return __bindings_tests_Errors_failingFloat64(isolate::global(), shouldFail);
}
int __bindings_tests_Errors_failingBool(bindings_tests_isolatethread_t* isolate, int shouldFail);
int bindings_tests_Errors_failingBool(int shouldFail) {
    return __bindings_tests_Errors_failingBool(isolate::global(), shouldFail);
}
const char* __bindings_tests_Errors_failingString(bindings_tests_isolatethread_t* isolate, int shouldFail);
const char* bindings_tests_Errors_failingString(int shouldFail) {
    return __bindings_tests_Errors_failingString(isolate::global(), shouldFail);
}
void __bindings_tests_Errors_neverThrows(bindings_tests_isolatethread_t* isolate);
void bindings_tests_Errors_neverThrows() {
    __bindings_tests_Errors_neverThrows(isolate::global());
}
void __bindings_tests_Errors_clearLastError(bindings_tests_isolatethread_t* isolate);
void bindings_tests_Errors_clearLastError() {
    __bindings_tests_Errors_clearLastError(isolate::global());
}
const char* __bindings_tests_Errors_getLastError(bindings_tests_isolatethread_t* isolate);
const char* bindings_tests_Errors_getLastError() {
    return __bindings_tests_Errors_getLastError(isolate::global());
}

// ==== Indices ====
int __bindings_tests_Indices_echo(bindings_tests_isolatethread_t* isolate, int index);
int bindings_tests_Indices_echo(int index) {
    return __bindings_tests_Indices_echo(isolate::global(), index);
}
int __bindings_tests_Indices_countUpTo(bindings_tests_isolatethread_t* isolate, int index);
int bindings_tests_Indices_countUpTo(int index) {
    return __bindings_tests_Indices_countUpTo(isolate::global(), index);
}
int __bindings_tests_Indices_lastIndexOf(bindings_tests_isolatethread_t* isolate, int count);
int bindings_tests_Indices_lastIndexOf(int count) {
    return __bindings_tests_Indices_lastIndexOf(isolate::global(), count);
}
int __bindings_tests_Indices_offsetFrom(bindings_tests_isolatethread_t* isolate, int row, int col);
int bindings_tests_Indices_offsetFrom(int row, int col) {
    return __bindings_tests_Indices_offsetFrom(isolate::global(), row, col);
}
int __bindings_tests_Indices_fromRow(bindings_tests_isolatethread_t* isolate, int row);
int bindings_tests_Indices_fromRow(int row) {
    return __bindings_tests_Indices_fromRow(isolate::global(), row);
}
long long __bindings_tests_Indices_echoSize(bindings_tests_isolatethread_t* isolate, long long index);
size_t bindings_tests_Indices_echoSize(size_t index) {
    return __bindings_tests_Indices_echoSize(isolate::global(), static_cast<long long>(index));
}

// ==== Isolated ====
void __bindings_tests_Isolated_increment(bindings_tests_isolatethread_t* isolate);
void bindings_tests_Isolated_increment() {
    __bindings_tests_Isolated_increment(isolate::secondary());
}
int __bindings_tests_Isolated_getCounter(bindings_tests_isolatethread_t* isolate);
int bindings_tests_Isolated_getCounter() {
    return __bindings_tests_Isolated_getCounter(isolate::secondary());
}
void __bindings_tests_Isolated_reset(bindings_tests_isolatethread_t* isolate);
void bindings_tests_Isolated_reset() {
    __bindings_tests_Isolated_reset(isolate::secondary());
}
int __bindings_tests_Isolated_alwaysFails(bindings_tests_isolatethread_t* isolate);
int bindings_tests_Isolated_alwaysFails() {
    return __bindings_tests_Isolated_alwaysFails(isolate::secondary());
}

// ==== Math ====
int __bindings_tests_Math_negate(bindings_tests_isolatethread_t* isolate, int value);
int bindings_tests_Math_negate(int value) {
    return __bindings_tests_Math_negate(isolate::global(), value);
}
unsigned short __bindings_tests_Math_nextChar(bindings_tests_isolatethread_t* isolate, unsigned short value);
unsigned short bindings_tests_Math_nextChar(unsigned short value) {
    return __bindings_tests_Math_nextChar(isolate::global(), value);
}
char __bindings_tests_Math_addInt8(bindings_tests_isolatethread_t* isolate, char a, char b);
char bindings_tests_Math_addInt8(char a, char b) {
    return __bindings_tests_Math_addInt8(isolate::global(), a, b);
}
short __bindings_tests_Math_addInt16(bindings_tests_isolatethread_t* isolate, short a, short b);
short bindings_tests_Math_addInt16(short a, short b) {
    return __bindings_tests_Math_addInt16(isolate::global(), a, b);
}
int __bindings_tests_Math_addInt32(bindings_tests_isolatethread_t* isolate, int a, int b);
int bindings_tests_Math_addInt32(int a, int b) {
    return __bindings_tests_Math_addInt32(isolate::global(), a, b);
}
long long __bindings_tests_Math_addInt64(bindings_tests_isolatethread_t* isolate, long long a, long long b);
long long bindings_tests_Math_addInt64(long long a, long long b) {
    return __bindings_tests_Math_addInt64(isolate::global(), a, b);
}
float __bindings_tests_Math_addFloat32(bindings_tests_isolatethread_t* isolate, float a, float b);
float bindings_tests_Math_addFloat32(float a, float b) {
    return __bindings_tests_Math_addFloat32(isolate::global(), a, b);
}
double __bindings_tests_Math_addFloat64(bindings_tests_isolatethread_t* isolate, double a, double b);
double bindings_tests_Math_addFloat64(double a, double b) {
    return __bindings_tests_Math_addFloat64(isolate::global(), a, b);
}
long long __bindings_tests_Math_addSize(bindings_tests_isolatethread_t* isolate, long long a, long long b);
size_t bindings_tests_Math_addSize(size_t a, size_t b) {
    return __bindings_tests_Math_addSize(isolate::global(), static_cast<long long>(a), static_cast<long long>(b));
}
int __bindings_tests_Math_sizeToInt32(bindings_tests_isolatethread_t* isolate, long long value);
int bindings_tests_Math_sizeToInt32(size_t value) {
    return __bindings_tests_Math_sizeToInt32(isolate::global(), static_cast<long long>(value));
}
int __bindings_tests_Math_maxInt32(bindings_tests_isolatethread_t* isolate);
int bindings_tests_Math_maxInt32() {
    return __bindings_tests_Math_maxInt32(isolate::global());
}
long long __bindings_tests_Math_maxSize(bindings_tests_isolatethread_t* isolate);
size_t bindings_tests_Math_maxSize() {
    return __bindings_tests_Math_maxSize(isolate::global());
}

// ==== Overloads ====
int __bindings_tests_Overloads_scale(bindings_tests_isolatethread_t* isolate, int value, int factor);
int bindings_tests_Overloads_scale(int value, int factor) {
    return __bindings_tests_Overloads_scale(isolate::global(), value, factor);
}
const char* __bindings_tests_Overloads_greet(bindings_tests_isolatethread_t* isolate, const char* name, const char* greeting);
const char* bindings_tests_Overloads_greet(const char* name, const char* greeting) {
    return __bindings_tests_Overloads_greet(isolate::global(), name, greeting);
}
int __bindings_tests_Overloads_total(bindings_tests_isolatethread_t* isolate, int sum, int factor);
int bindings_tests_Overloads_total(int sum, int factor) {
    return __bindings_tests_Overloads_total(isolate::global(), sum, factor);
}
double __bindings_tests_Overloads_gain(bindings_tests_isolatethread_t* isolate, int invert, double scale);
double bindings_tests_Overloads_gain(int invert, double scale) {
    return __bindings_tests_Overloads_gain(isolate::global(), invert, scale);
}
int __bindings_tests_Overloads_echoIndex(bindings_tests_isolatethread_t* isolate, int index);
int bindings_tests_Overloads_echoIndex(int index) {
    return __bindings_tests_Overloads_echoIndex(isolate::global(), index);
}
int __bindings_tests_Overloads_answer(bindings_tests_isolatethread_t* isolate, int multiplier);
int bindings_tests_Overloads_answer(int multiplier) {
    return __bindings_tests_Overloads_answer(isolate::global(), multiplier);
}
int __bindings_tests_Overloads_renamed(bindings_tests_isolatethread_t* isolate);
int bindings_tests_Overloads_renamed() {
    return __bindings_tests_Overloads_renamed(isolate::global());
}

// ==== Palette ====
bindings_tests_Color __bindings_tests_Palette_next(bindings_tests_isolatethread_t* isolate, bindings_tests_Color color);
bindings_tests_Color bindings_tests_Palette_next(bindings_tests_Color color) {
    return __bindings_tests_Palette_next(isolate::global(), color);
}
const char* __bindings_tests_Palette_nameOf(bindings_tests_isolatethread_t* isolate, bindings_tests_Color color);
const char* bindings_tests_Palette_nameOf(bindings_tests_Color color) {
    return __bindings_tests_Palette_nameOf(isolate::global(), color);
}
int __bindings_tests_Palette_ordinalOf(bindings_tests_isolatethread_t* isolate, bindings_tests_Color color);
int bindings_tests_Palette_ordinalOf(bindings_tests_Color color) {
    return __bindings_tests_Palette_ordinalOf(isolate::global(), color);
}
const char* __bindings_tests_Palette_describe(bindings_tests_isolatethread_t* isolate, bindings_tests_Color color, bindings_tests_Marker marker);
const char* bindings_tests_Palette_describe(bindings_tests_Color color, bindings_tests_Marker marker) {
    return __bindings_tests_Palette_describe(isolate::global(), color, marker);
}
bindings_tests_Color __bindings_tests_Palette_fromOrdinal(bindings_tests_isolatethread_t* isolate, int ordinal);
bindings_tests_Color bindings_tests_Palette_fromOrdinal(int ordinal) {
    return __bindings_tests_Palette_fromOrdinal(isolate::global(), ordinal);
}

// ==== Strings ====
const char* __bindings_tests_Strings_echo(bindings_tests_isolatethread_t* isolate, const char* value);
const char* bindings_tests_Strings_echo(const char* value) {
    return __bindings_tests_Strings_echo(isolate::global(), value);
}
const char* __bindings_tests_Strings_concat(bindings_tests_isolatethread_t* isolate, const char* a, const char* b);
const char* bindings_tests_Strings_concat(const char* a, const char* b) {
    return __bindings_tests_Strings_concat(isolate::global(), a, b);
}
long long __bindings_tests_Strings_utf8Length(bindings_tests_isolatethread_t* isolate, const char* value);
size_t bindings_tests_Strings_utf8Length(const char* value) {
    return __bindings_tests_Strings_utf8Length(isolate::global(), value);
}
long long __bindings_tests_Strings_utf16Length(bindings_tests_isolatethread_t* isolate, const char* value);
size_t bindings_tests_Strings_utf16Length(const char* value) {
    return __bindings_tests_Strings_utf16Length(isolate::global(), value);
}
const char* __bindings_tests_Strings_empty(bindings_tests_isolatethread_t* isolate);
const char* bindings_tests_Strings_empty() {
    return __bindings_tests_Strings_empty(isolate::global());
}
const char* __bindings_tests_Strings_nullString(bindings_tests_isolatethread_t* isolate);
const char* bindings_tests_Strings_nullString() {
    return __bindings_tests_Strings_nullString(isolate::global());
}
const char* __bindings_tests_Strings_join(bindings_tests_isolatethread_t* isolate, const char** values, long long count, const char* separator);
const char* bindings_tests_Strings_join(const char** values, size_t count, const char* separator) {
    return __bindings_tests_Strings_join(isolate::global(), values, static_cast<long long>(count), separator);
}
const char* __bindings_tests_Strings_repeat(bindings_tests_isolatethread_t* isolate, const char* value, long long times);
const char* bindings_tests_Strings_repeat(const char* value, size_t times) {
    return __bindings_tests_Strings_repeat(isolate::global(), value, static_cast<long long>(times));
}

// ==== Last Error ====
const char* __bindings_tests_getLastError(bindings_tests_isolatethread_t* isolate);
const char* bindings_tests_getLastError() {
    return __bindings_tests_getLastError(isolate::global());
}
const char* bindings_tests_secondary_getLastError() {
    return __bindings_tests_getLastError(isolate::secondary());
}
void bindings_tests_getLibraryVersion(int* major, int* minor, int* patch, int* build) {
    *major = 1;
    *minor = 2;
    *patch = 3;
    *build = 42;
}


#ifdef __cplusplus
}
#endif
