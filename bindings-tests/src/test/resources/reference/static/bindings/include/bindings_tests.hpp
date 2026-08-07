#pragma once

#include <cstddef>
#include <cstdint>
#include <stdexcept>
#include <string>
#include <vector>

namespace bindings {
namespace tests {

enum class CircleColor : int32_t {
  Red = 0,
  Green = 1,
  Blue = 2
};

enum class Color : int32_t {
  Red = 0,
  Green = 1,
  Blue = 2
};

enum class Marker : int32_t {
  Dot = 0,
  Cross = 1
};

// Plain data struct, passed by value. Layout changes are not ABI backwards compatible
struct StatusInfo {
  double voltage;
  int32_t state;
  int32_t enabled;
};

// ==== Forward Declarations ====
class Counter;
class Naming;
class Shape;
class Circle;
class Square;
using IndexCallbackFunction = void (*)(void* userData, int index);

namespace internal {
using CounterPtr = struct Counter_*;
using NamingPtr = struct Naming_*;
using ShapePtr = struct Shape_*;
using CirclePtr = struct Circle_*;
using SquarePtr = struct Square_*;
struct DoubleSpan {
  const double* data;
  size_t length;
};
struct ErrorInfo {
  const char* message;
  const char* id;
  int32_t code;
};
}

class Exception : public std::runtime_error {
public:
  explicit Exception(const char* message) : std::runtime_error(message) {}
  Exception(const char* message, const char* id) : std::runtime_error(message), id_(id) {}
  virtual ~Exception() noexcept = default;
  const char* id() const noexcept { return id_.c_str(); } // Kind of failure, e.g., bindings:Generic
private:
  std::string id_;
};

inline void checkError(const internal::ErrorInfo& error) {
  if (error.code != 0) {
    const char* message = error.message != nullptr ? error.message : "unknown error";
    const char* id = error.id != nullptr ? error.id : "";
    throw Exception(message, id);
  }
}

inline void checkNotNull(const void* ptr, const char* message) {
  if (!ptr) {
    throw Exception(message);
  }
}

// ==== Class Wrappers ====

/**
 * @brief A mutable counter, used to check handle creation and release.
 */
class Counter {
public:

  /**
   * @brief Creates a counter starting at the given value, or at zero by default.
   *
   * @param start 
   * @return A mutable counter, used to check handle creation and release.
   * @throw on internal errors
   */
  Counter(int start = 0);
  int getValue() const noexcept;
  void setValue(int value) noexcept;
  void increment() noexcept;
  void add(int amount) noexcept;

  /**
   * @brief Returns true once close() has run. Only observable before the handle is released.
   */
  bool isClosed() const noexcept;

  /**
   * @details 
   * Total number of counters that have been released so far in this isolate.
   * Increments exactly once per released handle, so tests can verify that the
   * generated destructor ran.
   */
  static int getClosedCount() noexcept;

  /**
   * @brief Resets the released counter, so tests do not depend on execution order.
   */
  static void resetClosedCount() noexcept;
  Counter(Counter&& from) noexcept;
  Counter& operator=(Counter&& from) noexcept;
  ~Counter() noexcept;
private:
  void cleanup() noexcept;
  internal::CounterPtr ptr_{};
};


/**
 * @brief A counter exported under a custom prefix in snake case.
 */
class Naming {
public:

  /**
   * @brief Creates an instance holding the given value, or zero by default.
   *
   * @param startValue 
   * @return A counter exported under a custom prefix in snake case.
   * @throw on internal errors
   */
  Naming(int startValue = 0);

  /**
   * @brief An instance method whose name needs more than one word to show the conversion.
   */
  int getStartValue() const noexcept;

  /**
   * @brief A static method, so the conversion is pinned for both kinds of call.
   *
   * @param first 
   * @param second 
   */
  static int addTwoNumbers(int first, int second) noexcept;
  Naming(Naming&& from) noexcept;
  Naming& operator=(Naming&& from) noexcept;
  ~Naming() noexcept;
private:
  void cleanup() noexcept;
  internal::NamingPtr ptr_{};
};


/**
 * @brief Base class for the shape hierarchy.
 */
class Shape {
public:
  std::string getName() const noexcept;
  void setName(const char* name) noexcept;
  void setName(const std::string& name) noexcept;

  /**
   * @brief Area of the shape. Dispatches to the concrete subclass.
   */
  double area() const noexcept;

  /**
   * @brief Number of sides. Zero for shapes without straight edges.
   */
  int sideCount() const noexcept;
  Shape(Shape&& from) noexcept;
  Shape& operator=(Shape&& from) noexcept;
  virtual ~Shape() noexcept;
protected:
  explicit Shape(internal::ShapePtr cPointer) noexcept : ptr_(cPointer) {}
private:
  void cleanup() noexcept;
  internal::ShapePtr ptr_{};
};


/**
 * @brief A circle. Inherits name and area from Shape.
 */
class Circle : public Shape {
public:
  Circle(double radius);
  double getRadius() const noexcept;

  /**
   * @brief Returns the fill color, which is always Blue until setters exist.
   *
   * @return Fill color of a circle.
   */
  CircleColor getColor() const noexcept;
  double area() const noexcept;
  int sideCount() const noexcept;
  Circle(Circle&& from) noexcept;
  Circle& operator=(Circle&& from) noexcept;
private:
  explicit Circle(internal::CirclePtr cPointer) noexcept;
  static internal::ShapePtr getShapePointer(internal::CirclePtr cPointer) noexcept;
  internal::CirclePtr ptr_{};
};


/**
 * @brief A square. Inherits name and area from Shape.
 */
class Square : public Shape {
public:
  Square(double side);
  double getSide() const noexcept;
  double area() const noexcept;
  int sideCount() const noexcept;
  Square(Square&& from) noexcept;
  Square& operator=(Square&& from) noexcept;
private:
  explicit Square(internal::SquarePtr cPointer) noexcept;
  static internal::ShapePtr getShapePointer(internal::SquarePtr cPointer) noexcept;
  internal::SquarePtr ptr_{};
};


/**
 * @brief Bulk array operations over caller-owned buffers.
 */
namespace arrays {

/**
 * @details 
 * A const input buffer. Besides the const in the C header, this is what makes the
 * C++ generator add a std::vector<double> overload next to the raw pointer one.
 *
 * @param values 
 * @param count 
 */
double sumFloat64(const double* values, size_t count) noexcept;
double sumFloat64(const std::vector<double>& values, size_t count) noexcept;

/**
 * @brief Const on a non-double pointer, which gets the const but no vector overload.
 *
 * @param values 
 * @param count 
 */
int sumInt32(const int* values, size_t count) noexcept;

/**
 * @brief Writes value into every element of target. Checks output buffer handling.
 *
 * @param target 
 * @param count 
 * @param value 
 */
void fillFloat64(double* target, size_t count, double value) noexcept;

/**
 * @details 
 * Writes the running total of source into target. Both buffers must hold at
 * least count elements. Passing the same buffer for both is supported. One const
 * and one mutable pointer in the same signature, so only the source picks up the
 * vector overload.
 *
 * @param source 
 * @param target 
 * @param count 
 */
void cumulativeSum(const double* source, double* target, size_t count) noexcept;
void cumulativeSum(const std::vector<double>& source, double* target, size_t count) noexcept;

/**
 * @brief Reverses the buffer in place.
 *
 * @param values 
 * @param count 
 */
void reverseFloat64(double* values, size_t count) noexcept;

/**
 * @brief Dot product of two spans. The lengths ride along in the spans, so a mismatch is checked once here rather than in every language wrapper.
 *
 * @param x 
 * @param y 
 * @throw mismatched lengths
 */
double dotProduct(internal::DoubleSpan x, internal::DoubleSpan y);
double dotProduct(const std::vector<double>& x, const std::vector<double>& y);

/**
 * @brief Returns a new array with the offset added to every element. Checks a span in and an array out.
 *
 * @param values 
 * @param offset 
 */
std::vector<double> addOffset(internal::DoubleSpan values, double offset) noexcept;
std::vector<double> addOffset(const std::vector<double>& values, double offset) noexcept;

/**
 * @details 
 * Returns 0, 1, .. count-1. A count of zero returns an empty array, and a count
 * over 128 forces the per-thread scratch buffer to grow, mirroring Strings.repeat.
 *
 * @param count 
 */
std::vector<double> getSequence(size_t count) noexcept;
} // namespace arrays


/**
 * @brief Synchronous callbacks into caller-provided function pointers.
 */
namespace callbacks {

/**
 * @details 
 * Invokes the callback exactly 'times' times with indices 0 to times-1, then
 * returns. A null function pointer is ignored, and a non-positive count does
 * nothing.
 *
 * @param func 
 * @param userData 
 * @param times 
 */
void invokeTimes(IndexCallbackFunction func, void* userData, size_t times) noexcept;

/**
 * @brief Invokes the callback once with the given index. Returns after it completes.
 *
 * @param func 
 * @param userData 
 * @param index 
 */
void invokeOnce(IndexCallbackFunction func, void* userData, int index) noexcept;
} // namespace callbacks


/**
 * @brief Deliberate failures, used to check error propagation.
 */
namespace errors {

/**
 * @brief Throws when value is negative, otherwise does nothing.
 *
 * @param value 
 * @throw value is negative
 */
void requireNonNegative(int value);

/**
 * @brief Integer division. Returns 0 through the ABI when it throws.
 *
 * @param a 
 * @param b 
 * @throw divisor is zero
 */
int divideInt32(int a, int b);

/**
 * @brief Returns 0.0 through the ABI when it throws.
 *
 * @param shouldFail 
 * @throw shouldFail is true
 */
double failingFloat64(bool shouldFail);

/**
 * @brief Returns false through the ABI when it throws.
 *
 * @param shouldFail 
 * @throw shouldFail is true
 */
bool failingBool(bool shouldFail);

/**
 * @brief Returns a null pointer through the ABI when it throws.
 *
 * @param shouldFail 
 * @throw shouldFail is true
 */
std::string failingString(bool shouldFail);

/**
 * @brief Throws an exception that carries no message.
 * @throw always
 */
int failWithoutMessage();

/**
 * @brief Marked noexcept, so no status code is generated for it.
 */
void neverThrows() noexcept;
} // namespace errors


/**
 * @brief Index conversion for languages that count from one.
 */
namespace indices {

/**
 * @details 
 * Returns the index unchanged. Both directions are marked, so a one-based caller
 * passes a value and gets the same value back.
 *
 * @param index 
 */
int echo(int index) noexcept;

/**
 * @brief Index in, plain number out, so only the input converts.
 *
 * @param index 
 */
int countUpTo(int index) noexcept;

/**
 * @brief Plain number in, index out, so only the output converts.
 *
 * @param count 
 */
int lastIndexOf(int count) noexcept;

/**
 * @details 
 * Two indices that default to the first element. The default is written zero based
 * here and each one-based wrapper shifts it, so an omitted argument converts the
 * same way a supplied one does.
 *
 * @param row 
 * @param col 
 */
int offsetFrom(int row = 0, int col = 0) noexcept;

/**
 * @details 
 * An index whose default is not zero, which is what catches a default that gets
 * converted twice. Omitting the argument has to reach Java as 5 in every language.
 *
 * @param row 
 */
int fromRow(int row = 5) noexcept;

/**
 * @brief A size_t index, so the size bridge and the index conversion have to compose.
 *
 * @param index 
 */
size_t echoSize(size_t index) noexcept;
} // namespace indices


/**
 * @brief State confined to a second isolate.
 */
namespace isolated {
void increment() noexcept;
int getCounter() noexcept;
void reset() noexcept;

/**
 * @brief Always throws, so the secondary isolate's error path is exercised too.
 * @throw always
 */
void alwaysFails();
} // namespace isolated


/**
 * @brief Stateless arithmetic used to check primitive type mapping.
 */
namespace math {
bool negate(bool value) noexcept;

/**
 * @brief Returns the next UTF-16 code unit. 'A' becomes 'B'.
 *
 * @param value 
 */
unsigned short nextChar(unsigned short value) noexcept;
char addInt8(char a, char b) noexcept;
short addInt16(short a, short b) noexcept;
int addInt32(int a, int b) noexcept;

/**
 * @brief Uses values above 2^32 to catch platforms that truncate long to 32 bit.
 *
 * @param a 
 * @param b 
 */
long long addInt64(long long a, long long b) noexcept;
float addFloat32(float a, float b) noexcept;
double addFloat64(double a, double b) noexcept;

/**
 * @brief size_t in and size_t out. Bridged through int64 on the Java side.
 *
 * @param a 
 * @param b 
 */
size_t addSize(size_t a, size_t b) noexcept;

/**
 * @brief Takes a size_t that is capped to a 32 bit Java int on the way in.
 *
 * @param value 
 */
int sizeToInt32(size_t value) noexcept;

/**
 * @brief Returns Integer.MAX_VALUE, so callers can check the 31 bit cap.
 */
int maxInt32() noexcept;

/**
 * @brief Returns Long.MAX_VALUE as a size_t, so callers can check the 63 bit cap.
 */
size_t maxSize() noexcept;
} // namespace math


/**
 * @brief Default arguments and language specific convenience overloads.
 */
namespace overloads {

/**
 * @brief Both parameters have defaults, so wrappers may call this with no arguments.
 *
 * @param value 
 * @param factor 
 */
int scale(int value = 1, int factor = 10) noexcept;

/**
 * @brief A trailing default only, which is the common case.
 *
 * @param name 
 * @param greeting 
 */
std::string greet(const char* name, const char* greeting = "Hello") noexcept;
std::string greet(const std::string& name, const std::string& greeting = "Hello") noexcept;

/**
 * @brief Takes a pre-summed total. Each language gets a wrapper that sums a collection for you.
 *
 * @param sum 
 * @param factor 
 */
int total(int sum, int factor) noexcept;
int total(const std::vector<int>& values, int factor) noexcept;

/**
 * @details 
 * A boolean default and a double default in scientific notation. Each wrapper has
 * to render both in its own syntax, which is where they tend to go wrong.
 *
 * @param invert 
 * @param scale 
 */
double gain(bool invert = false, double scale = 1e-3) noexcept;

/**
 * @details 
 * Returns the index unchanged. MATLAB converts to zero based indexing on the way
 * in and back to one based on the way out, so it sees the value it passed in.
 * Python clamps negatives instead, which matches C for every valid index.
 *
 * @param index 
 */
int echoIndex(int index) noexcept;

/**
 * @details 
 * The C++ wrapper body is hand written and never calls into the library, so
 * this is the one method where the C++ result may legitimately differ from
 * the others. Kept deliberately obvious.
 *
 * @param multiplier 
 */
int answer(int multiplier) noexcept;

/**
 * @brief Declared as originalName in Java but exported as renamed.
 */
int renamed() noexcept;
} // namespace overloads


/**
 * @brief Enum round trips.
 */
namespace palette {

/**
 * @brief Cycles Red to Green to Blue and back to Red.
 *
 * @param color 
 * @return A named color. Values are ordinals, starting at zero.
 */
Color next(Color color) noexcept;
std::string nameOf(Color color) noexcept;
int ordinalOf(Color color) noexcept;

/**
 * @brief Takes both enums at once, so the two generated types have to coexist in one signature.
 *
 * @param color 
 * @param marker 
 */
std::string describe(Color color, Marker marker) noexcept;

/**
 * @brief Returns the enum with the given ordinal, or Red if it is out of range.
 *
 * @param ordinal 
 * @return A named color. Values are ordinals, starting at zero.
 */
Color fromOrdinal(int ordinal) noexcept;
} // namespace palette


/**
 * @brief String conversion, including non-ASCII round trips.
 */
namespace strings {

/**
 * @brief Returns the input unchanged. Round trips UTF-8 through Java's UTF-16.
 *
 * @param value 
 */
std::string echo(const char* value) noexcept;
std::string echo(const std::string& value) noexcept;
std::string concat(const char* a, const char* b) noexcept;
std::string concat(const std::string& a, const std::string& b) noexcept;

/**
 * @brief Length in UTF-8 bytes, which differs from the Java String length for non-ASCII input.
 *
 * @param value 
 */
size_t utf8Length(const char* value) noexcept;
size_t utf8Length(const std::string& value) noexcept;

/**
 * @brief Number of UTF-16 code units, i.e. the Java String length.
 *
 * @param value 
 */
size_t utf16Length(const char* value) noexcept;
size_t utf16Length(const std::string& value) noexcept;

/**
 * @brief Returns an empty string, which must not be confused with a null pointer.
 */
std::string empty() noexcept;

/**
 * @brief Returns a null pointer rather than an empty string.
 */
std::string nullString() noexcept;

/**
 * @brief Joins a char* array with a separator. Checks const char** parameter handling.
 *
 * @param values 
 * @param count number of entries in values
 * @param separator 
 */
std::string join(const char** values, size_t count, const char* separator) noexcept;
std::string join(const char** values, size_t count, const std::string& separator) noexcept;

/**
 * @brief Returns a string longer than the initial scratch buffer, forcing it to grow.
 *
 * @param value 
 * @param times 
 */
std::string repeat(const char* value, size_t times) noexcept;
std::string repeat(const std::string& value, size_t times) noexcept;
} // namespace strings


/**
 * @brief Reads fields out of a caller-owned struct.
 */
namespace structs {

/**
 * @details 
 * Power draw in watts for the given current. Returns zero unless the actuator is
 * enabled and in the running state (2), so all three fields decide the result.
 *
 * @param status 
 * @param currentAmps 
 */
double totalPower(StatusInfo status, double currentAmps) noexcept;

/**
 * @brief Bus voltage in volts. Returns 0.0 through the ABI when it throws.
 *
 * @param status 
 * @throw the actuator is disabled
 */
double checkedVoltage(StatusInfo status);

/**
 * @details 
 * Fills the out parameter with a canned reading for the given index: index 0
 * reads (48.0, running, enabled), every other index reads (12.5, 1, disabled).
 *
 * @param index 
 * @return Status reading for the given index.
 */
StatusInfo getStatus(int index) noexcept;

/**
 * @brief Same readings as getStatus. Every field of the out parameter reads as zero through the ABI when it throws.
 *
 * @param index 
 * @throw the index is negative
 */
StatusInfo getCheckedStatus(int index);
} // namespace structs

// ==== C Library Functions ====
extern "C" {
  internal::CounterPtr bindings_tests_Counter_create(internal::ErrorInfo*, int);
  int bindings_tests_Counter_getValue(internal::CounterPtr);
  void bindings_tests_Counter_setValue(internal::CounterPtr, int);
  void bindings_tests_Counter_increment(internal::CounterPtr);
  void bindings_tests_Counter_add(internal::CounterPtr, int);
  int bindings_tests_Counter_isClosed(internal::CounterPtr);
  int bindings_tests_Counter_getClosedCount();
  void bindings_tests_Counter_resetClosedCount();
  void bindings_tests_Counter_release(internal::CounterPtr);
  internal::NamingPtr custom_prefix_Naming_create(internal::ErrorInfo*, int);
  int custom_prefix_Naming_get_start_value(internal::NamingPtr);
  int custom_prefix_Naming_add_two_numbers(int, int);
  void custom_prefix_Naming_release(internal::NamingPtr);
  const char* bindings_tests_Shape_getName(internal::ShapePtr);
  void bindings_tests_Shape_setName(internal::ShapePtr, const char*);
  double bindings_tests_Shape_area(internal::ShapePtr);
  int bindings_tests_Shape_sideCount(internal::ShapePtr);
  void bindings_tests_Shape_release(internal::ShapePtr);
  internal::CirclePtr bindings_tests_Circle_create(internal::ErrorInfo*, double);
  double bindings_tests_Circle_getRadius(internal::CirclePtr);
  CircleColor bindings_tests_Circle_getColor(internal::CirclePtr);
  double bindings_tests_Circle_area(internal::CirclePtr);
  int bindings_tests_Circle_sideCount(internal::CirclePtr);
  internal::ShapePtr bindings_tests_Circle_to_Shape(internal::CirclePtr);
  internal::SquarePtr bindings_tests_Square_create(internal::ErrorInfo*, double);
  double bindings_tests_Square_getSide(internal::SquarePtr);
  double bindings_tests_Square_area(internal::SquarePtr);
  int bindings_tests_Square_sideCount(internal::SquarePtr);
  internal::ShapePtr bindings_tests_Square_to_Shape(internal::SquarePtr);
  double bindings_tests_Arrays_sumFloat64(const double*, size_t);
  int bindings_tests_Arrays_sumInt32(const int*, size_t);
  void bindings_tests_Arrays_fillFloat64(double*, size_t, double);
  void bindings_tests_Arrays_cumulativeSum(const double*, double*, size_t);
  void bindings_tests_Arrays_reverseFloat64(double*, size_t);
  double bindings_tests_Arrays_dotProduct(internal::ErrorInfo*, internal::DoubleSpan, internal::DoubleSpan);
  internal::DoubleSpan bindings_tests_Arrays_addOffset(internal::DoubleSpan, double);
  internal::DoubleSpan bindings_tests_Arrays_getSequence(size_t);
  void bindings_tests_Callbacks_invokeTimes(IndexCallbackFunction, void*, size_t);
  void bindings_tests_Callbacks_invokeOnce(IndexCallbackFunction, void*, int);
  void bindings_tests_Errors_requireNonNegative(internal::ErrorInfo*, int);
  int bindings_tests_Errors_divideInt32(internal::ErrorInfo*, int, int);
  double bindings_tests_Errors_failingFloat64(internal::ErrorInfo*, int);
  int bindings_tests_Errors_failingBool(internal::ErrorInfo*, int);
  const char* bindings_tests_Errors_failingString(internal::ErrorInfo*, int);
  int bindings_tests_Errors_failWithoutMessage(internal::ErrorInfo*);
  void bindings_tests_Errors_neverThrows();
  int bindings_tests_Indices_echo(int);
  int bindings_tests_Indices_countUpTo(int);
  int bindings_tests_Indices_lastIndexOf(int);
  int bindings_tests_Indices_offsetFrom(int, int);
  int bindings_tests_Indices_fromRow(int);
  size_t bindings_tests_Indices_echoSize(size_t);
  void bindings_tests_Isolated_increment();
  int bindings_tests_Isolated_getCounter();
  void bindings_tests_Isolated_reset();
  void bindings_tests_Isolated_alwaysFails(internal::ErrorInfo*);
  int bindings_tests_Math_negate(int);
  unsigned short bindings_tests_Math_nextChar(unsigned short);
  char bindings_tests_Math_addInt8(char, char);
  short bindings_tests_Math_addInt16(short, short);
  int bindings_tests_Math_addInt32(int, int);
  long long bindings_tests_Math_addInt64(long long, long long);
  float bindings_tests_Math_addFloat32(float, float);
  double bindings_tests_Math_addFloat64(double, double);
  size_t bindings_tests_Math_addSize(size_t, size_t);
  int bindings_tests_Math_sizeToInt32(size_t);
  int bindings_tests_Math_maxInt32();
  size_t bindings_tests_Math_maxSize();
  int bindings_tests_Overloads_scale(int, int);
  const char* bindings_tests_Overloads_greet(const char*, const char*);
  int bindings_tests_Overloads_total(int, int);
  double bindings_tests_Overloads_gain(int, double);
  int bindings_tests_Overloads_echoIndex(int);
  int bindings_tests_Overloads_answer(int);
  int bindings_tests_Overloads_renamed();
  Color bindings_tests_Palette_next(Color);
  const char* bindings_tests_Palette_nameOf(Color);
  int bindings_tests_Palette_ordinalOf(Color);
  const char* bindings_tests_Palette_describe(Color, Marker);
  Color bindings_tests_Palette_fromOrdinal(int);
  const char* bindings_tests_Strings_echo(const char*);
  const char* bindings_tests_Strings_concat(const char*, const char*);
  size_t bindings_tests_Strings_utf8Length(const char*);
  size_t bindings_tests_Strings_utf16Length(const char*);
  const char* bindings_tests_Strings_empty();
  const char* bindings_tests_Strings_nullString();
  const char* bindings_tests_Strings_join(const char**, size_t, const char*);
  const char* bindings_tests_Strings_repeat(const char*, size_t);
  double bindings_tests_Structs_totalPower(StatusInfo, double);
  double bindings_tests_Structs_checkedVoltage(internal::ErrorInfo*, StatusInfo);
  StatusInfo bindings_tests_Structs_getStatus(int);
  StatusInfo bindings_tests_Structs_getCheckedStatus(internal::ErrorInfo*, int);
  void bindings_tests_getLibraryVersion(int* major, int* minor, int* patch, int* build);
}

// ==== C++ Implementations ====
// Counter
inline Counter::Counter(int start) {
  internal::ErrorInfo error_ = {};
  ptr_ = bindings_tests_Counter_create(&error_, start);
  checkError(error_);
  checkNotNull(ptr_, "Could not create Counter");
}
inline int Counter::getValue() const noexcept {
  return bindings_tests_Counter_getValue(ptr_);
}
inline void Counter::setValue(int value) noexcept {
  bindings_tests_Counter_setValue(ptr_, value);
}
inline void Counter::increment() noexcept {
  bindings_tests_Counter_increment(ptr_);
}
inline void Counter::add(int amount) noexcept {
  bindings_tests_Counter_add(ptr_, amount);
}
inline bool Counter::isClosed() const noexcept {
  return bindings_tests_Counter_isClosed(ptr_);
}
inline int Counter::getClosedCount() noexcept {
  return bindings_tests_Counter_getClosedCount();
}
inline void Counter::resetClosedCount() noexcept {
  bindings_tests_Counter_resetClosedCount();
}
inline void Counter::cleanup() noexcept {
  if (ptr_ != nullptr) {
    bindings_tests_Counter_release(ptr_);
  }
}
inline Counter::Counter(Counter&& from) noexcept : ptr_(from.ptr_) {
  from.ptr_ = nullptr;
};
inline Counter& Counter::operator=(Counter&& from) noexcept {
  cleanup();
  ptr_ = from.ptr_;
  from.ptr_ = nullptr;
  return *this;
};
inline Counter::~Counter() noexcept {
  cleanup();
}

// Naming
inline Naming::Naming(int startValue) {
  internal::ErrorInfo error_ = {};
  ptr_ = custom_prefix_Naming_create(&error_, startValue);
  checkError(error_);
  checkNotNull(ptr_, "Could not create Naming");
}
inline int Naming::getStartValue() const noexcept {
  return custom_prefix_Naming_get_start_value(ptr_);
}
inline int Naming::addTwoNumbers(int first, int second) noexcept {
  return custom_prefix_Naming_add_two_numbers(first, second);
}
inline void Naming::cleanup() noexcept {
  if (ptr_ != nullptr) {
    custom_prefix_Naming_release(ptr_);
  }
}
inline Naming::Naming(Naming&& from) noexcept : ptr_(from.ptr_) {
  from.ptr_ = nullptr;
};
inline Naming& Naming::operator=(Naming&& from) noexcept {
  cleanup();
  ptr_ = from.ptr_;
  from.ptr_ = nullptr;
  return *this;
};
inline Naming::~Naming() noexcept {
  cleanup();
}

// Shape
inline std::string Shape::getName() const noexcept {
  auto ptr = bindings_tests_Shape_getName(ptr_);
  return !ptr ? std::string() : std::string(ptr); // copy utf8
}
inline void Shape::setName(const char* name) noexcept {
  bindings_tests_Shape_setName(ptr_, name);
}
inline void Shape::setName(const std::string& name) noexcept {
  setName(name.c_str());
}
inline double Shape::area() const noexcept {
  return bindings_tests_Shape_area(ptr_);
}
inline int Shape::sideCount() const noexcept {
  return bindings_tests_Shape_sideCount(ptr_);
}
inline void Shape::cleanup() noexcept {
  if (ptr_ != nullptr) {
    bindings_tests_Shape_release(ptr_);
  }
}
inline Shape::Shape(Shape&& from) noexcept : ptr_(from.ptr_) {
  from.ptr_ = nullptr;
};
inline Shape& Shape::operator=(Shape&& from) noexcept {
  cleanup();
  ptr_ = from.ptr_;
  from.ptr_ = nullptr;
  return *this;
};
inline Shape::~Shape() noexcept {
  cleanup();
}

// Circle
namespace internal {
// The null check happens here because the base class gets initialized before the constructor body runs
inline internal::CirclePtr createCirclePointer(double radius) {
  internal::ErrorInfo error_ = {};
  auto ptr = bindings_tests_Circle_create(&error_, radius);
  checkError(error_);
  checkNotNull(ptr, "Could not create Circle");
  return ptr;
}
}
inline Circle::Circle(double radius) : Circle(internal::createCirclePointer(radius)) {}
inline double Circle::getRadius() const noexcept {
  return bindings_tests_Circle_getRadius(ptr_);
}
inline CircleColor Circle::getColor() const noexcept {
  return bindings_tests_Circle_getColor(ptr_);
}
inline double Circle::area() const noexcept {
  return bindings_tests_Circle_area(ptr_);
}
inline int Circle::sideCount() const noexcept {
  return bindings_tests_Circle_sideCount(ptr_);
}
inline internal::ShapePtr Circle::getShapePointer(internal::CirclePtr cPointer) noexcept {
  return bindings_tests_Circle_to_Shape(cPointer);
}
inline Circle::Circle(internal::CirclePtr cPointer) noexcept : Shape(getShapePointer(cPointer)), ptr_(cPointer) {}
inline Circle::Circle(Circle&& from) noexcept : Shape(std::move(from)), ptr_(from.ptr_) {
  from.ptr_ = nullptr;
};
inline Circle& Circle::operator=(Circle&& from) noexcept {
  Shape::operator=(std::move(from));
  ptr_ = from.ptr_;
  from.ptr_ = nullptr;
  return *this;
};

// Square
namespace internal {
// The null check happens here because the base class gets initialized before the constructor body runs
inline internal::SquarePtr createSquarePointer(double side) {
  internal::ErrorInfo error_ = {};
  auto ptr = bindings_tests_Square_create(&error_, side);
  checkError(error_);
  checkNotNull(ptr, "Could not create Square");
  return ptr;
}
}
inline Square::Square(double side) : Square(internal::createSquarePointer(side)) {}
inline double Square::getSide() const noexcept {
  return bindings_tests_Square_getSide(ptr_);
}
inline double Square::area() const noexcept {
  return bindings_tests_Square_area(ptr_);
}
inline int Square::sideCount() const noexcept {
  return bindings_tests_Square_sideCount(ptr_);
}
inline internal::ShapePtr Square::getShapePointer(internal::SquarePtr cPointer) noexcept {
  return bindings_tests_Square_to_Shape(cPointer);
}
inline Square::Square(internal::SquarePtr cPointer) noexcept : Shape(getShapePointer(cPointer)), ptr_(cPointer) {}
inline Square::Square(Square&& from) noexcept : Shape(std::move(from)), ptr_(from.ptr_) {
  from.ptr_ = nullptr;
};
inline Square& Square::operator=(Square&& from) noexcept {
  Shape::operator=(std::move(from));
  ptr_ = from.ptr_;
  from.ptr_ = nullptr;
  return *this;
};

// Arrays
inline double arrays::sumFloat64(const double* values, size_t count) noexcept {
  return bindings_tests_Arrays_sumFloat64(values, count);
}
inline double arrays::sumFloat64(const std::vector<double>& values, size_t count) noexcept {
  return sumFloat64(values.data(), count);
}
inline int arrays::sumInt32(const int* values, size_t count) noexcept {
  return bindings_tests_Arrays_sumInt32(values, count);
}
inline void arrays::fillFloat64(double* target, size_t count, double value) noexcept {
  bindings_tests_Arrays_fillFloat64(target, count, value);
}
inline void arrays::cumulativeSum(const double* source, double* target, size_t count) noexcept {
  bindings_tests_Arrays_cumulativeSum(source, target, count);
}
inline void arrays::cumulativeSum(const std::vector<double>& source, double* target, size_t count) noexcept {
  cumulativeSum(source.data(), target, count);
}
inline void arrays::reverseFloat64(double* values, size_t count) noexcept {
  bindings_tests_Arrays_reverseFloat64(values, count);
}
inline double arrays::dotProduct(internal::DoubleSpan x, internal::DoubleSpan y) {
  internal::ErrorInfo error_ = {};
  auto result_ = bindings_tests_Arrays_dotProduct(&error_, x, y);
  checkError(error_);
  return result_;
}
inline double arrays::dotProduct(const std::vector<double>& x, const std::vector<double>& y) {
  return dotProduct({x.data(), x.size()}, {y.data(), y.size()});
}
inline std::vector<double> arrays::addOffset(internal::DoubleSpan values, double offset) noexcept {
  auto struct_ = bindings_tests_Arrays_addOffset(values, offset);
  return std::vector<double>(struct_.data, struct_.data + struct_.length); // copy before the next call invalidates it
}
inline std::vector<double> arrays::addOffset(const std::vector<double>& values, double offset) noexcept {
  return addOffset({values.data(), values.size()}, offset);
}
inline std::vector<double> arrays::getSequence(size_t count) noexcept {
  auto struct_ = bindings_tests_Arrays_getSequence(count);
  return std::vector<double>(struct_.data, struct_.data + struct_.length); // copy before the next call invalidates it
}

// Callbacks
inline void callbacks::invokeTimes(IndexCallbackFunction func, void* userData, size_t times) noexcept {
  bindings_tests_Callbacks_invokeTimes(func, userData, times);
}
inline void callbacks::invokeOnce(IndexCallbackFunction func, void* userData, int index) noexcept {
  bindings_tests_Callbacks_invokeOnce(func, userData, index);
}

// Errors
inline void errors::requireNonNegative(int value) {
  internal::ErrorInfo error_ = {};
  bindings_tests_Errors_requireNonNegative(&error_, value);
  checkError(error_);
}
inline int errors::divideInt32(int a, int b) {
  internal::ErrorInfo error_ = {};
  auto result_ = bindings_tests_Errors_divideInt32(&error_, a, b);
  checkError(error_);
  return result_;
}
inline double errors::failingFloat64(bool shouldFail) {
  internal::ErrorInfo error_ = {};
  auto result_ = bindings_tests_Errors_failingFloat64(&error_, shouldFail);
  checkError(error_);
  return result_;
}
inline bool errors::failingBool(bool shouldFail) {
  internal::ErrorInfo error_ = {};
  auto result_ = bindings_tests_Errors_failingBool(&error_, shouldFail);
  checkError(error_);
  return result_;
}
inline std::string errors::failingString(bool shouldFail) {
  internal::ErrorInfo error_ = {};
  auto ptr = bindings_tests_Errors_failingString(&error_, shouldFail);
  checkError(error_);
  return !ptr ? std::string() : std::string(ptr); // copy utf8
}
inline int errors::failWithoutMessage() {
  internal::ErrorInfo error_ = {};
  auto result_ = bindings_tests_Errors_failWithoutMessage(&error_);
  checkError(error_);
  return result_;
}
inline void errors::neverThrows() noexcept {
  bindings_tests_Errors_neverThrows();
}

// Indices
inline int indices::echo(int index) noexcept {
  return bindings_tests_Indices_echo(index);
}
inline int indices::countUpTo(int index) noexcept {
  return bindings_tests_Indices_countUpTo(index);
}
inline int indices::lastIndexOf(int count) noexcept {
  return bindings_tests_Indices_lastIndexOf(count);
}
inline int indices::offsetFrom(int row, int col) noexcept {
  return bindings_tests_Indices_offsetFrom(row, col);
}
inline int indices::fromRow(int row) noexcept {
  return bindings_tests_Indices_fromRow(row);
}
inline size_t indices::echoSize(size_t index) noexcept {
  return bindings_tests_Indices_echoSize(index);
}

// Isolated
inline void isolated::increment() noexcept {
  bindings_tests_Isolated_increment();
}
inline int isolated::getCounter() noexcept {
  return bindings_tests_Isolated_getCounter();
}
inline void isolated::reset() noexcept {
  bindings_tests_Isolated_reset();
}
inline void isolated::alwaysFails() {
  internal::ErrorInfo error_ = {};
  bindings_tests_Isolated_alwaysFails(&error_);
  checkError(error_);
}

// Math
inline bool math::negate(bool value) noexcept {
  return bindings_tests_Math_negate(value);
}
inline unsigned short math::nextChar(unsigned short value) noexcept {
  return bindings_tests_Math_nextChar(value);
}
inline char math::addInt8(char a, char b) noexcept {
  return bindings_tests_Math_addInt8(a, b);
}
inline short math::addInt16(short a, short b) noexcept {
  return bindings_tests_Math_addInt16(a, b);
}
inline int math::addInt32(int a, int b) noexcept {
  return bindings_tests_Math_addInt32(a, b);
}
inline long long math::addInt64(long long a, long long b) noexcept {
  return bindings_tests_Math_addInt64(a, b);
}
inline float math::addFloat32(float a, float b) noexcept {
  return bindings_tests_Math_addFloat32(a, b);
}
inline double math::addFloat64(double a, double b) noexcept {
  return bindings_tests_Math_addFloat64(a, b);
}
inline size_t math::addSize(size_t a, size_t b) noexcept {
  return bindings_tests_Math_addSize(a, b);
}
inline int math::sizeToInt32(size_t value) noexcept {
  return bindings_tests_Math_sizeToInt32(value);
}
inline int math::maxInt32() noexcept {
  return bindings_tests_Math_maxInt32();
}
inline size_t math::maxSize() noexcept {
  return bindings_tests_Math_maxSize();
}

// Overloads
inline int overloads::scale(int value, int factor) noexcept {
  return bindings_tests_Overloads_scale(value, factor);
}
inline std::string overloads::greet(const char* name, const char* greeting) noexcept {
  auto ptr = bindings_tests_Overloads_greet(name, greeting);
  return !ptr ? std::string() : std::string(ptr); // copy utf8
}
inline std::string overloads::greet(const std::string& name, const std::string& greeting) noexcept {
  return greet(name.c_str(), greeting.c_str());
}
inline int overloads::total(int sum, int factor) noexcept {
  return bindings_tests_Overloads_total(sum, factor);
}
inline int overloads::total(const std::vector<int>& values, int factor) noexcept {
  int sum = 0; for (int v : values) { sum += v; }return total(sum, factor);
}
inline double overloads::gain(bool invert, double scale) noexcept {
  return bindings_tests_Overloads_gain(invert, scale);
}
inline int overloads::echoIndex(int index) noexcept {
  return bindings_tests_Overloads_echoIndex(index);
}
inline int overloads::answer(int multiplier) noexcept {
return 21 * multiplier;
  return bindings_tests_Overloads_answer(multiplier);
}
inline int overloads::renamed() noexcept {
  return bindings_tests_Overloads_renamed();
}

// Palette
inline Color palette::next(Color color) noexcept {
  return bindings_tests_Palette_next(color);
}
inline std::string palette::nameOf(Color color) noexcept {
  auto ptr = bindings_tests_Palette_nameOf(color);
  return !ptr ? std::string() : std::string(ptr); // copy utf8
}
inline int palette::ordinalOf(Color color) noexcept {
  return bindings_tests_Palette_ordinalOf(color);
}
inline std::string palette::describe(Color color, Marker marker) noexcept {
  auto ptr = bindings_tests_Palette_describe(color, marker);
  return !ptr ? std::string() : std::string(ptr); // copy utf8
}
inline Color palette::fromOrdinal(int ordinal) noexcept {
  return bindings_tests_Palette_fromOrdinal(ordinal);
}

// Strings
inline std::string strings::echo(const char* value) noexcept {
  auto ptr = bindings_tests_Strings_echo(value);
  return !ptr ? std::string() : std::string(ptr); // copy utf8
}
inline std::string strings::echo(const std::string& value) noexcept {
  return echo(value.c_str());
}
inline std::string strings::concat(const char* a, const char* b) noexcept {
  auto ptr = bindings_tests_Strings_concat(a, b);
  return !ptr ? std::string() : std::string(ptr); // copy utf8
}
inline std::string strings::concat(const std::string& a, const std::string& b) noexcept {
  return concat(a.c_str(), b.c_str());
}
inline size_t strings::utf8Length(const char* value) noexcept {
  return bindings_tests_Strings_utf8Length(value);
}
inline size_t strings::utf8Length(const std::string& value) noexcept {
  return utf8Length(value.c_str());
}
inline size_t strings::utf16Length(const char* value) noexcept {
  return bindings_tests_Strings_utf16Length(value);
}
inline size_t strings::utf16Length(const std::string& value) noexcept {
  return utf16Length(value.c_str());
}
inline std::string strings::empty() noexcept {
  auto ptr = bindings_tests_Strings_empty();
  return !ptr ? std::string() : std::string(ptr); // copy utf8
}
inline std::string strings::nullString() noexcept {
  auto ptr = bindings_tests_Strings_nullString();
  return !ptr ? std::string() : std::string(ptr); // copy utf8
}
inline std::string strings::join(const char** values, size_t count, const char* separator) noexcept {
  auto ptr = bindings_tests_Strings_join(values, count, separator);
  return !ptr ? std::string() : std::string(ptr); // copy utf8
}
inline std::string strings::join(const char** values, size_t count, const std::string& separator) noexcept {
  return join(values, count, separator.c_str());
}
inline std::string strings::repeat(const char* value, size_t times) noexcept {
  auto ptr = bindings_tests_Strings_repeat(value, times);
  return !ptr ? std::string() : std::string(ptr); // copy utf8
}
inline std::string strings::repeat(const std::string& value, size_t times) noexcept {
  return repeat(value.c_str(), times);
}

// Structs
inline double structs::totalPower(StatusInfo status, double currentAmps) noexcept {
  return bindings_tests_Structs_totalPower(status, currentAmps);
}
inline double structs::checkedVoltage(StatusInfo status) {
  internal::ErrorInfo error_ = {};
  auto result_ = bindings_tests_Structs_checkedVoltage(&error_, status);
  checkError(error_);
  return result_;
}
inline StatusInfo structs::getStatus(int index) noexcept {
  return bindings_tests_Structs_getStatus(index);
}
inline StatusInfo structs::getCheckedStatus(int index) {
  internal::ErrorInfo error_ = {};
  auto result_ = bindings_tests_Structs_getCheckedStatus(&error_, index);
  checkError(error_);
  return result_;
}


// Library Information
namespace lib {

struct Version {
  Version(int major, int minor, int patch, int build) : major_(major), minor_(minor), patch_(patch), build_(build) {}
  int major_{};
  int minor_{};
  int patch_{};
  int build_{};
};

inline Version getHeaderVersion() {
  return {1, 2, 3, 42};
}

inline Version getLibraryVersion() {
  Version v(0,0,0,0);
  bindings_tests_getLibraryVersion(&v.major_, &v.minor_, &v.patch_, &v.build_);
  return v;
}

inline bool isAvailable() { return true; }

} // namespace lib

} // namespace bindings
} // namespace tests
