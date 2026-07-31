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

  char a[] = "a";
  char b[] = "b";
  char c[] = "c";
  char* values[] = {a, b, c};
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

  double reversed[] = {1.0, 2.0, 3.0, 4.0};
  bt::arrays::reverseFloat64(reversed, 4);
  CHECK(reversed[0] == 4.0);
  CHECK(reversed[1] == 3.0);
  CHECK(reversed[2] == 2.0);
  CHECK(reversed[3] == 1.0);
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

  CHECK(bt::palette::fromOrdinal(1) == bt::Color::Green);
  CHECK(bt::palette::fromOrdinal(99) == bt::Color::Red);
}

// ---------------------------------------------------------------------------
// Errors
// ---------------------------------------------------------------------------

static void testErrors() {
  bt::errors::clearLastError();

  // A throwing void method is promoted to an int status code, and the wrapper
  // turns a non-zero status into a bindings::tests::Exception.
  bt::errors::requireNonNegative(5);

  bool threw = false;
  try {
    bt::errors::requireNonNegative(-1);
  } catch (const bt::Exception&) {
    threw = true;
  }
  CHECK(threw);
  CHECK(bt::errors::getLastError().find("must not be negative") != std::string::npos);

  // Methods with a real return type get a type specific default on failure. The
  // generated wrapper does not check those, so nothing is thrown here and the
  // C++ result matches the raw C ABI.
  CHECK(bt::errors::divideInt32(10, 2) == 5);
  CHECK(bt::errors::divideInt32(1, 0) == 0);

  CHECK(bt::errors::failingFloat64(false) == 1.5);
  CHECK(bt::errors::failingFloat64(true) == 0.0);

  CHECK(bt::errors::failingBool(false) == true);
  CHECK(bt::errors::failingBool(true) == false);

  CHECK(bt::errors::failingString(false) == "ok");
  CHECK(bt::errors::failingString(true).empty());

  // neverThrows is marked Noexcept, so it has no status code and the wrapper is
  // declared noexcept.
  bt::errors::neverThrows();

  bt::errors::clearLastError();
  CHECK(bt::errors::getLastError().empty());
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
  // fields, the handle registry or the last error slot with the default
  // isolate, which is why it has its own last error provider.
  bt::isolated::reset();
  bt::isolated::increment();
  bt::isolated::increment();
  bt::isolated::increment();
  CHECK(bt::isolated::getCounter() == 3);

  bool threw = false;
  try {
    bt::isolated::alwaysFails();
  } catch (const bt::Exception&) {
    threw = true;
  }
  CHECK(threw);
  CHECK(bt::isolated::getLastError().find("secondary isolate") != std::string::npos);
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

void onIndex(void* userData, int index) {
  CallbackState* state = static_cast<CallbackState*>(userData);
  if (state->count < kMaxCallbacks) {
    state->indices[state->count] = index;
  }
  ++state->count;
}

}  // namespace

static void testCallbacks() {
  // Fires synchronously, five times, with indices 0..4, before returning.
  CallbackState state = {};
  bt::callbacks::invokeTimes(&onIndex, &state, 5);
  CHECK(state.count == 5);
  for (int i = 0; i < 5; ++i) {
    CHECK(state.indices[i] == i);
  }

  CallbackState once = {};
  bt::callbacks::invokeOnce(&onIndex, &once, 7);
  CHECK(once.count == 1);
  CHECK(once.indices[0] == 7);

  // A null function pointer is a no-op.
  CallbackState none = {};
  bt::callbacks::invokeTimes(nullptr, &none, 5);
  bt::callbacks::invokeOnce(nullptr, &none, 7);
  CHECK(none.count == 0);
}

// ---------------------------------------------------------------------------

int main() {
  testMath();
  testCounter();
  testStrings();
  testArrays();
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
