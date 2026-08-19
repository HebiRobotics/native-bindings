#include "bindings_tests_c.h"

// Aliases for the built-in GraalVM isolate API to avoid naming conflicts
extern "C" {
typedef struct bindings_tests_Isolate_s bindings_tests_Isolate;
typedef struct bindings_tests_IsolateThread_s bindings_tests_IsolateThread;

bindings_tests_IsolateThread* bindings_tests_createIsolate();
bindings_tests_Isolate* bindings_tests_getIsolate(bindings_tests_IsolateThread* thread);
bindings_tests_IsolateThread* bindings_tests_attachThread(bindings_tests_Isolate* isolate);
bindings_tests_IsolateThread* bindings_tests_getCurrentThread(bindings_tests_Isolate* isolate);
int bindings_tests_detachThread(bindings_tests_IsolateThread* thread);
int bindings_tests_tearDownIsolate(bindings_tests_IsolateThread* thread);
}

namespace {
namespace isolate {

// Initial static creation. We attach the thread in the
// constructor, so we detach immediately to get an even
// number of operations.
bindings_tests_Isolate* createIsolate() {
    bindings_tests_IsolateThread* thread = bindings_tests_createIsolate();
    if (!thread) {
        return (bindings_tests_Isolate*) nullptr;
    }
    bindings_tests_Isolate* iso = bindings_tests_getIsolate(thread);
    bindings_tests_detachThread(thread);
    return iso;
}

// Thread-local attachment that lives for the lifetime of the thread
struct ThreadHandle {
    bindings_tests_IsolateThread* ptr = nullptr;

    explicit ThreadHandle(bindings_tests_Isolate* iso) {
        if (iso) ptr = bindings_tests_attachThread(iso);
    }

    ~ThreadHandle() {
        if (ptr) bindings_tests_detachThread(ptr);
    }
};

// Default global isolate
bindings_tests_IsolateThread* global() {
    static bindings_tests_Isolate* isolate = createIsolate();
    static thread_local ThreadHandle thread(isolate);
    return thread.ptr;
}

bindings_tests_IsolateThread* secondary() {
    static bindings_tests_Isolate* isolate = createIsolate();
    static thread_local ThreadHandle thread(isolate);
    return thread.ptr;
}

}
}

#ifdef __cplusplus
extern "C" {
#endif


// ==== Counter ====
bindings_tests_CounterPtr __bindings_tests_Counter_create(bindings_tests_IsolateThread* isolate, bindings_tests_ErrorInfo* _error, int start);
bindings_tests_CounterPtr bindings_tests_Counter_create(bindings_tests_ErrorInfo* _error, int start) {
    return __bindings_tests_Counter_create(isolate::global(), _error, start);
}
bindings_tests_CounterPtr __bindings_tests_Counter_parse(bindings_tests_IsolateThread* isolate, bindings_tests_ErrorInfo* _error, const char* text);
bindings_tests_CounterPtr bindings_tests_Counter_parse(bindings_tests_ErrorInfo* _error, const char* text) {
    return __bindings_tests_Counter_parse(isolate::global(), _error, text);
}
int __bindings_tests_Counter_getValue(bindings_tests_IsolateThread* isolate, bindings_tests_CounterPtr obj);
int bindings_tests_Counter_getValue(bindings_tests_CounterPtr obj) {
    return __bindings_tests_Counter_getValue(isolate::global(), obj);
}
void __bindings_tests_Counter_setValue(bindings_tests_IsolateThread* isolate, bindings_tests_CounterPtr obj, int value);
void bindings_tests_Counter_setValue(bindings_tests_CounterPtr obj, int value) {
    __bindings_tests_Counter_setValue(isolate::global(), obj, value);
}
void __bindings_tests_Counter_increment(bindings_tests_IsolateThread* isolate, bindings_tests_CounterPtr obj);
void bindings_tests_Counter_increment(bindings_tests_CounterPtr obj) {
    __bindings_tests_Counter_increment(isolate::global(), obj);
}
void __bindings_tests_Counter_add(bindings_tests_IsolateThread* isolate, bindings_tests_CounterPtr obj, int amount);
void bindings_tests_Counter_add(bindings_tests_CounterPtr obj, int amount) {
    __bindings_tests_Counter_add(isolate::global(), obj, amount);
}
int __bindings_tests_Counter_isClosed(bindings_tests_IsolateThread* isolate, bindings_tests_CounterPtr obj);
int bindings_tests_Counter_isClosed(bindings_tests_CounterPtr obj) {
    return __bindings_tests_Counter_isClosed(isolate::global(), obj);
}
bindings_tests_CounterPtr __bindings_tests_Counter_copy(bindings_tests_IsolateThread* isolate, bindings_tests_CounterPtr obj);
bindings_tests_CounterPtr bindings_tests_Counter_copy(bindings_tests_CounterPtr obj) {
    return __bindings_tests_Counter_copy(isolate::global(), obj);
}
int __bindings_tests_Counter_getClosedCount(bindings_tests_IsolateThread* isolate);
int bindings_tests_Counter_getClosedCount() {
    return __bindings_tests_Counter_getClosedCount(isolate::global());
}
void __bindings_tests_Counter_resetClosedCount(bindings_tests_IsolateThread* isolate);
void bindings_tests_Counter_resetClosedCount() {
    __bindings_tests_Counter_resetClosedCount(isolate::global());
}
void __bindings_tests_Counter_release(bindings_tests_IsolateThread* isolate, bindings_tests_CounterPtr obj);
void bindings_tests_Counter_release(bindings_tests_CounterPtr obj) {
    __bindings_tests_Counter_release(isolate::global(), obj);
}

// ==== Naming ====
custom_prefix_NamingPtr __custom_prefix_Naming_create(bindings_tests_IsolateThread* isolate, bindings_tests_ErrorInfo* _error, int startValue);
custom_prefix_NamingPtr custom_prefix_Naming_create(bindings_tests_ErrorInfo* _error, int startValue) {
    return __custom_prefix_Naming_create(isolate::global(), _error, startValue);
}
int __custom_prefix_Naming_get_start_value(bindings_tests_IsolateThread* isolate, custom_prefix_NamingPtr obj);
int custom_prefix_Naming_get_start_value(custom_prefix_NamingPtr obj) {
    return __custom_prefix_Naming_get_start_value(isolate::global(), obj);
}
int __custom_prefix_Naming_add_two_numbers(bindings_tests_IsolateThread* isolate, int first, int second);
int custom_prefix_Naming_add_two_numbers(int first, int second) {
    return __custom_prefix_Naming_add_two_numbers(isolate::global(), first, second);
}
void __custom_prefix_Naming_release(bindings_tests_IsolateThread* isolate, custom_prefix_NamingPtr obj);
void custom_prefix_Naming_release(custom_prefix_NamingPtr obj) {
    __custom_prefix_Naming_release(isolate::global(), obj);
}

// ==== Shape ====
bindings_tests_ShapePtr __bindings_tests_Shape_create(bindings_tests_IsolateThread* isolate, bindings_tests_ErrorInfo* _error, const char* name);
bindings_tests_ShapePtr bindings_tests_Shape_create(bindings_tests_ErrorInfo* _error, const char* name) {
    return __bindings_tests_Shape_create(isolate::global(), _error, name);
}
const char* __bindings_tests_Shape_getName(bindings_tests_IsolateThread* isolate, bindings_tests_ShapePtr obj);
const char* bindings_tests_Shape_getName(bindings_tests_ShapePtr obj) {
    return __bindings_tests_Shape_getName(isolate::global(), obj);
}
void __bindings_tests_Shape_setName(bindings_tests_IsolateThread* isolate, bindings_tests_ShapePtr obj, const char* name);
void bindings_tests_Shape_setName(bindings_tests_ShapePtr obj, const char* name) {
    __bindings_tests_Shape_setName(isolate::global(), obj, name);
}
bindings_tests_ShapePtr __bindings_tests_Shape_plainCopy(bindings_tests_IsolateThread* isolate, bindings_tests_ShapePtr obj);
bindings_tests_ShapePtr bindings_tests_Shape_plainCopy(bindings_tests_ShapePtr obj) {
    return __bindings_tests_Shape_plainCopy(isolate::global(), obj);
}
double __bindings_tests_Shape_area(bindings_tests_IsolateThread* isolate, bindings_tests_ShapePtr obj);
double bindings_tests_Shape_area(bindings_tests_ShapePtr obj) {
    return __bindings_tests_Shape_area(isolate::global(), obj);
}
int __bindings_tests_Shape_sideCount(bindings_tests_IsolateThread* isolate, bindings_tests_ShapePtr obj);
int bindings_tests_Shape_sideCount(bindings_tests_ShapePtr obj) {
    return __bindings_tests_Shape_sideCount(isolate::global(), obj);
}
void __bindings_tests_Shape_release(bindings_tests_IsolateThread* isolate, bindings_tests_ShapePtr obj);
void bindings_tests_Shape_release(bindings_tests_ShapePtr obj) {
    __bindings_tests_Shape_release(isolate::global(), obj);
}

// ==== Circle ====
bindings_tests_CirclePtr __bindings_tests_Circle_create(bindings_tests_IsolateThread* isolate, bindings_tests_ErrorInfo* _error, double radius);
bindings_tests_CirclePtr bindings_tests_Circle_create(bindings_tests_ErrorInfo* _error, double radius) {
    return __bindings_tests_Circle_create(isolate::global(), _error, radius);
}
double __bindings_tests_Circle_getRadius(bindings_tests_IsolateThread* isolate, bindings_tests_CirclePtr obj);
double bindings_tests_Circle_getRadius(bindings_tests_CirclePtr obj) {
    return __bindings_tests_Circle_getRadius(isolate::global(), obj);
}
bindings_tests_CircleColor __bindings_tests_Circle_getColor(bindings_tests_IsolateThread* isolate, bindings_tests_CirclePtr obj);
bindings_tests_CircleColor bindings_tests_Circle_getColor(bindings_tests_CirclePtr obj) {
    return __bindings_tests_Circle_getColor(isolate::global(), obj);
}
double __bindings_tests_Circle_area(bindings_tests_IsolateThread* isolate, bindings_tests_CirclePtr obj);
double bindings_tests_Circle_area(bindings_tests_CirclePtr obj) {
    return __bindings_tests_Circle_area(isolate::global(), obj);
}
int __bindings_tests_Circle_sideCount(bindings_tests_IsolateThread* isolate, bindings_tests_CirclePtr obj);
int bindings_tests_Circle_sideCount(bindings_tests_CirclePtr obj) {
    return __bindings_tests_Circle_sideCount(isolate::global(), obj);
}
bindings_tests_ShapePtr bindings_tests_Circle_to_Shape(bindings_tests_CirclePtr ptr) {
    return reinterpret_cast<bindings_tests_ShapePtr>(ptr);
}

// ==== Square ====
bindings_tests_SquarePtr __bindings_tests_Square_create(bindings_tests_IsolateThread* isolate, bindings_tests_ErrorInfo* _error, double side);
bindings_tests_SquarePtr bindings_tests_Square_create(bindings_tests_ErrorInfo* _error, double side) {
    return __bindings_tests_Square_create(isolate::global(), _error, side);
}
double __bindings_tests_Square_getSide(bindings_tests_IsolateThread* isolate, bindings_tests_SquarePtr obj);
double bindings_tests_Square_getSide(bindings_tests_SquarePtr obj) {
    return __bindings_tests_Square_getSide(isolate::global(), obj);
}
double __bindings_tests_Square_area(bindings_tests_IsolateThread* isolate, bindings_tests_SquarePtr obj);
double bindings_tests_Square_area(bindings_tests_SquarePtr obj) {
    return __bindings_tests_Square_area(isolate::global(), obj);
}
int __bindings_tests_Square_sideCount(bindings_tests_IsolateThread* isolate, bindings_tests_SquarePtr obj);
int bindings_tests_Square_sideCount(bindings_tests_SquarePtr obj) {
    return __bindings_tests_Square_sideCount(isolate::global(), obj);
}
bindings_tests_ShapePtr bindings_tests_Square_to_Shape(bindings_tests_SquarePtr ptr) {
    return reinterpret_cast<bindings_tests_ShapePtr>(ptr);
}

// ==== Arrays ====
double __bindings_tests_Arrays_sumFloat64(bindings_tests_IsolateThread* isolate, const double* values, long long count);
double bindings_tests_Arrays_sumFloat64(const double* values, size_t count) {
    return __bindings_tests_Arrays_sumFloat64(isolate::global(), values, static_cast<long long>(count));
}
int __bindings_tests_Arrays_sumInt32(bindings_tests_IsolateThread* isolate, const int* values, long long count);
int bindings_tests_Arrays_sumInt32(const int* values, size_t count) {
    return __bindings_tests_Arrays_sumInt32(isolate::global(), values, static_cast<long long>(count));
}
void __bindings_tests_Arrays_fillFloat64(bindings_tests_IsolateThread* isolate, double* target, long long count, double value);
void bindings_tests_Arrays_fillFloat64(double* target, size_t count, double value) {
    __bindings_tests_Arrays_fillFloat64(isolate::global(), target, static_cast<long long>(count), value);
}
void __bindings_tests_Arrays_cumulativeSum(bindings_tests_IsolateThread* isolate, const double* source, double* target, long long count);
void bindings_tests_Arrays_cumulativeSum(const double* source, double* target, size_t count) {
    __bindings_tests_Arrays_cumulativeSum(isolate::global(), source, target, static_cast<long long>(count));
}
void __bindings_tests_Arrays_reverseFloat64(bindings_tests_IsolateThread* isolate, double* values, long long count);
void bindings_tests_Arrays_reverseFloat64(double* values, size_t count) {
    __bindings_tests_Arrays_reverseFloat64(isolate::global(), values, static_cast<long long>(count));
}
double __bindings_tests_Arrays_dotProduct(bindings_tests_IsolateThread* isolate, bindings_tests_ErrorInfo* _error, const bindings_tests_DoubleSpan* x, const bindings_tests_DoubleSpan* y);
double bindings_tests_Arrays_dotProduct(bindings_tests_ErrorInfo* _error, bindings_tests_DoubleSpan x, bindings_tests_DoubleSpan y) {
    return __bindings_tests_Arrays_dotProduct(isolate::global(), _error, &x, &y);
}
void __bindings_tests_Arrays_addOffset(bindings_tests_IsolateThread* isolate, const bindings_tests_DoubleSpan* values, double offset, bindings_tests_DoubleSpan* structOut);
bindings_tests_DoubleSpan bindings_tests_Arrays_addOffset(bindings_tests_DoubleSpan values, double offset) {
    bindings_tests_DoubleSpan result_;
    __bindings_tests_Arrays_addOffset(isolate::global(), &values, offset, &result_);
    return result_;
}
void __bindings_tests_Arrays_addOffsetInt32(bindings_tests_IsolateThread* isolate, const bindings_tests_IntSpan* values, int offset, bindings_tests_IntSpan* structOut);
bindings_tests_IntSpan bindings_tests_Arrays_addOffsetInt32(bindings_tests_IntSpan values, int offset) {
    bindings_tests_IntSpan result_;
    __bindings_tests_Arrays_addOffsetInt32(isolate::global(), &values, offset, &result_);
    return result_;
}
void __bindings_tests_Arrays_addOffsetFloat32(bindings_tests_IsolateThread* isolate, const bindings_tests_FloatSpan* values, float offset, bindings_tests_FloatSpan* structOut);
bindings_tests_FloatSpan bindings_tests_Arrays_addOffsetFloat32(bindings_tests_FloatSpan values, float offset) {
    bindings_tests_FloatSpan result_;
    __bindings_tests_Arrays_addOffsetFloat32(isolate::global(), &values, offset, &result_);
    return result_;
}
int __bindings_tests_Arrays_sumUint8(bindings_tests_IsolateThread* isolate, char* values, long long count);
int bindings_tests_Arrays_sumUint8(uint8_t* values, size_t count) {
    return __bindings_tests_Arrays_sumUint8(isolate::global(), reinterpret_cast<char*>(values), static_cast<long long>(count));
}
void __bindings_tests_Arrays_addOffsetUint8(bindings_tests_IsolateThread* isolate, const bindings_tests_ByteSpan* values, char offset, bindings_tests_ByteSpan* structOut);
bindings_tests_ByteSpan bindings_tests_Arrays_addOffsetUint8(bindings_tests_ByteSpan values, char offset) {
    bindings_tests_ByteSpan result_;
    __bindings_tests_Arrays_addOffsetUint8(isolate::global(), &values, offset, &result_);
    return result_;
}
void __bindings_tests_Arrays_getSequence(bindings_tests_IsolateThread* isolate, long long count, bindings_tests_DoubleSpan* structOut);
bindings_tests_DoubleSpan bindings_tests_Arrays_getSequence(size_t count) {
    bindings_tests_DoubleSpan result_;
    __bindings_tests_Arrays_getSequence(isolate::global(), static_cast<long long>(count), &result_);
    return result_;
}

// ==== Callbacks ====
double __bindings_tests_Callbacks_invokeDoubleCallback(bindings_tests_IsolateThread* isolate, bindings_tests_DoubleCallbackFunc func, double a, double b);
double bindings_tests_Callbacks_invokeDoubleCallback(bindings_tests_DoubleCallbackFunc func, double a, double b) {
    return __bindings_tests_Callbacks_invokeDoubleCallback(isolate::global(), func, a, b);
}
double __bindings_tests_Callbacks_invokeDoubleSpanToScalarCallback(bindings_tests_IsolateThread* isolate, bindings_tests_DoubleSpanToScalarCallbackFunc func, const bindings_tests_DoubleSpan* a, const bindings_tests_DoubleSpan* b);
double bindings_tests_Callbacks_invokeDoubleSpanToScalarCallback(bindings_tests_DoubleSpanToScalarCallbackFunc func, bindings_tests_DoubleSpan a, bindings_tests_DoubleSpan b) {
    return __bindings_tests_Callbacks_invokeDoubleSpanToScalarCallback(isolate::global(), func, &a, &b);
}
double __bindings_tests_Callbacks_invokeProgressCallback(bindings_tests_IsolateThread* isolate, bindings_tests_ProgressCallbackFunc func, const bindings_tests_Progress* progress);
double bindings_tests_Callbacks_invokeProgressCallback(bindings_tests_ProgressCallbackFunc func, bindings_tests_Progress progress) {
    return __bindings_tests_Callbacks_invokeProgressCallback(isolate::global(), func, &progress);
}
void __bindings_tests_Callbacks_invokeTimes(bindings_tests_IsolateThread* isolate, bindings_tests_IndexCallbackFunction func, void* userData, long long times);
void bindings_tests_Callbacks_invokeTimes(bindings_tests_IndexCallbackFunction func, void* userData, size_t times) {
    __bindings_tests_Callbacks_invokeTimes(isolate::global(), func, userData, static_cast<long long>(times));
}
void __bindings_tests_Callbacks_transformOnce(bindings_tests_IsolateThread* isolate, bindings_tests_TransformCallbackFunction func, void* userData, const double* input, double* output);
void bindings_tests_Callbacks_transformOnce(bindings_tests_TransformCallbackFunction func, void* userData, const double* input, double* output) {
    __bindings_tests_Callbacks_transformOnce(isolate::global(), func, userData, input, output);
}
void __bindings_tests_Callbacks_invokePlainTimes(bindings_tests_IsolateThread* isolate, bindings_tests_PlainIndexCallbackFunction func, long long times);
void bindings_tests_Callbacks_invokePlainTimes(bindings_tests_PlainIndexCallbackFunction func, size_t times) {
    __bindings_tests_Callbacks_invokePlainTimes(isolate::global(), func, static_cast<long long>(times));
}
void __bindings_tests_Callbacks_invokePlainChecked(bindings_tests_IsolateThread* isolate, bindings_tests_ErrorInfo* _error, bindings_tests_PlainIndexCallbackFunction func, int index);
void bindings_tests_Callbacks_invokePlainChecked(bindings_tests_ErrorInfo* _error, bindings_tests_PlainIndexCallbackFunction func, int index) {
    __bindings_tests_Callbacks_invokePlainChecked(isolate::global(), _error, func, index);
}
void __bindings_tests_Callbacks_invokePlainOnWorkerThread(bindings_tests_IsolateThread* isolate, bindings_tests_PlainIndexCallbackFunction func, int index);
void bindings_tests_Callbacks_invokePlainOnWorkerThread(bindings_tests_PlainIndexCallbackFunction func, int index) {
    __bindings_tests_Callbacks_invokePlainOnWorkerThread(isolate::global(), func, index);
}
void __bindings_tests_Callbacks_notifyPlainOnce(bindings_tests_IsolateThread* isolate, bindings_tests_PlainNotifyCallbackFunction func);
void bindings_tests_Callbacks_notifyPlainOnce(bindings_tests_PlainNotifyCallbackFunction func) {
    __bindings_tests_Callbacks_notifyPlainOnce(isolate::global(), func);
}

// ==== Errors ====
void __bindings_tests_Errors_requireNonNegative(bindings_tests_IsolateThread* isolate, bindings_tests_ErrorInfo* _error, int value);
void bindings_tests_Errors_requireNonNegative(bindings_tests_ErrorInfo* _error, int value) {
    __bindings_tests_Errors_requireNonNegative(isolate::global(), _error, value);
}
int __bindings_tests_Errors_divideInt32(bindings_tests_IsolateThread* isolate, bindings_tests_ErrorInfo* _error, int a, int b);
int bindings_tests_Errors_divideInt32(bindings_tests_ErrorInfo* _error, int a, int b) {
    return __bindings_tests_Errors_divideInt32(isolate::global(), _error, a, b);
}
double __bindings_tests_Errors_failingFloat64(bindings_tests_IsolateThread* isolate, bindings_tests_ErrorInfo* _error, int shouldFail);
double bindings_tests_Errors_failingFloat64(bindings_tests_ErrorInfo* _error, int shouldFail) {
    return __bindings_tests_Errors_failingFloat64(isolate::global(), _error, shouldFail);
}
int __bindings_tests_Errors_failingBool(bindings_tests_IsolateThread* isolate, bindings_tests_ErrorInfo* _error, int shouldFail);
int bindings_tests_Errors_failingBool(bindings_tests_ErrorInfo* _error, int shouldFail) {
    return __bindings_tests_Errors_failingBool(isolate::global(), _error, shouldFail);
}
const char* __bindings_tests_Errors_failingString(bindings_tests_IsolateThread* isolate, bindings_tests_ErrorInfo* _error, int shouldFail);
const char* bindings_tests_Errors_failingString(bindings_tests_ErrorInfo* _error, int shouldFail) {
    return __bindings_tests_Errors_failingString(isolate::global(), _error, shouldFail);
}
int __bindings_tests_Errors_failWithoutMessage(bindings_tests_IsolateThread* isolate, bindings_tests_ErrorInfo* _error);
int bindings_tests_Errors_failWithoutMessage(bindings_tests_ErrorInfo* _error) {
    return __bindings_tests_Errors_failWithoutMessage(isolate::global(), _error);
}
void __bindings_tests_Errors_neverThrows(bindings_tests_IsolateThread* isolate);
void bindings_tests_Errors_neverThrows() {
    __bindings_tests_Errors_neverThrows(isolate::global());
}

// ==== Indices ====
int __bindings_tests_Indices_echo(bindings_tests_IsolateThread* isolate, int index);
int bindings_tests_Indices_echo(int index) {
    return __bindings_tests_Indices_echo(isolate::global(), index);
}
int __bindings_tests_Indices_countUpTo(bindings_tests_IsolateThread* isolate, int index);
int bindings_tests_Indices_countUpTo(int index) {
    return __bindings_tests_Indices_countUpTo(isolate::global(), index);
}
int __bindings_tests_Indices_lastIndexOf(bindings_tests_IsolateThread* isolate, int count);
int bindings_tests_Indices_lastIndexOf(int count) {
    return __bindings_tests_Indices_lastIndexOf(isolate::global(), count);
}
int __bindings_tests_Indices_offsetFrom(bindings_tests_IsolateThread* isolate, int row, int col);
int bindings_tests_Indices_offsetFrom(int row, int col) {
    return __bindings_tests_Indices_offsetFrom(isolate::global(), row, col);
}
int __bindings_tests_Indices_fromRow(bindings_tests_IsolateThread* isolate, int row);
int bindings_tests_Indices_fromRow(int row) {
    return __bindings_tests_Indices_fromRow(isolate::global(), row);
}
long long __bindings_tests_Indices_echoSize(bindings_tests_IsolateThread* isolate, long long index);
size_t bindings_tests_Indices_echoSize(size_t index) {
    return __bindings_tests_Indices_echoSize(isolate::global(), static_cast<long long>(index));
}

// ==== Isolated ====
void __bindings_tests_Isolated_increment(bindings_tests_IsolateThread* isolate);
void bindings_tests_Isolated_increment() {
    __bindings_tests_Isolated_increment(isolate::secondary());
}
int __bindings_tests_Isolated_getCounter(bindings_tests_IsolateThread* isolate);
int bindings_tests_Isolated_getCounter() {
    return __bindings_tests_Isolated_getCounter(isolate::secondary());
}
void __bindings_tests_Isolated_reset(bindings_tests_IsolateThread* isolate);
void bindings_tests_Isolated_reset() {
    __bindings_tests_Isolated_reset(isolate::secondary());
}
void __bindings_tests_Isolated_alwaysFails(bindings_tests_IsolateThread* isolate, bindings_tests_ErrorInfo* _error);
void bindings_tests_Isolated_alwaysFails(bindings_tests_ErrorInfo* _error) {
    __bindings_tests_Isolated_alwaysFails(isolate::secondary(), _error);
}

// ==== Math ====
int __bindings_tests_Math_negate(bindings_tests_IsolateThread* isolate, int value);
int bindings_tests_Math_negate(int value) {
    return __bindings_tests_Math_negate(isolate::global(), value);
}
unsigned short __bindings_tests_Math_nextChar(bindings_tests_IsolateThread* isolate, unsigned short value);
unsigned short bindings_tests_Math_nextChar(unsigned short value) {
    return __bindings_tests_Math_nextChar(isolate::global(), value);
}
char __bindings_tests_Math_addInt8(bindings_tests_IsolateThread* isolate, char a, char b);
char bindings_tests_Math_addInt8(char a, char b) {
    return __bindings_tests_Math_addInt8(isolate::global(), a, b);
}
short __bindings_tests_Math_addInt16(bindings_tests_IsolateThread* isolate, short a, short b);
short bindings_tests_Math_addInt16(short a, short b) {
    return __bindings_tests_Math_addInt16(isolate::global(), a, b);
}
int __bindings_tests_Math_addInt32(bindings_tests_IsolateThread* isolate, int a, int b);
int bindings_tests_Math_addInt32(int a, int b) {
    return __bindings_tests_Math_addInt32(isolate::global(), a, b);
}
long long __bindings_tests_Math_addInt64(bindings_tests_IsolateThread* isolate, long long a, long long b);
long long bindings_tests_Math_addInt64(long long a, long long b) {
    return __bindings_tests_Math_addInt64(isolate::global(), a, b);
}
float __bindings_tests_Math_addFloat32(bindings_tests_IsolateThread* isolate, float a, float b);
float bindings_tests_Math_addFloat32(float a, float b) {
    return __bindings_tests_Math_addFloat32(isolate::global(), a, b);
}
double __bindings_tests_Math_addFloat64(bindings_tests_IsolateThread* isolate, double a, double b);
double bindings_tests_Math_addFloat64(double a, double b) {
    return __bindings_tests_Math_addFloat64(isolate::global(), a, b);
}
long long __bindings_tests_Math_addSize(bindings_tests_IsolateThread* isolate, long long a, long long b);
size_t bindings_tests_Math_addSize(size_t a, size_t b) {
    return __bindings_tests_Math_addSize(isolate::global(), static_cast<long long>(a), static_cast<long long>(b));
}
int __bindings_tests_Math_sizeToInt32(bindings_tests_IsolateThread* isolate, long long value);
int bindings_tests_Math_sizeToInt32(size_t value) {
    return __bindings_tests_Math_sizeToInt32(isolate::global(), static_cast<long long>(value));
}
int __bindings_tests_Math_maxInt32(bindings_tests_IsolateThread* isolate);
int bindings_tests_Math_maxInt32() {
    return __bindings_tests_Math_maxInt32(isolate::global());
}
long long __bindings_tests_Math_maxSize(bindings_tests_IsolateThread* isolate);
size_t bindings_tests_Math_maxSize() {
    return __bindings_tests_Math_maxSize(isolate::global());
}

// ==== Overloads ====
int __bindings_tests_Overloads_scale(bindings_tests_IsolateThread* isolate, int value, int factor);
int bindings_tests_Overloads_scale(int value, int factor) {
    return __bindings_tests_Overloads_scale(isolate::global(), value, factor);
}
const char* __bindings_tests_Overloads_greet(bindings_tests_IsolateThread* isolate, const char* name, const char* greeting);
const char* bindings_tests_Overloads_greet(const char* name, const char* greeting) {
    return __bindings_tests_Overloads_greet(isolate::global(), name, greeting);
}
int __bindings_tests_Overloads_total(bindings_tests_IsolateThread* isolate, int sum, int factor);
int bindings_tests_Overloads_total(int sum, int factor) {
    return __bindings_tests_Overloads_total(isolate::global(), sum, factor);
}
double __bindings_tests_Overloads_gain(bindings_tests_IsolateThread* isolate, int invert, double scale);
double bindings_tests_Overloads_gain(int invert, double scale) {
    return __bindings_tests_Overloads_gain(isolate::global(), invert, scale);
}
int __bindings_tests_Overloads_echoIndex(bindings_tests_IsolateThread* isolate, int index);
int bindings_tests_Overloads_echoIndex(int index) {
    return __bindings_tests_Overloads_echoIndex(isolate::global(), index);
}
int __bindings_tests_Overloads_answer(bindings_tests_IsolateThread* isolate, int multiplier);
int bindings_tests_Overloads_answer(int multiplier) {
    return __bindings_tests_Overloads_answer(isolate::global(), multiplier);
}
int __bindings_tests_Overloads_renamed(bindings_tests_IsolateThread* isolate);
int bindings_tests_Overloads_renamed() {
    return __bindings_tests_Overloads_renamed(isolate::global());
}

// ==== Palette ====
bindings_tests_Color __bindings_tests_Palette_next(bindings_tests_IsolateThread* isolate, bindings_tests_Color color);
bindings_tests_Color bindings_tests_Palette_next(bindings_tests_Color color) {
    return __bindings_tests_Palette_next(isolate::global(), color);
}
const char* __bindings_tests_Palette_nameOf(bindings_tests_IsolateThread* isolate, bindings_tests_Color color);
const char* bindings_tests_Palette_nameOf(bindings_tests_Color color) {
    return __bindings_tests_Palette_nameOf(isolate::global(), color);
}
int __bindings_tests_Palette_ordinalOf(bindings_tests_IsolateThread* isolate, bindings_tests_Color color);
int bindings_tests_Palette_ordinalOf(bindings_tests_Color color) {
    return __bindings_tests_Palette_ordinalOf(isolate::global(), color);
}
const char* __bindings_tests_Palette_describe(bindings_tests_IsolateThread* isolate, bindings_tests_Color color, bindings_tests_Marker marker);
const char* bindings_tests_Palette_describe(bindings_tests_Color color, bindings_tests_Marker marker) {
    return __bindings_tests_Palette_describe(isolate::global(), color, marker);
}
bindings_tests_Color __bindings_tests_Palette_fromOrdinal(bindings_tests_IsolateThread* isolate, int ordinal);
bindings_tests_Color bindings_tests_Palette_fromOrdinal(int ordinal) {
    return __bindings_tests_Palette_fromOrdinal(isolate::global(), ordinal);
}

// ==== Strings ====
const char* __bindings_tests_Strings_echo(bindings_tests_IsolateThread* isolate, const char* value);
const char* bindings_tests_Strings_echo(const char* value) {
    return __bindings_tests_Strings_echo(isolate::global(), value);
}
const char* __bindings_tests_Strings_concat(bindings_tests_IsolateThread* isolate, const char* a, const char* b);
const char* bindings_tests_Strings_concat(const char* a, const char* b) {
    return __bindings_tests_Strings_concat(isolate::global(), a, b);
}
long long __bindings_tests_Strings_utf8Length(bindings_tests_IsolateThread* isolate, const char* value);
size_t bindings_tests_Strings_utf8Length(const char* value) {
    return __bindings_tests_Strings_utf8Length(isolate::global(), value);
}
long long __bindings_tests_Strings_utf16Length(bindings_tests_IsolateThread* isolate, const char* value);
size_t bindings_tests_Strings_utf16Length(const char* value) {
    return __bindings_tests_Strings_utf16Length(isolate::global(), value);
}
const char* __bindings_tests_Strings_empty(bindings_tests_IsolateThread* isolate);
const char* bindings_tests_Strings_empty() {
    return __bindings_tests_Strings_empty(isolate::global());
}
const char* __bindings_tests_Strings_nullString(bindings_tests_IsolateThread* isolate);
const char* bindings_tests_Strings_nullString() {
    return __bindings_tests_Strings_nullString(isolate::global());
}
const char* __bindings_tests_Strings_join(bindings_tests_IsolateThread* isolate, const char** values, long long count, const char* separator);
const char* bindings_tests_Strings_join(const char** values, size_t count, const char* separator) {
    return __bindings_tests_Strings_join(isolate::global(), values, static_cast<long long>(count), separator);
}
const char* __bindings_tests_Strings_repeat(bindings_tests_IsolateThread* isolate, const char* value, long long times);
const char* bindings_tests_Strings_repeat(const char* value, size_t times) {
    return __bindings_tests_Strings_repeat(isolate::global(), value, static_cast<long long>(times));
}

// ==== Structs ====
void __bindings_tests_Structs_fillFieldTypes(bindings_tests_IsolateThread* isolate, unsigned short unit, short* shorts, long long int* longs, void* context, bindings_tests_FieldTypes* structOut);
bindings_tests_FieldTypes bindings_tests_Structs_fillFieldTypes(unsigned short unit, short* shorts, long long int* longs, void* context) {
    bindings_tests_FieldTypes result_;
    __bindings_tests_Structs_fillFieldTypes(isolate::global(), unit, shorts, longs, context, &result_);
    return result_;
}
double __bindings_tests_Structs_totalPower(bindings_tests_IsolateThread* isolate, const bindings_tests_StatusInfo* status, double currentAmps);
double bindings_tests_Structs_totalPower(bindings_tests_StatusInfo status, double currentAmps) {
    return __bindings_tests_Structs_totalPower(isolate::global(), &status, currentAmps);
}
double __bindings_tests_Structs_checkedVoltage(bindings_tests_IsolateThread* isolate, bindings_tests_ErrorInfo* _error, const bindings_tests_StatusInfo* status);
double bindings_tests_Structs_checkedVoltage(bindings_tests_ErrorInfo* _error, bindings_tests_StatusInfo status) {
    return __bindings_tests_Structs_checkedVoltage(isolate::global(), _error, &status);
}
void __bindings_tests_Structs_getStatus(bindings_tests_IsolateThread* isolate, int index, bindings_tests_StatusInfo* structOut);
bindings_tests_StatusInfo bindings_tests_Structs_getStatus(int index) {
    bindings_tests_StatusInfo result_;
    __bindings_tests_Structs_getStatus(isolate::global(), index, &result_);
    return result_;
}
void __bindings_tests_Structs_getCheckedStatus(bindings_tests_IsolateThread* isolate, bindings_tests_ErrorInfo* _error, int index, bindings_tests_StatusInfo* structOut);
bindings_tests_StatusInfo bindings_tests_Structs_getCheckedStatus(bindings_tests_ErrorInfo* _error, int index) {
    bindings_tests_StatusInfo result_;
    __bindings_tests_Structs_getCheckedStatus(isolate::global(), _error, index, &result_);
    return result_;
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
