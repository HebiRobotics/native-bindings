/*
 * Runtime test for the raw C ABI of the bindings_tests library.
 *
 * This is the most direct check that the generated ABI is correct. It talks to
 * the shared library through the generated bindings_tests_c.h header only, with
 * no wrapper in between, so anything that fails here is a problem in the C entry
 * points rather than in one of the language wrappers.
 *
 * Every value asserted here is also asserted by the C++, Python and MATLAB
 * suites, so a divergence between languages is easy to spot.
 *
 * Exit code 0 means success.
 */

/* assert() must stay live even in a release build. */
#undef NDEBUG

#include <assert.h>
#include <math.h>
#include <stddef.h>
#include <stdio.h>
#include <string.h>

#include "bindings_tests_c.h"

/*
 * Strings returned by the library live in a per-thread scratch buffer that the
 * next call on the same thread overwrites. Never hold on to two of them at once.
 */

/* ------------------------------------------------------------------ */
/* Math                                                                */
/* ------------------------------------------------------------------ */

static void test_math(void) {
    assert(bindings_tests_Math_negate(1) == 0);
    assert(bindings_tests_Math_negate(0) != 0);

    assert(bindings_tests_Math_nextChar('A') == 'B');

    assert(bindings_tests_Math_addInt8(100, 20) == 120);
    assert(bindings_tests_Math_addInt16(30000, 700) == 30700);
    assert(bindings_tests_Math_addInt32(2, 3) == 5);

    /* 2^33 + 1. Catches platforms that truncate a Java long to 32 bit. */
    assert(bindings_tests_Math_addInt64(8589934592LL, 1LL) == 8589934593LL);

    assert(bindings_tests_Math_addFloat32(0.5f, 0.25f) == 0.75f);
    assert(bindings_tests_Math_addFloat64(0.5, 0.25) == 0.75);

    assert(bindings_tests_Math_addSize((size_t) 4, (size_t) 5) == (size_t) 9);
    assert(bindings_tests_Math_sizeToInt32((size_t) 7) == 7);

    assert(bindings_tests_Math_maxInt32() == 2147483647);
    assert(bindings_tests_Math_maxSize() == (size_t) 9223372036854775807LL);

    printf("math ok\n");
}

/* ------------------------------------------------------------------ */
/* Counter (object handles)                                            */
/* ------------------------------------------------------------------ */

static void test_counter(void) {
    bindings_tests_CounterPtr counter;
    bindings_tests_CounterPtr defaultCounter;

    bindings_tests_Counter_resetClosedCount();
    assert(bindings_tests_Counter_getClosedCount() == 0);

    counter = bindings_tests_Counter_create(NULL, 10);
    assert(counter != NULL);

    bindings_tests_Counter_increment(counter);
    bindings_tests_Counter_increment(counter);
    assert(bindings_tests_Counter_getValue(counter) == 12);

    bindings_tests_Counter_add(counter, 5);
    assert(bindings_tests_Counter_getValue(counter) == 17);

    bindings_tests_Counter_setValue(counter, 3);
    assert(bindings_tests_Counter_getValue(counter) == 3);

    assert(bindings_tests_Counter_isClosed(counter) == 0);

    /* The C ABI has no default arguments, so the zero is passed explicitly. */
    defaultCounter = bindings_tests_Counter_create(NULL, 0);
    assert(defaultCounter != NULL);
    assert(bindings_tests_Counter_getValue(defaultCounter) == 0);

    /* Releasing runs the generated destructor, which calls close() on the Java side. */
    bindings_tests_Counter_release(counter);
    bindings_tests_Counter_release(defaultCounter);
    assert(bindings_tests_Counter_getClosedCount() == 2);

    printf("counter ok\n");
}

/* ------------------------------------------------------------------ */
/* Naming (cPrefix and useCamelCase)                                   */
/* ------------------------------------------------------------------ */

static void test_naming(void) {
    custom_prefix_NamingPtr naming;

    /*
     * The symbols themselves are the assertion. This is the only language that sees
     * them, and nothing links if the generator gets either option wrong: the class
     * carries a prefix of its own instead of the library one, and its method names
     * are snake case. Only the method names convert, the class name stays as written.
     */
    assert(custom_prefix_Naming_add_two_numbers(2, 3) == 5);

    naming = custom_prefix_Naming_create(NULL, 7);
    assert(naming != NULL);
    assert(custom_prefix_Naming_get_start_value(naming) == 7);
    custom_prefix_Naming_release(naming);

    printf("naming ok\n");
}

/* ------------------------------------------------------------------ */
/* Strings                                                             */
/* ------------------------------------------------------------------ */

static void test_strings(void) {
    /* This file is UTF-8. The literals below must survive the round trip byte for byte. */
    const char* utf8Input = "na\xc3\xaf" "ve caf\xc3\xa9"; /* naive cafe with diacritics */
    const char* joinValues[3];
    const char* result;

    result = bindings_tests_Strings_echo("hello");
    assert(result != NULL);
    assert(strcmp(result, "hello") == 0);

    result = bindings_tests_Strings_echo(utf8Input);
    assert(result != NULL);
    assert(strcmp(result, utf8Input) == 0);

    result = bindings_tests_Strings_concat("ab", "cd");
    assert(strcmp(result, "abcd") == 0);

    /* 'naive' with a diaeresis: 6 UTF-8 bytes, 5 UTF-16 code units. */
    assert(bindings_tests_Strings_utf8Length("na\xc3\xaf" "ve") == (size_t) 6);
    assert(bindings_tests_Strings_utf16Length("na\xc3\xaf" "ve") == (size_t) 5);

    /* An empty string is not the same thing as a null pointer. */
    result = bindings_tests_Strings_empty();
    assert(result != NULL);
    assert(strlen(result) == 0);

    assert(bindings_tests_Strings_nullString() == NULL);

    joinValues[0] = "a";
    joinValues[1] = "b";
    joinValues[2] = "c";
    result = bindings_tests_Strings_join(joinValues, (size_t) 3, "-");
    assert(strcmp(result, "a-b-c") == 0);

    /* 200 bytes, which forces the 128 byte scratch buffer to grow. */
    result = bindings_tests_Strings_repeat("0123456789", (size_t) 20);
    assert(strlen(result) == 200);

    printf("strings ok\n");
}

/* ------------------------------------------------------------------ */
/* Arrays                                                              */
/* ------------------------------------------------------------------ */

static void test_arrays(void) {
    double doubles[4];
    double target[4];
    int ints[3];
    int i;

    doubles[0] = 1.0;
    doubles[1] = 2.0;
    doubles[2] = 3.5;
    assert(bindings_tests_Arrays_sumFloat64(doubles, (size_t) 3) == 6.5);

    ints[0] = 1;
    ints[1] = 2;
    ints[2] = 3;
    assert(bindings_tests_Arrays_sumInt32(ints, (size_t) 3) == 6);

    for (i = 0; i < 4; i++) {
        doubles[i] = 0.0;
    }
    bindings_tests_Arrays_fillFloat64(doubles, (size_t) 4, 2.5);
    for (i = 0; i < 4; i++) {
        assert(doubles[i] == 2.5);
    }

    doubles[0] = 1.0;
    doubles[1] = 2.0;
    doubles[2] = 3.0;
    doubles[3] = 4.0;
    bindings_tests_Arrays_cumulativeSum(doubles, target, (size_t) 4);
    assert(target[0] == 1.0);
    assert(target[1] == 3.0);
    assert(target[2] == 6.0);
    assert(target[3] == 10.0);

    doubles[0] = 1.0;
    doubles[1] = 2.0;
    doubles[2] = 3.0;
    doubles[3] = 4.0;
    bindings_tests_Arrays_reverseFloat64(doubles, (size_t) 4);
    assert(doubles[0] == 4.0);
    assert(doubles[1] == 3.0);
    assert(doubles[2] == 2.0);
    assert(doubles[3] == 1.0);

    printf("arrays ok\n");
}

/* ------------------------------------------------------------------ */
/* Spans (pointer and length as one by-value struct)                   */
/* ------------------------------------------------------------------ */

static void test_spans(void) {
    double xs[3];
    double ys[3];
    bindings_tests_DoubleSpan x;
    bindings_tests_DoubleSpan y;
    bindings_tests_DoubleSpan result;
    bindings_tests_ErrorInfo error;
    int i;

    xs[0] = 1.0;
    xs[1] = 2.0;
    xs[2] = 3.0;
    ys[0] = 4.0;
    ys[1] = 5.0;
    ys[2] = 6.0;
    x.data = xs;
    x.length = 3;
    y.data = ys;
    y.length = 3;
    assert(bindings_tests_Arrays_dotProduct(NULL, x, y) == 32.0);

    /* The length check lives once in the Java implementation. A mismatch
       returns the type default and reports through the error struct. */
    y.length = 2;
    assert(bindings_tests_Arrays_dotProduct(&error, x, y) == 0.0);
    assert(error.code != 0);
    assert(error.message != NULL && strstr(error.message, "elements") != NULL);
    y.length = 3;

    /* Span in, span out. The result is library-owned. */
    result = bindings_tests_Arrays_addOffset(x, 10.0);
    assert(result.length == 3);
    assert(result.data[0] == 11.0);
    assert(result.data[1] == 12.0);
    assert(result.data[2] == 13.0);

    /* Typed spans repeat the same shape per element type */
    {
        int32_t ints[3];
        float floats[3];
        bindings_tests_IntSpan ispan;
        bindings_tests_FloatSpan fspan;
        bindings_tests_IntSpan iresult;
        bindings_tests_FloatSpan fresult;
        ints[0] = 1; ints[1] = 2; ints[2] = 3;
        floats[0] = 1.5f; floats[1] = 2.5f; floats[2] = 3.5f;
        ispan.data = ints;
        ispan.length = 3;
        fspan.data = floats;
        fspan.length = 3;
        iresult = bindings_tests_Arrays_addOffsetInt32(ispan, 10);
        assert(iresult.length == 3);
        assert(iresult.data[0] == 11 && iresult.data[1] == 12 && iresult.data[2] == 13);
        fresult = bindings_tests_Arrays_addOffsetFloat32(fspan, 10.0f);
        assert(fresult.length == 3);
        assert(fresult.data[0] == 11.5f && fresult.data[1] == 12.5f && fresult.data[2] == 13.5f);
    }

    /* Raw uint8 buffer with an explicit count, the shape an image row uses.
       The sum is unsigned, 250 counts as 250 and not -6. */
    {
        uint8_t raw[3];
        raw[0] = 250; raw[1] = 10; raw[2] = 5;
        assert(bindings_tests_Arrays_sumUint8(raw, 3) == 265);
    }

    /* Byte spans carry binary data, so an embedded zero must survive the
       roundtrip. 246 + 10 wraps to 0 in uint8. */
    {
        uint8_t bytes[3];
        bindings_tests_ByteSpan bspan;
        bindings_tests_ByteSpan bresult;
        bytes[0] = 1; bytes[1] = 246; bytes[2] = 3;
        bspan.data = bytes;
        bspan.length = 3;
        bresult = bindings_tests_Arrays_addOffsetUint8(bspan, 10);
        assert(bresult.length == 3);
        assert(bresult.data[0] == 11 && bresult.data[1] == 0 && bresult.data[2] == 13);
    }

    result = bindings_tests_Arrays_getSequence((size_t) 5);
    assert(result.length == 5);
    for (i = 0; i < 5; i++) {
        assert(result.data[i] == (double) i);
    }

    /* A count of zero returns an empty span */
    result = bindings_tests_Arrays_getSequence((size_t) 0);
    assert(result.length == 0);

    /* 200 elements, which forces the 128 element scratch buffer to grow */
    result = bindings_tests_Arrays_getSequence((size_t) 200);
    assert(result.length == 200);
    assert(result.data[199] == 199.0);

    printf("spans ok\n");
}

/* ------------------------------------------------------------------ */
/* Structs (a user defined struct passed by value)                     */
/* ------------------------------------------------------------------ */

static void test_structs(void) {
    /* Declaration order is the ABI, so the initializer doubles as a layout check. */
    bindings_tests_StatusInfo status = {48.0, 2, 1};
    bindings_tests_StatusInfo out;
    bindings_tests_ErrorInfo error;

    assert(bindings_tests_Structs_totalPower(status, 2.5) == 120.0);
    assert(bindings_tests_Structs_checkedVoltage(&error, status) == 48.0);
    assert(error.code == 0);

    /* State 1 is not the running state, so the power is zero while the voltage still reads. */
    status.state = 1;
    assert(bindings_tests_Structs_totalPower(status, 2.5) == 0.0);
    assert(bindings_tests_Structs_checkedVoltage(&error, status) == 48.0);
    status.state = 2;

    /* A bool field crosses as an int32, so the C caller writes 0 rather than false. */
    status.enabled = 0;
    assert(bindings_tests_Structs_totalPower(status, 2.5) == 0.0);
    assert(bindings_tests_Structs_checkedVoltage(&error, status) == 0.0);
    assert(error.code != 0);
    assert(error.message != NULL && strstr(error.message, "disabled") != NULL);
    assert(error.id != NULL && strcmp(error.id, "bindings_tests:IllegalArgumentException") == 0);

    /* The remaining field types: a char code unit crossing as uint16, and typed
       pointers passing through untouched. */
    {
        short shorts[2];
        long long int longs[2];
        bindings_tests_FieldTypes ft;
        shorts[0] = 1; shorts[1] = 2;
        longs[0] = 3; longs[1] = 4;
        ft = bindings_tests_Structs_fillFieldTypes(65, shorts, longs, shorts);
        assert(ft.unit == 65);
        assert(ft.shorts == (const int16_t*) shorts);
        assert(ft.longs == (const int64_t*) longs);
        assert(ft.context == (void*) shorts);
    }

    /* getStatus/getCheckedStatus present the same struct the other way round, as a
       by-value return keyed by index rather than an input struct the caller assembles. */
    out = bindings_tests_Structs_getStatus(0);
    assert(out.voltage == 48.0);
    assert(out.state == 2);
    assert(out.enabled != 0);

    out = bindings_tests_Structs_getStatus(1);
    assert(out.voltage == 12.5);
    assert(out.state == 1);
    assert(out.enabled == 0);

    out = bindings_tests_Structs_getCheckedStatus(&error, 0);
    assert(error.code == 0);
    assert(out.voltage == 48.0);
    assert(out.state == 2);
    assert(out.enabled != 0);

    /* A negative index throws, and the entry point zeroes every field of the out
       parameter through its setters before returning, so a failed call cannot be
       mistaken for a successful one that happens to read zero. */
    out = bindings_tests_Structs_getCheckedStatus(&error, -1);
    assert(error.code != 0);
    assert(error.message != NULL && strstr(error.message, "negative") != NULL);
    assert(error.id != NULL && strcmp(error.id, "bindings_tests:IllegalArgumentException") == 0);
    assert(out.voltage == 0.0);
    assert(out.state == 0);
    assert(out.enabled == 0);

    printf("structs ok\n");
}

/* ------------------------------------------------------------------ */
/* Shapes (inheritance)                                                */
/* ------------------------------------------------------------------ */

static void test_shapes(void) {
    bindings_tests_CirclePtr circle;
    bindings_tests_SquarePtr square;
    bindings_tests_ShapePtr shape;

    circle = bindings_tests_Circle_create(NULL, 2.0);
    assert(circle != NULL);
    assert(fabs(bindings_tests_Circle_area(circle) - 3.14159265358979323846 * 4.0) < 1e-9);
    assert(bindings_tests_Circle_sideCount(circle) == 0);

    square = bindings_tests_Square_create(NULL, 3.0);
    assert(square != NULL);
    assert(bindings_tests_Square_area(square) == 9.0);
    assert(bindings_tests_Square_sideCount(square) == 4);

    /*
     * The important part: go through the generated cast function and call the
     * base type, which must still dispatch to the concrete Java subclass.
     */
    shape = bindings_tests_Circle_to_Shape(circle);
    assert(shape != NULL);
    assert(fabs(bindings_tests_Shape_area(shape) - 3.14159265358979323846 * 4.0) < 1e-9);
    assert(bindings_tests_Shape_sideCount(shape) == 0);
    assert(strcmp(bindings_tests_Shape_getName(shape), "circle") == 0);
    bindings_tests_Shape_release(shape);

    shape = bindings_tests_Square_to_Shape(square);
    assert(shape != NULL);
    assert(bindings_tests_Shape_area(shape) == 9.0);
    assert(bindings_tests_Shape_sideCount(shape) == 4);
    assert(strcmp(bindings_tests_Shape_getName(shape), "square") == 0);
    bindings_tests_Shape_release(shape);

    printf("shapes ok\n");
}

/* ------------------------------------------------------------------ */
/* Palette (enums)                                                     */
/* ------------------------------------------------------------------ */

static void test_palette(void) {
    assert(bindings_tests_Palette_next(bindings_tests_Color_Red) == bindings_tests_Color_Green);
    assert(bindings_tests_Palette_next(bindings_tests_Color_Green) == bindings_tests_Color_Blue);
    assert(bindings_tests_Palette_next(bindings_tests_Color_Blue) == bindings_tests_Color_Red);

    assert(strcmp(bindings_tests_Palette_nameOf(bindings_tests_Color_Green), "Green") == 0);
    assert(bindings_tests_Palette_ordinalOf(bindings_tests_Color_Blue) == 2);

    /* Two enums declared in the same class, used in the same signature. */
    assert(strcmp(bindings_tests_Palette_describe(bindings_tests_Color_Red,
                                                  bindings_tests_Marker_Cross), "Red Cross") == 0);

    assert(bindings_tests_Palette_fromOrdinal(1) == bindings_tests_Color_Green);
    assert(bindings_tests_Palette_fromOrdinal(99) == bindings_tests_Color_Red);

    printf("palette ok\n");
}

/* ------------------------------------------------------------------ */
/* Errors                                                              */
/* ------------------------------------------------------------------ */

static void test_errors(void) {
    const char* message;
    bindings_tests_ErrorInfo error;

    /* A throwing void method stays void: the struct is the only report. */
    bindings_tests_Errors_requireNonNegative(NULL, 5);
    bindings_tests_Errors_requireNonNegative(&error, -1);
    assert(error.code != 0);
    assert(error.message != NULL && strstr(error.message, "must not be negative") != NULL);
    assert(error.id != NULL && strcmp(error.id, "bindings_tests:IllegalArgumentException") == 0);

    /*
     * The struct is what makes a value return checkable. The return value on failure is
     * still the type specific default, but it is no longer the only thing the caller sees.
     */
    error.code = -1;
    assert(bindings_tests_Errors_divideInt32(&error, 10, 2) == 5);
    assert(error.code == 0);
    assert(error.message == NULL);
    assert(error.id == NULL);

    assert(bindings_tests_Errors_divideInt32(&error, 1, 0) == 0);
    assert(error.code != 0);
    assert(error.message != NULL && strstr(error.message, "zero") != NULL);
    assert(error.id != NULL && strcmp(error.id, "bindings_tests:ArithmeticException") == 0);

    assert(bindings_tests_Errors_failingFloat64(&error, 0) == 1.5);
    assert(error.code == 0);
    assert(bindings_tests_Errors_failingFloat64(&error, 1) == 0.0);
    assert(error.code != 0);

    assert(bindings_tests_Errors_failingBool(&error, 0) != 0);
    assert(error.code == 0);
    assert(bindings_tests_Errors_failingBool(&error, 1) == 0);
    assert(error.code != 0);

    message = bindings_tests_Errors_failingString(&error, 0);
    assert(message != NULL);
    assert(strcmp(message, "ok") == 0);
    assert(error.code == 0);
    assert(bindings_tests_Errors_failingString(&error, 1) == NULL);
    assert(error.code != 0);

    /* An exception without a message crosses as NULL rather than an empty string. */
    bindings_tests_Errors_failWithoutMessage(&error);
    assert(error.code != 0);
    assert(error.message == NULL);
    assert(error.id != NULL && strcmp(error.id, "bindings_tests:IllegalStateException") == 0);

    /* A caller that does not care passes NULL, which every entry point tolerates. */
    assert(bindings_tests_Errors_divideInt32(NULL, 1, 0) == 0);

    /*
     * neverThrows is marked Noexcept, so it has no status code at all. The call
     * below only compiles because the header declares it as returning void.
     */
    bindings_tests_Errors_neverThrows();

    /* A struct reused across calls is cleared on every one, so no failure lingers. */
    bindings_tests_Errors_requireNonNegative(&error, 0);
    assert(error.code == 0);
    assert(error.message == NULL);
    assert(error.id == NULL);

    printf("errors ok\n");
}

/* ------------------------------------------------------------------ */
/* Overloads                                                           */
/* ------------------------------------------------------------------ */

static void test_overloads(void) {
    const char* result;

    /*
     * There is exactly one C symbol per method here. Default arguments and the
     * collection overloads only exist in the language wrappers, so C always has
     * to pass every argument explicitly.
     */
    assert(bindings_tests_Overloads_scale(3, 4) == 12);
    assert(bindings_tests_Overloads_scale(1, 10) == 10);
    assert(bindings_tests_Overloads_scale(3, 10) == 30);

    result = bindings_tests_Overloads_greet("Bob", "Hello");
    assert(strcmp(result, "Hello, Bob") == 0);
    result = bindings_tests_Overloads_greet("Bob", "Hi");
    assert(strcmp(result, "Hi, Bob") == 0);

    /* C takes the pre-summed total, the wrappers get a collection overload. */
    assert(bindings_tests_Overloads_total(6, 2) == 12);

    /* A boolean default and a scientific notation double default. C has neither, so
       it is the only language that has to pass both explicitly. */
    assert(bindings_tests_Overloads_gain(0, 2.0) == 2.0);
    assert(bindings_tests_Overloads_gain(1, 2.0) == -2.0);

    /* C is the zero based baseline that the MATLAB postBody and postCall convert around. */
    assert(bindings_tests_Overloads_echoIndex(3) == 3);

    assert(bindings_tests_Overloads_answer(2) == 84);

    /* @CLibIndex is a no-op in C: the ABI is zero based, same as Java. */
    assert(bindings_tests_Indices_echo(3) == 3);
    assert(bindings_tests_Indices_countUpTo(3) == 4);
    assert(bindings_tests_Indices_lastIndexOf(5) == 4);
    assert(bindings_tests_Indices_offsetFrom(1, 2) == 12);
    assert(bindings_tests_Indices_offsetFrom(0, 0) == 0);
    assert(bindings_tests_Indices_fromRow(5) == 5);
    assert(bindings_tests_Indices_fromRow(0) == 0);
    assert(bindings_tests_Indices_echoSize((size_t) 3) == (size_t) 3);

    /* Declared as originalName in Java, exported as renamed. */
    assert(bindings_tests_Overloads_renamed() == 7);

    /*
     * There must be no symbol named bindings_tests_Overloads_originalName and no
     * symbol for the ignored hidden() method. Absence is checked by the reference
     * file test in the Java module, since C cannot assert it at runtime.
     */

    printf("overloads ok\n");
}

/* ------------------------------------------------------------------ */
/* Isolated (second isolate)                                           */
/* ------------------------------------------------------------------ */

static void test_isolated(void) {
    bindings_tests_ErrorInfo error;

    /*
     * This state lives in a second GraalVM isolate. It does not share static
     * fields or the handle registry with the default isolate.
     */
    bindings_tests_Isolated_reset();
    bindings_tests_Isolated_increment();
    bindings_tests_Isolated_increment();
    bindings_tests_Isolated_increment();
    assert(bindings_tests_Isolated_getCounter() == 3);

    /* The error struct is caller-owned, so the secondary isolate reports the same way. */
    bindings_tests_Isolated_alwaysFails(&error);
    assert(error.code != 0);
    assert(error.message != NULL && strstr(error.message, "secondary isolate") != NULL);

    printf("isolated ok\n");
}

/* ------------------------------------------------------------------ */
/* Isolate management                                                  */
/* ------------------------------------------------------------------ */

/*
 * The prefixed aliases of the GraalVM isolate API are exported by the image
 * but kept out of the public header on purpose, so this test declares the
 * symbols it links against itself.
 */
typedef struct bindings_tests_Isolate_s bindings_tests_Isolate;
typedef struct bindings_tests_IsolateThread_s bindings_tests_IsolateThread;

bindings_tests_IsolateThread* bindings_tests_createIsolate(void);
bindings_tests_Isolate* bindings_tests_getIsolate(bindings_tests_IsolateThread* thread);
bindings_tests_IsolateThread* bindings_tests_attachThread(bindings_tests_Isolate* isolate);
bindings_tests_IsolateThread* bindings_tests_getCurrentThread(bindings_tests_Isolate* isolate);
int bindings_tests_detachThread(bindings_tests_IsolateThread* thread);
int bindings_tests_tearDownIsolate(bindings_tests_IsolateThread* thread);

static void test_isolate_api(void) {
    /*
     * Creating another isolate of the same image is supported, so this runs a
     * scratch isolate through the full lifecycle. The isolates behind the
     * wrappers stay untouched: tearing down the shared global isolate would
     * break every later call.
     */
    bindings_tests_IsolateThread* thread = bindings_tests_createIsolate();
    assert(thread != NULL);
    bindings_tests_Isolate* isolate = bindings_tests_getIsolate(thread);
    assert(isolate != NULL);
    assert(bindings_tests_getCurrentThread(isolate) == thread);
    assert(bindings_tests_detachThread(thread) == 0);

    /* Re-attach so the scratch isolate can be torn down completely. */
    thread = bindings_tests_attachThread(isolate);
    assert(thread != NULL);
    assert(bindings_tests_tearDownIsolate(thread) == 0);

    printf("isolate api ok\n");
}

/* ------------------------------------------------------------------ */
/* Callbacks                                                           */
/* ------------------------------------------------------------------ */

#define MAX_CALLBACKS 16

typedef struct {
    int count;
    int indices[MAX_CALLBACKS];
} CallbackState;

static void on_index(void* userData, int index) {
    CallbackState* state = (CallbackState*) userData;
    if (state->count < MAX_CALLBACKS) {
        state->indices[state->count] = index;
    }
    state->count++;
}

static void on_notify(void* userData) {
    ((CallbackState*) userData)->count++;
}

static double on_scale(void* userData, double value) {
    ((CallbackState*) userData)->count++;
    return value * 2.0;
}

static void test_callbacks(void) {
    CallbackState state;
    bindings_tests_ErrorInfo error;
    bindings_tests_NotifiedPtr notified;
    bindings_tests_CountedPtr counted;
    int i;

    /* Fires synchronously, five times, with indices 0..4, before returning. */
    memset(&state, 0, sizeof(state));
    bindings_tests_Callbacks_invokeTimes(&on_index, &state, (size_t) 5);
    assert(state.count == 5);
    for (i = 0; i < 5; i++) {
        assert(state.indices[i] == i);
    }

    memset(&state, 0, sizeof(state));
    bindings_tests_Callbacks_invokeOnce(&on_index, &state, 7);
    assert(state.count == 1);
    assert(state.indices[0] == 7);

    /* A null function pointer is a no-op. */
    memset(&state, 0, sizeof(state));
    bindings_tests_Callbacks_invokeTimes(NULL, &state, (size_t) 5);
    bindings_tests_Callbacks_invokeOnce(NULL, &state, 7);
    assert(state.count == 0);

    memset(&state, 0, sizeof(state));
    bindings_tests_Callbacks_invokeChecked(&error, &on_index, &state, 3);
    assert(error.code == 0);
    assert(state.count == 1);
    assert(state.indices[0] == 3);

    /* The callback fires before the validation, so a failing call still records it. */
    memset(&state, 0, sizeof(state));
    bindings_tests_Callbacks_invokeChecked(&error, &on_index, &state, -1);
    assert(error.code != 0);
    assert(error.message != NULL && strstr(error.message, "must not be negative") != NULL);
    assert(state.count == 1);
    assert(state.indices[0] == -1);

    memset(&state, 0, sizeof(state));
    bindings_tests_Callbacks_notifyOnce(&on_notify, &state);
    assert(state.count == 1);

    /* The value the callback produces is what the method hands back. */
    memset(&state, 0, sizeof(state));
    assert(bindings_tests_Callbacks_applyScaled(&on_scale, &state, 21.0) == 42.0);
    assert(state.count == 1);
    assert(bindings_tests_Callbacks_applyScaled(NULL, &state, 21.0) == 21.0);
    assert(state.count == 1);

    /* Fires on a worker thread the method joins before returning, so the recorded index
       is visible here without any synchronization of our own. */
    memset(&state, 0, sizeof(state));
    bindings_tests_Callbacks_invokeOnWorkerThread(&on_index, &state, 4);
    assert(state.count == 1);
    assert(state.indices[0] == 4);

    /* A constructor takes a callback like any other method, and this one fires during
       construction. */
    memset(&state, 0, sizeof(state));
    notified = bindings_tests_Notified_create(&error, &on_notify, &state);
    assert(error.code == 0);
    assert(notified != NULL);
    assert(state.count == 1);
    bindings_tests_Notified_release(notified);

    /* A synchronous callback in a constructor fires once per index, in order. */
    memset(&state, 0, sizeof(state));
    counted = bindings_tests_Counted_create(&error, &on_index, &state, 3);
    assert(error.code == 0);
    assert(counted != NULL);
    assert(state.count == 3);
    for (i = 0; i < 3; i++) {
        assert(state.indices[i] == i);
    }
    bindings_tests_Counted_release(counted);

    printf("callbacks ok\n");
}

/* ------------------------------------------------------------------ */

int main(void) {
    test_math();
    test_counter();
    test_naming();
    test_strings();
    test_arrays();
    test_spans();
    test_structs();
    test_shapes();
    test_palette();
    test_errors();
    test_overloads();
    test_isolated();
    test_isolate_api();
    test_callbacks();
    printf("all C tests passed\n");
    return 0;
}
