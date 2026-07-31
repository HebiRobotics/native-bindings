#pragma once

#include <algorithm>
#include <cstddef>
#include <cstdint>
#include <stdexcept>
#include <string>
#include <vector>

namespace bindings {
namespace tests {

class Exception : public std::exception {
public:
  Exception(const char* message) noexcept : msg_(message) {}
  Exception(const std::string& message) noexcept : msg_(message) {}
  virtual ~Exception() noexcept {}
  const char* what() const noexcept override {
    return msg_.c_str();
  }
protected:
  std::string msg_;
};

enum class Color : int32_t {
  Red = 0,
  Green = 1,
  Blue = 2
};

// ==== Forward Declarations ====
class Counter;
class Shape;
class Circle;
class Square;
using IndexCallbackFunction = void (*)(void* userData, int index);

namespace internal {
using CounterPtr = struct Counter_*;
using ShapePtr = struct Shape_*;
using CirclePtr = struct Circle_*;
using SquarePtr = struct Square_*;
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
double sumFloat64(double* values, size_t count) noexcept;
int sumInt32(int* values, size_t count) noexcept;

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
 * least count elements. Passing the same buffer for both is supported.
 *
 * @param source 
 * @param target 
 * @param count 
 */
void cumulativeSum(double* source, double* target, size_t count) noexcept;

/**
 * @brief Reverses the buffer in place.
 *
 * @param values 
 * @param count 
 */
void reverseFloat64(double* values, size_t count) noexcept;
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
 * @brief Marked noexcept, so no status code is generated for it.
 */
void neverThrows() noexcept;

/**
 * @brief Clears the last error for the calling thread.
 */
void clearLastError() noexcept;

/**
 * @details 
 * Message of the last exception thrown on the calling thread, or an empty
 * string if there has not been one. The language wrappers call this to turn
 * a failed call into a native exception.
 */
std::string getLastError() noexcept;
} // namespace errors


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

/**
 * @brief Last error for the calling thread within the secondary isolate.
 */
std::string getLastError() noexcept;
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
 */
Color next(Color color) noexcept;
std::string nameOf(Color color) noexcept;
int ordinalOf(Color color) noexcept;

/**
 * @brief Returns the enum with the given ordinal, or Red if it is out of range.
 *
 * @param ordinal 
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
 * @brief Joins a char* array with a separator. Checks char** parameter handling.
 *
 * @param values 
 * @param count number of entries in values
 * @param separator 
 */
std::string join(char** values, size_t count, const char* separator) noexcept;
std::string join(char** values, size_t count, const std::string& separator) noexcept;

/**
 * @brief Returns a string longer than the initial scratch buffer, forcing it to grow.
 *
 * @param value 
 * @param times 
 */
std::string repeat(const char* value, size_t times) noexcept;
std::string repeat(const std::string& value, size_t times) noexcept;
} // namespace strings

// ==== C Library Functions ====
extern "C" {
  internal::CounterPtr bindings_tests_Counter_create(int);
  int bindings_tests_Counter_getValue(internal::CounterPtr);
  void bindings_tests_Counter_setValue(internal::CounterPtr, int);
  void bindings_tests_Counter_increment(internal::CounterPtr);
  void bindings_tests_Counter_add(internal::CounterPtr, int);
  int bindings_tests_Counter_isClosed(internal::CounterPtr);
  int bindings_tests_Counter_getClosedCount();
  void bindings_tests_Counter_resetClosedCount();
  void bindings_tests_Counter_release(internal::CounterPtr);
  const char* bindings_tests_Shape_getName(internal::ShapePtr);
  void bindings_tests_Shape_setName(internal::ShapePtr, const char*);
  double bindings_tests_Shape_area(internal::ShapePtr);
  int bindings_tests_Shape_sideCount(internal::ShapePtr);
  void bindings_tests_Shape_release(internal::ShapePtr);
  internal::CirclePtr bindings_tests_Circle_create(double);
  double bindings_tests_Circle_getRadius(internal::CirclePtr);
  double bindings_tests_Circle_area(internal::CirclePtr);
  int bindings_tests_Circle_sideCount(internal::CirclePtr);
  internal::ShapePtr bindings_tests_Circle_to_Shape(internal::CirclePtr);
  internal::SquarePtr bindings_tests_Square_create(double);
  double bindings_tests_Square_getSide(internal::SquarePtr);
  double bindings_tests_Square_area(internal::SquarePtr);
  int bindings_tests_Square_sideCount(internal::SquarePtr);
  internal::ShapePtr bindings_tests_Square_to_Shape(internal::SquarePtr);
  double bindings_tests_Arrays_sumFloat64(double*, size_t);
  int bindings_tests_Arrays_sumInt32(int*, size_t);
  void bindings_tests_Arrays_fillFloat64(double*, size_t, double);
  void bindings_tests_Arrays_cumulativeSum(double*, double*, size_t);
  void bindings_tests_Arrays_reverseFloat64(double*, size_t);
  void bindings_tests_Callbacks_invokeTimes(IndexCallbackFunction, void*, size_t);
  void bindings_tests_Callbacks_invokeOnce(IndexCallbackFunction, void*, int);
  int bindings_tests_Errors_requireNonNegative(int);
  int bindings_tests_Errors_divideInt32(int, int);
  double bindings_tests_Errors_failingFloat64(int);
  int bindings_tests_Errors_failingBool(int);
  const char* bindings_tests_Errors_failingString(int);
  void bindings_tests_Errors_neverThrows();
  void bindings_tests_Errors_clearLastError();
  const char* bindings_tests_Errors_getLastError();
  void bindings_tests_Isolated_increment();
  int bindings_tests_Isolated_getCounter();
  void bindings_tests_Isolated_reset();
  int bindings_tests_Isolated_alwaysFails();
  const char* bindings_tests_Isolated_getLastError();
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
  int bindings_tests_Overloads_answer(int);
  int bindings_tests_Overloads_renamed();
  Color bindings_tests_Palette_next(Color);
  const char* bindings_tests_Palette_nameOf(Color);
  int bindings_tests_Palette_ordinalOf(Color);
  Color bindings_tests_Palette_fromOrdinal(int);
  const char* bindings_tests_Strings_echo(const char*);
  const char* bindings_tests_Strings_concat(const char*, const char*);
  size_t bindings_tests_Strings_utf8Length(const char*);
  size_t bindings_tests_Strings_utf16Length(const char*);
  const char* bindings_tests_Strings_empty();
  const char* bindings_tests_Strings_nullString();
  const char* bindings_tests_Strings_join(char**, size_t, const char*);
  const char* bindings_tests_Strings_repeat(const char*, size_t);
  void bindings_tests_getLibraryVersion(int* major, int* minor, int* patch, int* build);
}

// ==== C++ Implementations ====
// Counter
inline Counter::Counter(int start) {
  ptr_ = bindings_tests_Counter_create(start);
  if (!ptr_) {
    throw Exception("Could not create Counter");
  }
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
inline Circle::Circle(double radius) {
  ptr_ = bindings_tests_Circle_create(radius);
  if (!ptr_) {
    throw Exception("Could not create Circle");
  }
}
inline double Circle::getRadius() const noexcept {
  return bindings_tests_Circle_getRadius(ptr_);
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
inline Square::Square(double side) {
  ptr_ = bindings_tests_Square_create(side);
  if (!ptr_) {
    throw Exception("Could not create Square");
  }
}
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
inline double arrays::sumFloat64(double* values, size_t count) noexcept {
  return bindings_tests_Arrays_sumFloat64(values, count);
}
inline int arrays::sumInt32(int* values, size_t count) noexcept {
  return bindings_tests_Arrays_sumInt32(values, count);
}
inline void arrays::fillFloat64(double* target, size_t count, double value) noexcept {
  bindings_tests_Arrays_fillFloat64(target, count, value);
}
inline void arrays::cumulativeSum(double* source, double* target, size_t count) noexcept {
  bindings_tests_Arrays_cumulativeSum(source, target, count);
}
inline void arrays::reverseFloat64(double* values, size_t count) noexcept {
  bindings_tests_Arrays_reverseFloat64(values, count);
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
  int status_ = bindings_tests_Errors_requireNonNegative(value);
  if (status_ != 0) {
    throw Exception("Encountered error in Errors::requireNonNegative");
  }
}
inline int errors::divideInt32(int a, int b) {
  return bindings_tests_Errors_divideInt32(a, b);
}
inline double errors::failingFloat64(bool shouldFail) {
  return bindings_tests_Errors_failingFloat64(shouldFail);
}
inline bool errors::failingBool(bool shouldFail) {
  return bindings_tests_Errors_failingBool(shouldFail);
}
inline std::string errors::failingString(bool shouldFail) {
  auto ptr = bindings_tests_Errors_failingString(shouldFail);
  return !ptr ? std::string() : std::string(ptr); // copy utf8
}
inline void errors::neverThrows() noexcept {
  bindings_tests_Errors_neverThrows();
}
inline void errors::clearLastError() noexcept {
  bindings_tests_Errors_clearLastError();
}
inline std::string errors::getLastError() noexcept {
  auto ptr = bindings_tests_Errors_getLastError();
  return !ptr ? std::string() : std::string(ptr); // copy utf8
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
  int status_ = bindings_tests_Isolated_alwaysFails();
  if (status_ != 0) {
    throw Exception("Encountered error in Isolated::alwaysFails");
  }
}
inline std::string isolated::getLastError() noexcept {
  auto ptr = bindings_tests_Isolated_getLastError();
  return !ptr ? std::string() : std::string(ptr); // copy utf8
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
inline std::string strings::join(char** values, size_t count, const char* separator) noexcept {
  auto ptr = bindings_tests_Strings_join(values, count, separator);
  return !ptr ? std::string() : std::string(ptr); // copy utf8
}
inline std::string strings::join(char** values, size_t count, const std::string& separator) noexcept {
  return join(values, count, separator.c_str());
}
inline std::string strings::repeat(const char* value, size_t times) noexcept {
  auto ptr = bindings_tests_Strings_repeat(value, times);
  return !ptr ? std::string() : std::string(ptr); // copy utf8
}
inline std::string strings::repeat(const std::string& value, size_t times) noexcept {
  return repeat(value.c_str(), times);
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
