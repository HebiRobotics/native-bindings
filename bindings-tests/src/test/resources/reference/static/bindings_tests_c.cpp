#include "bindings_tests_c.h"
#include "graal_isolate.h"

namespace {
namespace isolate {

// Initial static creation. We attach the thread in the
// constructor, so we detach immediately to get an even
// number of operations.
graal_isolate_t* createIsolate() {
    graal_isolate_t* iso = nullptr;
    graal_isolatethread_t* thread = nullptr;
    if (graal_create_isolate(nullptr, &iso, &thread) != 0) {
        return (graal_isolate_t*) nullptr;
    }
    graal_detach_thread(thread);
    return iso;
}

// Thread-local attachment that lives for the lifetime of the thread
struct ThreadHandle {
    graal_isolatethread_t* ptr = nullptr;

    explicit ThreadHandle(graal_isolate_t* iso) {
        graal_attach_thread(iso, &ptr);
    }

    ~ThreadHandle() {
        if (ptr) graal_detach_thread(ptr);
    }
};

// Default global isolate
graal_isolatethread_t* global() {
    static graal_isolate_t* isolate = createIsolate();
    static thread_local ThreadHandle thread(isolate);
    return thread.ptr;
}

graal_isolatethread_t* secondary() {
     static graal_isolate_t* isolate = createIsolate();
     static thread_local ThreadHandle thread(isolate);
     return thread.ptr;
}

}
}

#ifdef __cplusplus
extern "C" {
#endif


// ==== Counter ====
bindings_tests_CounterPtr __bindings_tests_Counter_create(graal_isolatethread_t* isolate, int start);
bindings_tests_CounterPtr bindings_tests_Counter_create(int start) {
    return __bindings_tests_Counter_create(isolate::global(), start);
}
int __bindings_tests_Counter_getValue(graal_isolatethread_t* isolate, bindings_tests_CounterPtr obj);
int bindings_tests_Counter_getValue(bindings_tests_CounterPtr obj) {
    return __bindings_tests_Counter_getValue(isolate::global(), obj);
}
void __bindings_tests_Counter_setValue(graal_isolatethread_t* isolate, bindings_tests_CounterPtr obj, int value);
void bindings_tests_Counter_setValue(bindings_tests_CounterPtr obj, int value) {
    __bindings_tests_Counter_setValue(isolate::global(), obj, value);
}
void __bindings_tests_Counter_increment(graal_isolatethread_t* isolate, bindings_tests_CounterPtr obj);
void bindings_tests_Counter_increment(bindings_tests_CounterPtr obj) {
    __bindings_tests_Counter_increment(isolate::global(), obj);
}
void __bindings_tests_Counter_add(graal_isolatethread_t* isolate, bindings_tests_CounterPtr obj, int amount);
void bindings_tests_Counter_add(bindings_tests_CounterPtr obj, int amount) {
    __bindings_tests_Counter_add(isolate::global(), obj, amount);
}
int __bindings_tests_Counter_isClosed(graal_isolatethread_t* isolate, bindings_tests_CounterPtr obj);
int bindings_tests_Counter_isClosed(bindings_tests_CounterPtr obj) {
    return __bindings_tests_Counter_isClosed(isolate::global(), obj);
}
int __bindings_tests_Counter_getClosedCount(graal_isolatethread_t* isolate);
int bindings_tests_Counter_getClosedCount() {
    return __bindings_tests_Counter_getClosedCount(isolate::global());
}
void __bindings_tests_Counter_resetClosedCount(graal_isolatethread_t* isolate);
void bindings_tests_Counter_resetClosedCount() {
    __bindings_tests_Counter_resetClosedCount(isolate::global());
}
void __bindings_tests_Counter_release(graal_isolatethread_t* isolate, bindings_tests_CounterPtr obj);
void bindings_tests_Counter_release(bindings_tests_CounterPtr obj) {
    __bindings_tests_Counter_release(isolate::global(), obj);
}

// ==== Shape ====
const char* __bindings_tests_Shape_getName(graal_isolatethread_t* isolate, bindings_tests_ShapePtr obj);
const char* bindings_tests_Shape_getName(bindings_tests_ShapePtr obj) {
    return __bindings_tests_Shape_getName(isolate::global(), obj);
}
void __bindings_tests_Shape_setName(graal_isolatethread_t* isolate, bindings_tests_ShapePtr obj, const char* name);
void bindings_tests_Shape_setName(bindings_tests_ShapePtr obj, const char* name) {
    __bindings_tests_Shape_setName(isolate::global(), obj, name);
}
double __bindings_tests_Shape_area(graal_isolatethread_t* isolate, bindings_tests_ShapePtr obj);
double bindings_tests_Shape_area(bindings_tests_ShapePtr obj) {
    return __bindings_tests_Shape_area(isolate::global(), obj);
}
int __bindings_tests_Shape_sideCount(graal_isolatethread_t* isolate, bindings_tests_ShapePtr obj);
int bindings_tests_Shape_sideCount(bindings_tests_ShapePtr obj) {
    return __bindings_tests_Shape_sideCount(isolate::global(), obj);
}
void __bindings_tests_Shape_release(graal_isolatethread_t* isolate, bindings_tests_ShapePtr obj);
void bindings_tests_Shape_release(bindings_tests_ShapePtr obj) {
    __bindings_tests_Shape_release(isolate::global(), obj);
}

// ==== Circle ====
bindings_tests_CirclePtr __bindings_tests_Circle_create(graal_isolatethread_t* isolate, double radius);
bindings_tests_CirclePtr bindings_tests_Circle_create(double radius) {
    return __bindings_tests_Circle_create(isolate::global(), radius);
}
double __bindings_tests_Circle_getRadius(graal_isolatethread_t* isolate, bindings_tests_CirclePtr obj);
double bindings_tests_Circle_getRadius(bindings_tests_CirclePtr obj) {
    return __bindings_tests_Circle_getRadius(isolate::global(), obj);
}
double __bindings_tests_Circle_area(graal_isolatethread_t* isolate, bindings_tests_CirclePtr obj);
double bindings_tests_Circle_area(bindings_tests_CirclePtr obj) {
    return __bindings_tests_Circle_area(isolate::global(), obj);
}
int __bindings_tests_Circle_sideCount(graal_isolatethread_t* isolate, bindings_tests_CirclePtr obj);
int bindings_tests_Circle_sideCount(bindings_tests_CirclePtr obj) {
    return __bindings_tests_Circle_sideCount(isolate::global(), obj);
}
bindings_tests_ShapePtr bindings_tests_Circle_to_Shape(bindings_tests_CirclePtr ptr) {
  return reinterpret_cast<bindings_tests_ShapePtr>(ptr);
}

// ==== Square ====
bindings_tests_SquarePtr __bindings_tests_Square_create(graal_isolatethread_t* isolate, double side);
bindings_tests_SquarePtr bindings_tests_Square_create(double side) {
    return __bindings_tests_Square_create(isolate::global(), side);
}
double __bindings_tests_Square_getSide(graal_isolatethread_t* isolate, bindings_tests_SquarePtr obj);
double bindings_tests_Square_getSide(bindings_tests_SquarePtr obj) {
    return __bindings_tests_Square_getSide(isolate::global(), obj);
}
double __bindings_tests_Square_area(graal_isolatethread_t* isolate, bindings_tests_SquarePtr obj);
double bindings_tests_Square_area(bindings_tests_SquarePtr obj) {
    return __bindings_tests_Square_area(isolate::global(), obj);
}
int __bindings_tests_Square_sideCount(graal_isolatethread_t* isolate, bindings_tests_SquarePtr obj);
int bindings_tests_Square_sideCount(bindings_tests_SquarePtr obj) {
    return __bindings_tests_Square_sideCount(isolate::global(), obj);
}
bindings_tests_ShapePtr bindings_tests_Square_to_Shape(bindings_tests_SquarePtr ptr) {
  return reinterpret_cast<bindings_tests_ShapePtr>(ptr);
}

// ==== Arrays ====
double __bindings_tests_Arrays_sumFloat64(graal_isolatethread_t* isolate, double* values, long long count);
double bindings_tests_Arrays_sumFloat64(double* values, size_t count) {
    return __bindings_tests_Arrays_sumFloat64(isolate::global(), values, static_cast<long long>(count));
}
int __bindings_tests_Arrays_sumInt32(graal_isolatethread_t* isolate, int* values, long long count);
int bindings_tests_Arrays_sumInt32(int* values, size_t count) {
    return __bindings_tests_Arrays_sumInt32(isolate::global(), values, static_cast<long long>(count));
}
void __bindings_tests_Arrays_fillFloat64(graal_isolatethread_t* isolate, double* target, long long count, double value);
void bindings_tests_Arrays_fillFloat64(double* target, size_t count, double value) {
    __bindings_tests_Arrays_fillFloat64(isolate::global(), target, static_cast<long long>(count), value);
}
void __bindings_tests_Arrays_cumulativeSum(graal_isolatethread_t* isolate, double* source, double* target, long long count);
void bindings_tests_Arrays_cumulativeSum(double* source, double* target, size_t count) {
    __bindings_tests_Arrays_cumulativeSum(isolate::global(), source, target, static_cast<long long>(count));
}
void __bindings_tests_Arrays_reverseFloat64(graal_isolatethread_t* isolate, double* values, long long count);
void bindings_tests_Arrays_reverseFloat64(double* values, size_t count) {
    __bindings_tests_Arrays_reverseFloat64(isolate::global(), values, static_cast<long long>(count));
}
double __bindings_tests_Arrays_dotProduct(graal_isolatethread_t* isolate, const bindings_tests_double_span* x, const bindings_tests_double_span* y);
double bindings_tests_Arrays_dotProduct(bindings_tests_double_span x, bindings_tests_double_span y) {
    return __bindings_tests_Arrays_dotProduct(isolate::global(), &x, &y);
}
void __bindings_tests_Arrays_addOffset(graal_isolatethread_t* isolate, const bindings_tests_double_span* values, double offset, bindings_tests_double_span* spanOut);
bindings_tests_double_span bindings_tests_Arrays_addOffset(bindings_tests_double_span values, double offset) {
    bindings_tests_double_span result_;
    __bindings_tests_Arrays_addOffset(isolate::global(), &values, offset, &result_);
    return result_;
}
void __bindings_tests_Arrays_getSequence(graal_isolatethread_t* isolate, long long count, bindings_tests_double_span* spanOut);
bindings_tests_double_span bindings_tests_Arrays_getSequence(size_t count) {
    bindings_tests_double_span result_;
    __bindings_tests_Arrays_getSequence(isolate::global(), static_cast<long long>(count), &result_);
    return result_;
}

// ==== Callbacks ====
void __bindings_tests_Callbacks_invokeTimes(graal_isolatethread_t* isolate, IndexCallbackFunction func, void* userData, long long times);
void bindings_tests_Callbacks_invokeTimes(IndexCallbackFunction func, void* userData, size_t times) {
    __bindings_tests_Callbacks_invokeTimes(isolate::global(), func, userData, static_cast<long long>(times));
}
void __bindings_tests_Callbacks_invokeOnce(graal_isolatethread_t* isolate, IndexCallbackFunction func, void* userData, int index);
void bindings_tests_Callbacks_invokeOnce(IndexCallbackFunction func, void* userData, int index) {
    __bindings_tests_Callbacks_invokeOnce(isolate::global(), func, userData, index);
}

// ==== Errors ====
int __bindings_tests_Errors_requireNonNegative(graal_isolatethread_t* isolate, int value);
int bindings_tests_Errors_requireNonNegative(int value) {
    return __bindings_tests_Errors_requireNonNegative(isolate::global(), value);
}
int __bindings_tests_Errors_divideInt32(graal_isolatethread_t* isolate, int a, int b);
int bindings_tests_Errors_divideInt32(int a, int b) {
    return __bindings_tests_Errors_divideInt32(isolate::global(), a, b);
}
double __bindings_tests_Errors_failingFloat64(graal_isolatethread_t* isolate, int shouldFail);
double bindings_tests_Errors_failingFloat64(int shouldFail) {
    return __bindings_tests_Errors_failingFloat64(isolate::global(), shouldFail);
}
int __bindings_tests_Errors_failingBool(graal_isolatethread_t* isolate, int shouldFail);
int bindings_tests_Errors_failingBool(int shouldFail) {
    return __bindings_tests_Errors_failingBool(isolate::global(), shouldFail);
}
const char* __bindings_tests_Errors_failingString(graal_isolatethread_t* isolate, int shouldFail);
const char* bindings_tests_Errors_failingString(int shouldFail) {
    return __bindings_tests_Errors_failingString(isolate::global(), shouldFail);
}
void __bindings_tests_Errors_neverThrows(graal_isolatethread_t* isolate);
void bindings_tests_Errors_neverThrows() {
    __bindings_tests_Errors_neverThrows(isolate::global());
}
void __bindings_tests_Errors_clearLastError(graal_isolatethread_t* isolate);
void bindings_tests_Errors_clearLastError() {
    __bindings_tests_Errors_clearLastError(isolate::global());
}
const char* __bindings_tests_Errors_getLastError(graal_isolatethread_t* isolate);
const char* bindings_tests_Errors_getLastError() {
    return __bindings_tests_Errors_getLastError(isolate::global());
}

// ==== Isolated ====
void __bindings_tests_Isolated_increment(graal_isolatethread_t* isolate);
void bindings_tests_Isolated_increment() {
    __bindings_tests_Isolated_increment(isolate::secondary());
}
int __bindings_tests_Isolated_getCounter(graal_isolatethread_t* isolate);
int bindings_tests_Isolated_getCounter() {
    return __bindings_tests_Isolated_getCounter(isolate::secondary());
}
void __bindings_tests_Isolated_reset(graal_isolatethread_t* isolate);
void bindings_tests_Isolated_reset() {
    __bindings_tests_Isolated_reset(isolate::secondary());
}
int __bindings_tests_Isolated_alwaysFails(graal_isolatethread_t* isolate);
int bindings_tests_Isolated_alwaysFails() {
    return __bindings_tests_Isolated_alwaysFails(isolate::secondary());
}
const char* __bindings_tests_Isolated_getLastError(graal_isolatethread_t* isolate);
const char* bindings_tests_Isolated_getLastError() {
    return __bindings_tests_Isolated_getLastError(isolate::secondary());
}

// ==== Math ====
int __bindings_tests_Math_negate(graal_isolatethread_t* isolate, int value);
int bindings_tests_Math_negate(int value) {
    return __bindings_tests_Math_negate(isolate::global(), value);
}
unsigned short __bindings_tests_Math_nextChar(graal_isolatethread_t* isolate, unsigned short value);
unsigned short bindings_tests_Math_nextChar(unsigned short value) {
    return __bindings_tests_Math_nextChar(isolate::global(), value);
}
char __bindings_tests_Math_addInt8(graal_isolatethread_t* isolate, char a, char b);
char bindings_tests_Math_addInt8(char a, char b) {
    return __bindings_tests_Math_addInt8(isolate::global(), a, b);
}
short __bindings_tests_Math_addInt16(graal_isolatethread_t* isolate, short a, short b);
short bindings_tests_Math_addInt16(short a, short b) {
    return __bindings_tests_Math_addInt16(isolate::global(), a, b);
}
int __bindings_tests_Math_addInt32(graal_isolatethread_t* isolate, int a, int b);
int bindings_tests_Math_addInt32(int a, int b) {
    return __bindings_tests_Math_addInt32(isolate::global(), a, b);
}
long long __bindings_tests_Math_addInt64(graal_isolatethread_t* isolate, long long a, long long b);
long long bindings_tests_Math_addInt64(long long a, long long b) {
    return __bindings_tests_Math_addInt64(isolate::global(), a, b);
}
float __bindings_tests_Math_addFloat32(graal_isolatethread_t* isolate, float a, float b);
float bindings_tests_Math_addFloat32(float a, float b) {
    return __bindings_tests_Math_addFloat32(isolate::global(), a, b);
}
double __bindings_tests_Math_addFloat64(graal_isolatethread_t* isolate, double a, double b);
double bindings_tests_Math_addFloat64(double a, double b) {
    return __bindings_tests_Math_addFloat64(isolate::global(), a, b);
}
long long __bindings_tests_Math_addSize(graal_isolatethread_t* isolate, long long a, long long b);
size_t bindings_tests_Math_addSize(size_t a, size_t b) {
    return __bindings_tests_Math_addSize(isolate::global(), static_cast<long long>(a), static_cast<long long>(b));
}
int __bindings_tests_Math_sizeToInt32(graal_isolatethread_t* isolate, long long value);
int bindings_tests_Math_sizeToInt32(size_t value) {
    return __bindings_tests_Math_sizeToInt32(isolate::global(), static_cast<long long>(value));
}
int __bindings_tests_Math_maxInt32(graal_isolatethread_t* isolate);
int bindings_tests_Math_maxInt32() {
    return __bindings_tests_Math_maxInt32(isolate::global());
}
long long __bindings_tests_Math_maxSize(graal_isolatethread_t* isolate);
size_t bindings_tests_Math_maxSize() {
    return __bindings_tests_Math_maxSize(isolate::global());
}

// ==== Overloads ====
int __bindings_tests_Overloads_scale(graal_isolatethread_t* isolate, int value, int factor);
int bindings_tests_Overloads_scale(int value, int factor) {
    return __bindings_tests_Overloads_scale(isolate::global(), value, factor);
}
const char* __bindings_tests_Overloads_greet(graal_isolatethread_t* isolate, const char* name, const char* greeting);
const char* bindings_tests_Overloads_greet(const char* name, const char* greeting) {
    return __bindings_tests_Overloads_greet(isolate::global(), name, greeting);
}
int __bindings_tests_Overloads_total(graal_isolatethread_t* isolate, int sum, int factor);
int bindings_tests_Overloads_total(int sum, int factor) {
    return __bindings_tests_Overloads_total(isolate::global(), sum, factor);
}
int __bindings_tests_Overloads_answer(graal_isolatethread_t* isolate, int multiplier);
int bindings_tests_Overloads_answer(int multiplier) {
    return __bindings_tests_Overloads_answer(isolate::global(), multiplier);
}
int __bindings_tests_Overloads_renamed(graal_isolatethread_t* isolate);
int bindings_tests_Overloads_renamed() {
    return __bindings_tests_Overloads_renamed(isolate::global());
}

// ==== Palette ====
bindings_tests_Color __bindings_tests_Palette_next(graal_isolatethread_t* isolate, bindings_tests_Color color);
bindings_tests_Color bindings_tests_Palette_next(bindings_tests_Color color) {
    return __bindings_tests_Palette_next(isolate::global(), color);
}
const char* __bindings_tests_Palette_nameOf(graal_isolatethread_t* isolate, bindings_tests_Color color);
const char* bindings_tests_Palette_nameOf(bindings_tests_Color color) {
    return __bindings_tests_Palette_nameOf(isolate::global(), color);
}
int __bindings_tests_Palette_ordinalOf(graal_isolatethread_t* isolate, bindings_tests_Color color);
int bindings_tests_Palette_ordinalOf(bindings_tests_Color color) {
    return __bindings_tests_Palette_ordinalOf(isolate::global(), color);
}
bindings_tests_Color __bindings_tests_Palette_fromOrdinal(graal_isolatethread_t* isolate, int ordinal);
bindings_tests_Color bindings_tests_Palette_fromOrdinal(int ordinal) {
    return __bindings_tests_Palette_fromOrdinal(isolate::global(), ordinal);
}

// ==== Strings ====
const char* __bindings_tests_Strings_echo(graal_isolatethread_t* isolate, const char* value);
const char* bindings_tests_Strings_echo(const char* value) {
    return __bindings_tests_Strings_echo(isolate::global(), value);
}
const char* __bindings_tests_Strings_concat(graal_isolatethread_t* isolate, const char* a, const char* b);
const char* bindings_tests_Strings_concat(const char* a, const char* b) {
    return __bindings_tests_Strings_concat(isolate::global(), a, b);
}
long long __bindings_tests_Strings_utf8Length(graal_isolatethread_t* isolate, const char* value);
size_t bindings_tests_Strings_utf8Length(const char* value) {
    return __bindings_tests_Strings_utf8Length(isolate::global(), value);
}
long long __bindings_tests_Strings_utf16Length(graal_isolatethread_t* isolate, const char* value);
size_t bindings_tests_Strings_utf16Length(const char* value) {
    return __bindings_tests_Strings_utf16Length(isolate::global(), value);
}
const char* __bindings_tests_Strings_empty(graal_isolatethread_t* isolate);
const char* bindings_tests_Strings_empty() {
    return __bindings_tests_Strings_empty(isolate::global());
}
const char* __bindings_tests_Strings_nullString(graal_isolatethread_t* isolate);
const char* bindings_tests_Strings_nullString() {
    return __bindings_tests_Strings_nullString(isolate::global());
}
const char* __bindings_tests_Strings_join(graal_isolatethread_t* isolate, char** values, long long count, const char* separator);
const char* bindings_tests_Strings_join(char** values, size_t count, const char* separator) {
    return __bindings_tests_Strings_join(isolate::global(), values, static_cast<long long>(count), separator);
}
const char* __bindings_tests_Strings_repeat(graal_isolatethread_t* isolate, const char* value, long long times);
const char* bindings_tests_Strings_repeat(const char* value, size_t times) {
    return __bindings_tests_Strings_repeat(isolate::global(), value, static_cast<long long>(times));
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
