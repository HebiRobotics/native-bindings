// Runtime test for the generated RAII C++ wrapper of the bindings_tests library.
//
// This asserts the same value table as the C, Python and MATLAB suites, so a
// divergence between languages shows up as a failure here. There is no external
// test framework, just the CHECK macro below and a failure counter.
//
// Exit code 0 means success, which is what CTest keys off.

#include <cmath>
#include <cstddef>
#include <cstdio>
#include <string>
#include <utility>
#include <vector>

#include "bindings_tests.hpp"

namespace bt = bindings::tests;

static int failures = 0;

#define CHECK(cond)                                                            \
  do {                                                                         \
    if (!(cond)) {                                                             \
      std::printf("FAILED %s:%d: %s\n", __FILE__, __LINE__, #cond);            \
      ++failures;                                                              \
    }                                                                          \
  } while (0)

static const double kPi = 3.14159265358979323846;

// Every throwing method now reports through the error out-struct, so the wrapper raises
// instead of handing back a default the caller cannot tell apart from data.
template <typename Call>
static bool throwsWithMessage(Call call, const char* fragment) {
  try {
    call();
  } catch (const bt::Exception& e) {
    return std::string(e.what()).find(fragment) != std::string::npos;
  }
  return false;
}

// ---------------------------------------------------------------------------
// Math
// ---------------------------------------------------------------------------

static void testMath() {
  CHECK(bt::math::negate(true) == false);
  CHECK(bt::math::negate(false) == true);

  CHECK(bt::math::nextChar('A') == 'B');

  CHECK(bt::math::addInt8(100, 20) == 120);
  CHECK(bt::math::addInt16(30000, 700) == 30700);
  CHECK(bt::math::addInt32(2, 3) == 5);

  // 2^33 + 1. Catches platforms that truncate a Java long to 32 bit.
  CHECK(bt::math::addInt64(8589934592LL, 1LL) == 8589934593LL);

  CHECK(bt::math::addFloat32(0.5f, 0.25f) == 0.75f);
  CHECK(bt::math::addFloat64(0.5, 0.25) == 0.75);

  CHECK(bt::math::addSize(4, 5) == static_cast<std::size_t>(9));
  CHECK(bt::math::sizeToInt32(7) == 7);

  CHECK(bt::math::maxInt32() == 2147483647);
  CHECK(bt::math::maxSize() == static_cast<std::size_t>(9223372036854775807LL));
}

// ---------------------------------------------------------------------------
// Counter (object handles, released by RAII)
// ---------------------------------------------------------------------------

static void testCounter() {
  // Counter is instantiable, so it becomes a class and its static methods stay
  // static members. Only the non-instantiable classes turn into namespaces.
  bt::Counter::resetClosedCount();
  CHECK(bt::Counter::getClosedCount() == 0);

  {
    bt::Counter counter(10);
    counter.increment();
    counter.increment();
    CHECK(counter.getValue() == 12);

    counter.add(5);
    CHECK(counter.getValue() == 17);

    counter.setValue(3);
    CHECK(counter.getValue() == 3);

    CHECK(counter.isClosed() == false);

    // The single constructor defaults 'start' to 0, so this resolves to Counter(0)
    bt::Counter defaultCounter;
    CHECK(defaultCounter.getValue() == 0);
  }
  // Both handles went out of scope, so the generated destructor ran twice.
  CHECK(bt::Counter::getClosedCount() == 2);
}

// ---------------------------------------------------------------------------
// Naming (cPrefix and useCamelCase)
// ---------------------------------------------------------------------------

static void testNaming() {
  // Both options rename C symbols only. The wrapper is the assertion that they stop
  // there: the class sits in the same namespace as every other one and keeps its
  // camel case methods, even though it links against custom_prefix_Naming_*.
  bt::Naming naming(7);
  CHECK(naming.getStartValue() == 7);

  bt::Naming defaultNaming;
  CHECK(defaultNaming.getStartValue() == 0);

  CHECK(bt::Naming::addTwoNumbers(2, 3) == 5);
}

// ---------------------------------------------------------------------------
// Strings
// ---------------------------------------------------------------------------

static void testStrings() {
  // This file is UTF-8. The literal below must survive the round trip unchanged.
  const std::string utf8Input = "na\xc3\xaf" "ve caf\xc3\xa9";

  CHECK(bt::strings::echo("hello") == "hello");
  CHECK(bt::strings::echo(utf8Input) == utf8Input);

  CHECK(bt::strings::concat("ab", "cd") == "abcd");

  // 'naive' with a diaeresis: 6 UTF-8 bytes, 5 UTF-16 code units.
  CHECK(bt::strings::utf8Length("na\xc3\xaf" "ve") == static_cast<std::size_t>(6));
  CHECK(bt::strings::utf16Length("na\xc3\xaf" "ve") == static_cast<std::size_t>(5));

  CHECK(bt::strings::empty() == "");

  // The wrapper copies a char* into a std::string and turns a null pointer into
  // an empty string, so a null result is indistinguishable from "" here. The C
  // suite is what actually checks that the pointer is null.
  CHECK(bt::strings::nullString().empty());

  const char* values[] = {"a", "b", "c"};
  CHECK(bt::strings::join(values, 3, "-") == "a-b-c");

  // 200 characters, which forces the 128 byte scratch buffer to grow.
  CHECK(bt::strings::repeat("0123456789", 20).size() == 200);
}

// ---------------------------------------------------------------------------
// Arrays
// ---------------------------------------------------------------------------

static void testArrays() {
  double values[] = {1.0, 2.0, 3.5};
  CHECK(bt::arrays::sumFloat64(values, 3) == 6.5);

  // The const on the parameter gets a std::vector overload next to the pointer one.
  CHECK(bt::arrays::sumFloat64(std::vector<double>{1.0, 2.0, 3.5}, 3) == 6.5);

  int ints[] = {1, 2, 3};
  CHECK(bt::arrays::sumInt32(ints, 3) == 6);

  double filled[4] = {0.0, 0.0, 0.0, 0.0};
  bt::arrays::fillFloat64(filled, 4, 2.5);
  for (int i = 0; i < 4; ++i) {
    CHECK(filled[i] == 2.5);
  }

  double source[] = {1.0, 2.0, 3.0, 4.0};
  double target[4] = {0.0, 0.0, 0.0, 0.0};
  bt::arrays::cumulativeSum(source, target, 4);
  CHECK(target[0] == 1.0);
  CHECK(target[1] == 3.0);
  CHECK(target[2] == 6.0);
  CHECK(target[3] == 10.0);

  // Only the const source picks up the vector overload, the target stays a pointer.
  double target2[4] = {0.0, 0.0, 0.0, 0.0};
  bt::arrays::cumulativeSum(std::vector<double>{1.0, 2.0, 3.0, 4.0}, target2, 4);
  CHECK(target2[3] == 10.0);

  double reversed[] = {1.0, 2.0, 3.0, 4.0};
  bt::arrays::reverseFloat64(reversed, 4);
  CHECK(reversed[0] == 4.0);
  CHECK(reversed[1] == 3.0);
  CHECK(reversed[2] == 2.0);
  CHECK(reversed[3] == 1.0);
}

// ---------------------------------------------------------------------------
// Spans (pointer and length as one by-value struct)
// ---------------------------------------------------------------------------

static void testSpans() {
  // The vector overload packs the span without a separate count argument
  std::vector<double> x = {1.0, 2.0, 3.0};
  std::vector<double> y = {4.0, 5.0, 6.0};
  CHECK(bt::arrays::dotProduct(x, y) == 32.0);

  // The raw span form stays available for existing C style callers
  bt::internal::DoubleSpan rawX{x.data(), x.size()};
  bt::internal::DoubleSpan rawY{y.data(), y.size()};
  CHECK(bt::arrays::dotProduct(rawX, rawY) == 32.0);

  // The length check lives once in the Java implementation, and the wrapper turns
  // it into an exception carrying the message rather than a silent zero.
  std::vector<double> shorter = {4.0, 5.0};
  bool threwOnMismatch = false;
  try {
    bt::arrays::dotProduct(x, shorter);
  } catch (const bt::Exception& e) {
    threwOnMismatch = true;
    CHECK(std::string(e.what()).find("elements") != std::string::npos);
  }
  CHECK(threwOnMismatch);

  // Span in, vector out. The wrapper copies before the next call invalidates it.
  std::vector<double> offset = bt::arrays::addOffset(x, 10.0);
  CHECK(offset.size() == 3);
  CHECK(offset[0] == 11.0);
  CHECK(offset[1] == 12.0);
  CHECK(offset[2] == 13.0);

  // Typed spans repeat the same shape per element type
  std::vector<int32_t> intValues = {1, 2, 3};
  std::vector<int32_t> intOffset = bt::arrays::addOffsetInt32(intValues, 10);
  CHECK(intOffset.size() == 3);
  CHECK(intOffset[0] == 11);
  CHECK(intOffset[2] == 13);

  std::vector<float> floatValues = {1.5f, 2.5f, 3.5f};
  std::vector<float> floatOffset = bt::arrays::addOffsetFloat32(floatValues, 10.0f);
  CHECK(floatOffset.size() == 3);
  CHECK(floatOffset[0] == 11.5f);
  CHECK(floatOffset[2] == 13.5f);

  // Raw uint8 buffer with an explicit count, the shape an image row uses.
  // The sum is unsigned, 250 counts as 250 and not -6.
  uint8_t raw[3] = {250, 10, 5};
  CHECK(bt::arrays::sumUint8(raw, 3) == 265);

  // Byte spans carry binary data, so an embedded zero must survive the
  // roundtrip. 246 + 10 wraps to 0 in uint8.
  std::vector<uint8_t> byteValues = {1, 246, 3};
  std::vector<uint8_t> byteOffset = bt::arrays::addOffsetUint8(byteValues, 10);
  CHECK(byteOffset.size() == 3);
  CHECK(byteOffset[0] == 11);
  CHECK(byteOffset[1] == 0);
  CHECK(byteOffset[2] == 13);

  std::vector<double> sequence = bt::arrays::getSequence(5);
  CHECK(sequence.size() == 5);
  for (int i = 0; i < 5; ++i) {
    CHECK(sequence[i] == static_cast<double>(i));
  }

  // A count of zero returns an empty vector
  CHECK(bt::arrays::getSequence(0).empty());

  // 200 elements, which forces the 128 element scratch buffer to grow
  std::vector<double> longSequence = bt::arrays::getSequence(200);
  CHECK(longSequence.size() == 200);
  CHECK(longSequence[199] == 199.0);
}

// ---------------------------------------------------------------------------
// Structs (a user defined struct passed by value)
// ---------------------------------------------------------------------------

static void testStructs() {
  // Plain data with the same layout as the C struct, so aggregate initialization
  // works and the trailing 1 is the bool field in its int32 slot.
  bt::StatusInfo status{48.0, 2, 1};
  CHECK(bt::structs::totalPower(status, 2.5) == 120.0);
  CHECK(bt::structs::checkedVoltage(status) == 48.0);

  // State 1 is not the running state, so the power is zero while the voltage still reads.
  bt::StatusInfo idle{48.0, 1, 1};
  CHECK(bt::structs::totalPower(idle, 2.5) == 0.0);
  CHECK(bt::structs::checkedVoltage(idle) == 48.0);

  bt::StatusInfo disabled{48.0, 2, 0};
  CHECK(bt::structs::totalPower(disabled, 2.5) == 0.0);
  CHECK(throwsWithMessage([disabled] { bt::structs::checkedVoltage(disabled); }, "disabled"));

  bool threwWithId = false;
  try {
    bt::structs::checkedVoltage(disabled);
  } catch (const bt::Exception& e) {
    threwWithId = true;
    CHECK(std::string(e.id()) == "bindings_tests:IllegalArgumentException");
  }
  CHECK(threwWithId);

  // The remaining field types: a char code unit crossing as uint16, and typed
  // pointers passing through untouched.
  short shorts[2] = {1, 2};
  long long int longs[2] = {3, 4};
  bt::FieldTypes ft = bt::structs::fillFieldTypes(65, shorts, longs, shorts);
  CHECK(ft.unit == 65);
  CHECK(ft.shorts == reinterpret_cast<const int16_t*>(shorts));
  CHECK(ft.longs == reinterpret_cast<const int64_t*>(longs));
  CHECK(ft.context == static_cast<void*>(shorts));

  // getStatus/getCheckedStatus present the same struct the other way round, as a
  // by-value return keyed by index rather than an input struct the caller assembles.
  bt::StatusInfo s0 = bt::structs::getStatus(0);
  CHECK(s0.voltage == 48.0);
  CHECK(s0.state == 2);
  CHECK(s0.enabled == true);

  bt::StatusInfo s1 = bt::structs::getStatus(1);
  CHECK(s1.voltage == 12.5);
  CHECK(s1.state == 1);
  CHECK(s1.enabled == false);

  bt::StatusInfo checked = bt::structs::getCheckedStatus(0);
  CHECK(checked.voltage == 48.0);
  CHECK(checked.state == 2);
  CHECK(checked.enabled == true);

  // A negative index throws, same as checkedVoltage above.
  CHECK(throwsWithMessage([] { bt::structs::getCheckedStatus(-1); }, "negative"));
}

// ---------------------------------------------------------------------------
// Shapes (inheritance)
// ---------------------------------------------------------------------------

static void testShapes() {
  bt::Circle circle(2.0);
  CHECK(std::fabs(circle.area() - kPi * 4.0) < 1e-9);
  CHECK(circle.sideCount() == 0);
  CHECK(circle.getName() == "circle");

  bt::Square square(3.0);
  CHECK(square.area() == 9.0);
  CHECK(square.sideCount() == 4);
  CHECK(square.getName() == "square");

  // The important part: call through the base type. The generated subclass
  // constructor runs the cast function, so the base holds a Shape pointer and
  // the call still dispatches to the concrete Java subclass.
  bt::Shape& circleAsShape = circle;
  CHECK(std::fabs(circleAsShape.area() - kPi * 4.0) < 1e-9);
  CHECK(circleAsShape.sideCount() == 0);
  CHECK(circleAsShape.getName() == "circle");

  bt::Shape& squareAsShape = square;
  CHECK(squareAsShape.area() == 9.0);
  CHECK(squareAsShape.sideCount() == 4);
  CHECK(squareAsShape.getName() == "square");
}

// ---------------------------------------------------------------------------
// Palette (enums)
// ---------------------------------------------------------------------------

static void testPalette() {
  CHECK(bt::palette::next(bt::Color::Red) == bt::Color::Green);
  CHECK(bt::palette::next(bt::Color::Green) == bt::Color::Blue);
  CHECK(bt::palette::next(bt::Color::Blue) == bt::Color::Red);

  CHECK(bt::palette::nameOf(bt::Color::Green) == "Green");
  CHECK(bt::palette::ordinalOf(bt::Color::Blue) == 2);

  // Two enums declared in the same class, used in the same signature.
  CHECK(bt::palette::describe(bt::Color::Red, bt::Marker::Cross) == "Red Cross");

  CHECK(bt::palette::fromOrdinal(1) == bt::Color::Green);
  CHECK(bt::palette::fromOrdinal(99) == bt::Color::Red);
}

// ---------------------------------------------------------------------------
// Errors
// ---------------------------------------------------------------------------

static void testErrors() {
  // A throwing void method is promoted to an int status code, and the wrapper
  // turns a non-zero status into a bindings::tests::Exception.
  bt::errors::requireNonNegative(5);

  CHECK(throwsWithMessage([] { bt::errors::requireNonNegative(-1); }, "must not be negative"));

  // Methods with a real return type report through the same error struct, so a
  // failure is an exception here too rather than a value the caller cannot tell
  // apart from data.
  CHECK(bt::errors::divideInt32(10, 2) == 5);
  CHECK(throwsWithMessage([] { bt::errors::divideInt32(1, 0); }, "zero"));

  // The id travels alongside the message as structured data
  bool threwWithId = false;
  try {
    bt::errors::divideInt32(1, 0);
  } catch (const bt::Exception& e) {
    threwWithId = true;
    CHECK(std::string(e.id()) == "bindings_tests:ArithmeticException");
    CHECK(std::string(e.what()).find("zero") != std::string::npos);
  }
  CHECK(threwWithId);

  CHECK(bt::errors::failingFloat64(false) == 1.5);
  CHECK(throwsWithMessage([] { bt::errors::failingFloat64(true); }, "asked to fail"));

  CHECK(bt::errors::failingBool(false) == true);
  CHECK(throwsWithMessage([] { bt::errors::failingBool(true); }, "asked to fail"));

  CHECK(bt::errors::failingString(false) == "ok");
  CHECK(throwsWithMessage([] { bt::errors::failingString(true); }, "asked to fail"));

  // An exception without a message falls back to a placeholder, the id still arrives
  CHECK(throwsWithMessage([] { bt::errors::failWithoutMessage(); }, "unknown error"));
  bool threwWithoutMessage = false;
  try {
    bt::errors::failWithoutMessage();
  } catch (const bt::Exception& e) {
    threwWithoutMessage = true;
    CHECK(std::string(e.id()) == "bindings_tests:IllegalStateException");
  }
  CHECK(threwWithoutMessage);

  // neverThrows is marked Noexcept, so it has no status code and the wrapper is
  // declared noexcept.
  bt::errors::neverThrows();

  // A successful call after a failure must not report the earlier one
  CHECK(bt::errors::divideInt32(9, 3) == 3);
}

// ---------------------------------------------------------------------------
// Overloads
// ---------------------------------------------------------------------------

static void testOverloads() {
  CHECK(bt::overloads::scale(3, 4) == 12);
  CHECK(bt::overloads::scale() == 10);
  CHECK(bt::overloads::scale(3) == 30);

  CHECK(bt::overloads::greet("Bob") == "Hello, Bob");
  CHECK(bt::overloads::greet("Bob", "Hi") == "Hi, Bob");

  CHECK(bt::overloads::renamed() == 7);
  // originalName and hidden must not exist. Referencing either would fail to
  // compile, so absence is checked by the reference file test in the Java module.

  // The plain form takes a pre-summed total, same as the C ABI.
  CHECK(bt::overloads::total(6, 2) == 12);

  // The C++ overload takes a vector and sums it in the generated body. The whole
  // prologue lives in 'body' rather than 'postBody', which the generator does not emit.
  CHECK(bt::overloads::total(std::vector<int>{1, 2, 3}, 2) == 12);

  // The defaults are rendered as 'bool invert = false' and 'double scale = 1e-3'.
  CHECK(bt::overloads::gain() == 1e-3);
  CHECK(bt::overloads::gain(true) == -1e-3);
  CHECK(bt::overloads::gain(false, 2.0) == 2.0);

  // C++ gets neither postBody nor postCall, so it sees the raw zero based index.
  CHECK(bt::overloads::echoIndex(3) == 3);

  // @CLibIndex is a no-op in C++: the wrapper is zero based, same as Java.
  CHECK(bt::indices::echo(3) == 3);
  CHECK(bt::indices::countUpTo(3) == 4);
  CHECK(bt::indices::lastIndexOf(5) == 4);
  CHECK(bt::indices::offsetFrom(1, 2) == 12);
  CHECK(bt::indices::offsetFrom() == 0);

  // The default is zero based in C++, same as Java
  CHECK(bt::indices::fromRow() == 5);
  CHECK(bt::indices::fromRow(2) == 2);

  CHECK(bt::indices::echoSize(3) == static_cast<std::size_t>(3));

  // C++ is the one language where the result legitimately differs: the
  // hand-written cppBody returns 21 * multiplier and never calls the library,
  // while every other language gets 42 * multiplier, so answer(2) is 84 there.
  CHECK(bt::overloads::answer(2) == 42);
}

// ---------------------------------------------------------------------------
// Isolated (second isolate)
// ---------------------------------------------------------------------------

static void testIsolated() {
  // This state lives in a second GraalVM isolate. It does not share static
  // fields or the handle registry with the default isolate.
  bt::isolated::reset();
  bt::isolated::increment();
  bt::isolated::increment();
  bt::isolated::increment();
  CHECK(bt::isolated::getCounter() == 3);

  // The error struct is caller-owned, so the secondary isolate reports the same way
  CHECK(throwsWithMessage([] { bt::isolated::alwaysFails(); }, "secondary isolate"));
}

// ---------------------------------------------------------------------------
// Callbacks
// ---------------------------------------------------------------------------

namespace {

const int kMaxCallbacks = 16;

struct CallbackState {
  int count;
  int indices[kMaxCallbacks];
};

// The callback's pointers carry no length, so the element count is agreed on out of band.
const int kTransformCount = 3;

void onTransform(void* userData, const double* input, double* output) {
  ++static_cast<CallbackState*>(userData)->count;
  for (int i = 0; i < kTransformCount; ++i) {
    output[i] = input[i] * 2.0;
  }
}

void onIndex(void* userData, int index) {
  CallbackState* state = static_cast<CallbackState*>(userData);
  if (state->count < kMaxCallbacks) {
    state->indices[state->count] = index;
  }
  ++state->count;
}

// The plain shapes carry no userData, so their state lives at file scope and only a
// captureless lambda converts to the parameter type
CallbackState plainState;

void onPlainIndex(int index) {
  if (plainState.count < kMaxCallbacks) {
    plainState.indices[plainState.count] = index;
  }
  ++plainState.count;
}

}  // namespace

static void testCallbacks() {
  // Fires synchronously, five times, with indices 0..4. The void* is passed through unread.
  CallbackState state = {};
  bt::callbacks::invokeTimes(&onIndex, &state, 5);
  CHECK(state.count == 5);
  for (int i = 0; i < 5; ++i) {
    CHECK(state.indices[i] == i);
  }

  // A null function pointer is a no-op.
  CallbackState none = {};
  bt::callbacks::invokeTimes(nullptr, &none, 5);
  CHECK(none.count == 0);

  // A binary operator with no context at all, a captureless lambda converts to it.
  CHECK(bt::callbacks::invokeDoubleCallback([](double a, double b) { return a + b; }, 1.5, 2.25) == 3.75);

  // The method's spans get their vector overload, the callback receives them by pointer
  const std::vector<double> left = {1.0, 2.0, 3.0};
  const std::vector<double> right = {4.0, 5.0};
  CHECK(bt::callbacks::invokeDoubleSpanToScalarCallback(
            [](const bt::internal::DoubleSpan* a, const bt::internal::DoubleSpan* b) {
              double total = 0.0;
              for (size_t i = 0; i < a->length; i++) total += a->data[i];
              for (size_t i = 0; i < b->length; i++) total += b->data[i];
              return total;
            },
            left, right) == 15.0);

  // Raw pointer arguments, the shape of a solver objective. The const input still gets its
  // vector overload.
  CallbackState transformed = {};
  const double source[kTransformCount] = {1.0, 2.0, 3.0};
  double target[kTransformCount] = {};
  bt::callbacks::transformOnce(&onTransform, &transformed, source, target);
  CHECK(transformed.count == 1);
  CHECK(target[0] == 2.0 && target[1] == 4.0 && target[2] == 6.0);

  const std::vector<double> sourceVector = {4.0, 5.0, 6.0};
  bt::callbacks::transformOnce(&onTransform, &transformed, sourceVector, target);
  CHECK(transformed.count == 2);
  CHECK(target[0] == 8.0 && target[1] == 10.0 && target[2] == 12.0);

  // No context pointer, so a plain function pointer or captureless lambda
  plainState = CallbackState();
  bt::callbacks::invokePlainTimes(&onPlainIndex, 4);
  CHECK(plainState.count == 4);
  for (int i = 0; i < 4; ++i) {
    CHECK(plainState.indices[i] == i);
  }

  plainState = CallbackState();
  bt::callbacks::invokePlainTimes([](int index) { onPlainIndex(index); }, 3);
  CHECK(plainState.count == 3);
  CHECK(plainState.indices[2] == 2);

  plainState = CallbackState();
  bt::callbacks::invokePlainChecked(&onPlainIndex, 3);
  CHECK(plainState.count == 1);
  CHECK(plainState.indices[0] == 3);

  // The callback fires before the validation, so a throwing call still records it.
  plainState = CallbackState();
  CHECK(throwsWithMessage([] { bt::callbacks::invokePlainChecked(&onPlainIndex, -1); },
                          "must not be negative"));
  CHECK(plainState.count == 1);
  CHECK(plainState.indices[0] == -1);

  plainState = CallbackState();
  bt::callbacks::invokePlainOnWorkerThread(&onPlainIndex, 6);
  CHECK(plainState.count == 1);
  CHECK(plainState.indices[0] == 6);

  plainState = CallbackState();
  bt::callbacks::notifyPlainOnce([] { ++plainState.count; });
  CHECK(plainState.count == 1);
}

// ---------------------------------------------------------------------------

int main() {
  testMath();
  testCounter();
  testNaming();
  testStrings();
  testArrays();
  testSpans();
  testStructs();
  testShapes();
  testPalette();
  testErrors();
  testOverloads();
  testIsolated();
  testCallbacks();

  if (failures == 0) {
    std::printf("all C++ tests passed\n");
    return 0;
  }
  std::printf("%d C++ check(s) failed\n", failures);
  return 1;
}
