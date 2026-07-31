#pragma once

#include <algorithm>
#include <cassert>
#include <cstddef>
#include <cstdint>
#include <stdexcept>
#include <iostream>
#include <string>
#include <vector>

#ifdef WIN32
#include <Windows.h>
#else
#include <dlfcn.h>
#endif

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

// ==== C Library Lookup ====
namespace {
struct DynamicLookup {
public:
  static DynamicLookup& instance() {
#ifdef WIN32
    static DynamicLookup lib("bindings_tests.dll");
#elif __APPLE__
    static DynamicLookup lib("libbindings_tests.dylib");
#else
    static DynamicLookup lib("./libbindings_tests.so", "libbindings_tests.so");
#endif
    return lib;
  }

  bool isLoaded() const { return lib_ != nullptr; };

  template<typename FuncType> FuncType getFunc(const char* func) const;
  DynamicLookup(const DynamicLookup&) = delete;
  DynamicLookup &operator = (const DynamicLookup&) = delete;

private:
  explicit DynamicLookup(const std::string& path, const std::string& alt_path = "");
  void* lib_{};
  std::string path_{};
};
} // namespace

// ==== C++ Implementations ====
// Counter
inline Counter::Counter(int start) {
  static auto bindings_tests_Counter_create = DynamicLookup::instance().getFunc<internal::CounterPtr(*)(int)>("bindings_tests_Counter_create");
  ptr_ = bindings_tests_Counter_create(start);
  if (!ptr_) {
    throw Exception("Could not create Counter");
  }
}
inline int Counter::getValue() const noexcept {
  static auto bindings_tests_Counter_getValue = DynamicLookup::instance().getFunc<int(*)(internal::CounterPtr)>("bindings_tests_Counter_getValue");
  return bindings_tests_Counter_getValue(ptr_);
}
inline void Counter::setValue(int value) noexcept {
  static auto bindings_tests_Counter_setValue = DynamicLookup::instance().getFunc<void(*)(internal::CounterPtr, int)>("bindings_tests_Counter_setValue");
  bindings_tests_Counter_setValue(ptr_, value);
}
inline void Counter::increment() noexcept {
  static auto bindings_tests_Counter_increment = DynamicLookup::instance().getFunc<void(*)(internal::CounterPtr)>("bindings_tests_Counter_increment");
  bindings_tests_Counter_increment(ptr_);
}
inline void Counter::add(int amount) noexcept {
  static auto bindings_tests_Counter_add = DynamicLookup::instance().getFunc<void(*)(internal::CounterPtr, int)>("bindings_tests_Counter_add");
  bindings_tests_Counter_add(ptr_, amount);
}
inline bool Counter::isClosed() const noexcept {
  static auto bindings_tests_Counter_isClosed = DynamicLookup::instance().getFunc<int(*)(internal::CounterPtr)>("bindings_tests_Counter_isClosed");
  return bindings_tests_Counter_isClosed(ptr_);
}
inline int Counter::getClosedCount() noexcept {
  static auto bindings_tests_Counter_getClosedCount = DynamicLookup::instance().getFunc<int(*)()>("bindings_tests_Counter_getClosedCount");
  return bindings_tests_Counter_getClosedCount();
}
inline void Counter::resetClosedCount() noexcept {
  static auto bindings_tests_Counter_resetClosedCount = DynamicLookup::instance().getFunc<void(*)()>("bindings_tests_Counter_resetClosedCount");
  bindings_tests_Counter_resetClosedCount();
}
inline void Counter::cleanup() noexcept {
  if (ptr_ != nullptr) {
    static auto bindings_tests_Counter_release = DynamicLookup::instance().getFunc<void(*)(internal::CounterPtr)>("bindings_tests_Counter_release");
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
  static auto bindings_tests_Shape_getName = DynamicLookup::instance().getFunc<const char*(*)(internal::ShapePtr)>("bindings_tests_Shape_getName");
  auto ptr = bindings_tests_Shape_getName(ptr_);
  return !ptr ? std::string() : std::string(ptr); // copy utf8
}
inline void Shape::setName(const char* name) noexcept {
  static auto bindings_tests_Shape_setName = DynamicLookup::instance().getFunc<void(*)(internal::ShapePtr, const char*)>("bindings_tests_Shape_setName");
  bindings_tests_Shape_setName(ptr_, name);
}
inline void Shape::setName(const std::string& name) noexcept {
  setName(name.c_str());
}
inline double Shape::area() const noexcept {
  static auto bindings_tests_Shape_area = DynamicLookup::instance().getFunc<double(*)(internal::ShapePtr)>("bindings_tests_Shape_area");
  return bindings_tests_Shape_area(ptr_);
}
inline int Shape::sideCount() const noexcept {
  static auto bindings_tests_Shape_sideCount = DynamicLookup::instance().getFunc<int(*)(internal::ShapePtr)>("bindings_tests_Shape_sideCount");
  return bindings_tests_Shape_sideCount(ptr_);
}
inline void Shape::cleanup() noexcept {
  if (ptr_ != nullptr) {
    static auto bindings_tests_Shape_release = DynamicLookup::instance().getFunc<void(*)(internal::ShapePtr)>("bindings_tests_Shape_release");
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
  static auto bindings_tests_Circle_create = DynamicLookup::instance().getFunc<internal::CirclePtr(*)(double)>("bindings_tests_Circle_create");
  ptr_ = bindings_tests_Circle_create(radius);
  if (!ptr_) {
    throw Exception("Could not create Circle");
  }
}
inline double Circle::getRadius() const noexcept {
  static auto bindings_tests_Circle_getRadius = DynamicLookup::instance().getFunc<double(*)(internal::CirclePtr)>("bindings_tests_Circle_getRadius");
  return bindings_tests_Circle_getRadius(ptr_);
}
inline double Circle::area() const noexcept {
  static auto bindings_tests_Circle_area = DynamicLookup::instance().getFunc<double(*)(internal::CirclePtr)>("bindings_tests_Circle_area");
  return bindings_tests_Circle_area(ptr_);
}
inline int Circle::sideCount() const noexcept {
  static auto bindings_tests_Circle_sideCount = DynamicLookup::instance().getFunc<int(*)(internal::CirclePtr)>("bindings_tests_Circle_sideCount");
  return bindings_tests_Circle_sideCount(ptr_);
}
inline internal::ShapePtr Circle::getShapePointer(internal::CirclePtr cPointer) noexcept {
  static auto bindings_tests_Circle_to_Shape = DynamicLookup::instance().getFunc<internal::ShapePtr(*)(internal::CirclePtr)>("bindings_tests_Circle_to_Shape");
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
  static auto bindings_tests_Square_create = DynamicLookup::instance().getFunc<internal::SquarePtr(*)(double)>("bindings_tests_Square_create");
  ptr_ = bindings_tests_Square_create(side);
  if (!ptr_) {
    throw Exception("Could not create Square");
  }
}
inline double Square::getSide() const noexcept {
  static auto bindings_tests_Square_getSide = DynamicLookup::instance().getFunc<double(*)(internal::SquarePtr)>("bindings_tests_Square_getSide");
  return bindings_tests_Square_getSide(ptr_);
}
inline double Square::area() const noexcept {
  static auto bindings_tests_Square_area = DynamicLookup::instance().getFunc<double(*)(internal::SquarePtr)>("bindings_tests_Square_area");
  return bindings_tests_Square_area(ptr_);
}
inline int Square::sideCount() const noexcept {
  static auto bindings_tests_Square_sideCount = DynamicLookup::instance().getFunc<int(*)(internal::SquarePtr)>("bindings_tests_Square_sideCount");
  return bindings_tests_Square_sideCount(ptr_);
}
inline internal::ShapePtr Square::getShapePointer(internal::SquarePtr cPointer) noexcept {
  static auto bindings_tests_Square_to_Shape = DynamicLookup::instance().getFunc<internal::ShapePtr(*)(internal::SquarePtr)>("bindings_tests_Square_to_Shape");
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
  static auto bindings_tests_Arrays_sumFloat64 = DynamicLookup::instance().getFunc<double(*)(double*, size_t)>("bindings_tests_Arrays_sumFloat64");
  return bindings_tests_Arrays_sumFloat64(values, count);
}
inline int arrays::sumInt32(int* values, size_t count) noexcept {
  static auto bindings_tests_Arrays_sumInt32 = DynamicLookup::instance().getFunc<int(*)(int*, size_t)>("bindings_tests_Arrays_sumInt32");
  return bindings_tests_Arrays_sumInt32(values, count);
}
inline void arrays::fillFloat64(double* target, size_t count, double value) noexcept {
  static auto bindings_tests_Arrays_fillFloat64 = DynamicLookup::instance().getFunc<void(*)(double*, size_t, double)>("bindings_tests_Arrays_fillFloat64");
  bindings_tests_Arrays_fillFloat64(target, count, value);
}
inline void arrays::cumulativeSum(double* source, double* target, size_t count) noexcept {
  static auto bindings_tests_Arrays_cumulativeSum = DynamicLookup::instance().getFunc<void(*)(double*, double*, size_t)>("bindings_tests_Arrays_cumulativeSum");
  bindings_tests_Arrays_cumulativeSum(source, target, count);
}
inline void arrays::reverseFloat64(double* values, size_t count) noexcept {
  static auto bindings_tests_Arrays_reverseFloat64 = DynamicLookup::instance().getFunc<void(*)(double*, size_t)>("bindings_tests_Arrays_reverseFloat64");
  bindings_tests_Arrays_reverseFloat64(values, count);
}

// Callbacks
inline void callbacks::invokeTimes(IndexCallbackFunction func, void* userData, size_t times) noexcept {
  static auto bindings_tests_Callbacks_invokeTimes = DynamicLookup::instance().getFunc<void(*)(IndexCallbackFunction, void*, size_t)>("bindings_tests_Callbacks_invokeTimes");
  bindings_tests_Callbacks_invokeTimes(func, userData, times);
}
inline void callbacks::invokeOnce(IndexCallbackFunction func, void* userData, int index) noexcept {
  static auto bindings_tests_Callbacks_invokeOnce = DynamicLookup::instance().getFunc<void(*)(IndexCallbackFunction, void*, int)>("bindings_tests_Callbacks_invokeOnce");
  bindings_tests_Callbacks_invokeOnce(func, userData, index);
}

// Errors
inline void errors::requireNonNegative(int value) {
  static auto bindings_tests_Errors_requireNonNegative = DynamicLookup::instance().getFunc<int(*)(int)>("bindings_tests_Errors_requireNonNegative");
  int status_ = bindings_tests_Errors_requireNonNegative(value);
  if (status_ != 0) {
    throw Exception("Encountered error in Errors::requireNonNegative");
  }
}
inline int errors::divideInt32(int a, int b) {
  static auto bindings_tests_Errors_divideInt32 = DynamicLookup::instance().getFunc<int(*)(int, int)>("bindings_tests_Errors_divideInt32");
  return bindings_tests_Errors_divideInt32(a, b);
}
inline double errors::failingFloat64(bool shouldFail) {
  static auto bindings_tests_Errors_failingFloat64 = DynamicLookup::instance().getFunc<double(*)(int)>("bindings_tests_Errors_failingFloat64");
  return bindings_tests_Errors_failingFloat64(shouldFail);
}
inline bool errors::failingBool(bool shouldFail) {
  static auto bindings_tests_Errors_failingBool = DynamicLookup::instance().getFunc<int(*)(int)>("bindings_tests_Errors_failingBool");
  return bindings_tests_Errors_failingBool(shouldFail);
}
inline std::string errors::failingString(bool shouldFail) {
  static auto bindings_tests_Errors_failingString = DynamicLookup::instance().getFunc<const char*(*)(int)>("bindings_tests_Errors_failingString");
  auto ptr = bindings_tests_Errors_failingString(shouldFail);
  return !ptr ? std::string() : std::string(ptr); // copy utf8
}
inline void errors::neverThrows() noexcept {
  static auto bindings_tests_Errors_neverThrows = DynamicLookup::instance().getFunc<void(*)()>("bindings_tests_Errors_neverThrows");
  bindings_tests_Errors_neverThrows();
}
inline void errors::clearLastError() noexcept {
  static auto bindings_tests_Errors_clearLastError = DynamicLookup::instance().getFunc<void(*)()>("bindings_tests_Errors_clearLastError");
  bindings_tests_Errors_clearLastError();
}
inline std::string errors::getLastError() noexcept {
  static auto bindings_tests_Errors_getLastError = DynamicLookup::instance().getFunc<const char*(*)()>("bindings_tests_Errors_getLastError");
  auto ptr = bindings_tests_Errors_getLastError();
  return !ptr ? std::string() : std::string(ptr); // copy utf8
}

// Isolated
inline void isolated::increment() noexcept {
  static auto bindings_tests_Isolated_increment = DynamicLookup::instance().getFunc<void(*)()>("bindings_tests_Isolated_increment");
  bindings_tests_Isolated_increment();
}
inline int isolated::getCounter() noexcept {
  static auto bindings_tests_Isolated_getCounter = DynamicLookup::instance().getFunc<int(*)()>("bindings_tests_Isolated_getCounter");
  return bindings_tests_Isolated_getCounter();
}
inline void isolated::reset() noexcept {
  static auto bindings_tests_Isolated_reset = DynamicLookup::instance().getFunc<void(*)()>("bindings_tests_Isolated_reset");
  bindings_tests_Isolated_reset();
}
inline void isolated::alwaysFails() {
  static auto bindings_tests_Isolated_alwaysFails = DynamicLookup::instance().getFunc<int(*)()>("bindings_tests_Isolated_alwaysFails");
  int status_ = bindings_tests_Isolated_alwaysFails();
  if (status_ != 0) {
    throw Exception("Encountered error in Isolated::alwaysFails");
  }
}
inline std::string isolated::getLastError() noexcept {
  static auto bindings_tests_Isolated_getLastError = DynamicLookup::instance().getFunc<const char*(*)()>("bindings_tests_Isolated_getLastError");
  auto ptr = bindings_tests_Isolated_getLastError();
  return !ptr ? std::string() : std::string(ptr); // copy utf8
}

// Math
inline bool math::negate(bool value) noexcept {
  static auto bindings_tests_Math_negate = DynamicLookup::instance().getFunc<int(*)(int)>("bindings_tests_Math_negate");
  return bindings_tests_Math_negate(value);
}
inline unsigned short math::nextChar(unsigned short value) noexcept {
  static auto bindings_tests_Math_nextChar = DynamicLookup::instance().getFunc<unsigned short(*)(unsigned short)>("bindings_tests_Math_nextChar");
  return bindings_tests_Math_nextChar(value);
}
inline char math::addInt8(char a, char b) noexcept {
  static auto bindings_tests_Math_addInt8 = DynamicLookup::instance().getFunc<char(*)(char, char)>("bindings_tests_Math_addInt8");
  return bindings_tests_Math_addInt8(a, b);
}
inline short math::addInt16(short a, short b) noexcept {
  static auto bindings_tests_Math_addInt16 = DynamicLookup::instance().getFunc<short(*)(short, short)>("bindings_tests_Math_addInt16");
  return bindings_tests_Math_addInt16(a, b);
}
inline int math::addInt32(int a, int b) noexcept {
  static auto bindings_tests_Math_addInt32 = DynamicLookup::instance().getFunc<int(*)(int, int)>("bindings_tests_Math_addInt32");
  return bindings_tests_Math_addInt32(a, b);
}
inline long long math::addInt64(long long a, long long b) noexcept {
  static auto bindings_tests_Math_addInt64 = DynamicLookup::instance().getFunc<long long(*)(long long, long long)>("bindings_tests_Math_addInt64");
  return bindings_tests_Math_addInt64(a, b);
}
inline float math::addFloat32(float a, float b) noexcept {
  static auto bindings_tests_Math_addFloat32 = DynamicLookup::instance().getFunc<float(*)(float, float)>("bindings_tests_Math_addFloat32");
  return bindings_tests_Math_addFloat32(a, b);
}
inline double math::addFloat64(double a, double b) noexcept {
  static auto bindings_tests_Math_addFloat64 = DynamicLookup::instance().getFunc<double(*)(double, double)>("bindings_tests_Math_addFloat64");
  return bindings_tests_Math_addFloat64(a, b);
}
inline size_t math::addSize(size_t a, size_t b) noexcept {
  static auto bindings_tests_Math_addSize = DynamicLookup::instance().getFunc<size_t(*)(size_t, size_t)>("bindings_tests_Math_addSize");
  return bindings_tests_Math_addSize(a, b);
}
inline int math::sizeToInt32(size_t value) noexcept {
  static auto bindings_tests_Math_sizeToInt32 = DynamicLookup::instance().getFunc<int(*)(size_t)>("bindings_tests_Math_sizeToInt32");
  return bindings_tests_Math_sizeToInt32(value);
}
inline int math::maxInt32() noexcept {
  static auto bindings_tests_Math_maxInt32 = DynamicLookup::instance().getFunc<int(*)()>("bindings_tests_Math_maxInt32");
  return bindings_tests_Math_maxInt32();
}
inline size_t math::maxSize() noexcept {
  static auto bindings_tests_Math_maxSize = DynamicLookup::instance().getFunc<size_t(*)()>("bindings_tests_Math_maxSize");
  return bindings_tests_Math_maxSize();
}

// Overloads
inline int overloads::scale(int value, int factor) noexcept {
  static auto bindings_tests_Overloads_scale = DynamicLookup::instance().getFunc<int(*)(int, int)>("bindings_tests_Overloads_scale");
  return bindings_tests_Overloads_scale(value, factor);
}
inline std::string overloads::greet(const char* name, const char* greeting) noexcept {
  static auto bindings_tests_Overloads_greet = DynamicLookup::instance().getFunc<const char*(*)(const char*, const char*)>("bindings_tests_Overloads_greet");
  auto ptr = bindings_tests_Overloads_greet(name, greeting);
  return !ptr ? std::string() : std::string(ptr); // copy utf8
}
inline std::string overloads::greet(const std::string& name, const std::string& greeting) noexcept {
  return greet(name.c_str(), greeting.c_str());
}
inline int overloads::total(int sum, int factor) noexcept {
  static auto bindings_tests_Overloads_total = DynamicLookup::instance().getFunc<int(*)(int, int)>("bindings_tests_Overloads_total");
  return bindings_tests_Overloads_total(sum, factor);
}
inline int overloads::total(const std::vector<int>& values, int factor) noexcept {
  int sum = 0; for (int v : values) { sum += v; }return total(sum, factor);
}
inline int overloads::answer(int multiplier) noexcept {
  static auto bindings_tests_Overloads_answer = DynamicLookup::instance().getFunc<int(*)(int)>("bindings_tests_Overloads_answer");
return 21 * multiplier;
  return bindings_tests_Overloads_answer(multiplier);
}
inline int overloads::renamed() noexcept {
  static auto bindings_tests_Overloads_renamed = DynamicLookup::instance().getFunc<int(*)()>("bindings_tests_Overloads_renamed");
  return bindings_tests_Overloads_renamed();
}

// Palette
inline Color palette::next(Color color) noexcept {
  static auto bindings_tests_Palette_next = DynamicLookup::instance().getFunc<Color(*)(Color)>("bindings_tests_Palette_next");
  return bindings_tests_Palette_next(color);
}
inline std::string palette::nameOf(Color color) noexcept {
  static auto bindings_tests_Palette_nameOf = DynamicLookup::instance().getFunc<const char*(*)(Color)>("bindings_tests_Palette_nameOf");
  auto ptr = bindings_tests_Palette_nameOf(color);
  return !ptr ? std::string() : std::string(ptr); // copy utf8
}
inline int palette::ordinalOf(Color color) noexcept {
  static auto bindings_tests_Palette_ordinalOf = DynamicLookup::instance().getFunc<int(*)(Color)>("bindings_tests_Palette_ordinalOf");
  return bindings_tests_Palette_ordinalOf(color);
}
inline Color palette::fromOrdinal(int ordinal) noexcept {
  static auto bindings_tests_Palette_fromOrdinal = DynamicLookup::instance().getFunc<Color(*)(int)>("bindings_tests_Palette_fromOrdinal");
  return bindings_tests_Palette_fromOrdinal(ordinal);
}

// Strings
inline std::string strings::echo(const char* value) noexcept {
  static auto bindings_tests_Strings_echo = DynamicLookup::instance().getFunc<const char*(*)(const char*)>("bindings_tests_Strings_echo");
  auto ptr = bindings_tests_Strings_echo(value);
  return !ptr ? std::string() : std::string(ptr); // copy utf8
}
inline std::string strings::echo(const std::string& value) noexcept {
  return echo(value.c_str());
}
inline std::string strings::concat(const char* a, const char* b) noexcept {
  static auto bindings_tests_Strings_concat = DynamicLookup::instance().getFunc<const char*(*)(const char*, const char*)>("bindings_tests_Strings_concat");
  auto ptr = bindings_tests_Strings_concat(a, b);
  return !ptr ? std::string() : std::string(ptr); // copy utf8
}
inline std::string strings::concat(const std::string& a, const std::string& b) noexcept {
  return concat(a.c_str(), b.c_str());
}
inline size_t strings::utf8Length(const char* value) noexcept {
  static auto bindings_tests_Strings_utf8Length = DynamicLookup::instance().getFunc<size_t(*)(const char*)>("bindings_tests_Strings_utf8Length");
  return bindings_tests_Strings_utf8Length(value);
}
inline size_t strings::utf8Length(const std::string& value) noexcept {
  return utf8Length(value.c_str());
}
inline size_t strings::utf16Length(const char* value) noexcept {
  static auto bindings_tests_Strings_utf16Length = DynamicLookup::instance().getFunc<size_t(*)(const char*)>("bindings_tests_Strings_utf16Length");
  return bindings_tests_Strings_utf16Length(value);
}
inline size_t strings::utf16Length(const std::string& value) noexcept {
  return utf16Length(value.c_str());
}
inline std::string strings::empty() noexcept {
  static auto bindings_tests_Strings_empty = DynamicLookup::instance().getFunc<const char*(*)()>("bindings_tests_Strings_empty");
  auto ptr = bindings_tests_Strings_empty();
  return !ptr ? std::string() : std::string(ptr); // copy utf8
}
inline std::string strings::nullString() noexcept {
  static auto bindings_tests_Strings_nullString = DynamicLookup::instance().getFunc<const char*(*)()>("bindings_tests_Strings_nullString");
  auto ptr = bindings_tests_Strings_nullString();
  return !ptr ? std::string() : std::string(ptr); // copy utf8
}
inline std::string strings::join(char** values, size_t count, const char* separator) noexcept {
  static auto bindings_tests_Strings_join = DynamicLookup::instance().getFunc<const char*(*)(char**, size_t, const char*)>("bindings_tests_Strings_join");
  auto ptr = bindings_tests_Strings_join(values, count, separator);
  return !ptr ? std::string() : std::string(ptr); // copy utf8
}
inline std::string strings::join(char** values, size_t count, const std::string& separator) noexcept {
  return join(values, count, separator.c_str());
}
inline std::string strings::repeat(const char* value, size_t times) noexcept {
  static auto bindings_tests_Strings_repeat = DynamicLookup::instance().getFunc<const char*(*)(const char*, size_t)>("bindings_tests_Strings_repeat");
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
  static auto bindings_tests_getLibraryVersion = DynamicLookup::instance().getFunc<void(*)(int*, int*, int*, int*)>("bindings_tests_getLibraryVersion");
  bindings_tests_getLibraryVersion(&v.major_, &v.minor_, &v.patch_, &v.build_);
  return v;
}

inline bool isAvailable() { return DynamicLookup::instance().isLoaded(); }

} // namespace lib


template<typename FuncType> FuncType DynamicLookup::getFunc(const char* func) const {
  assert(lib_ != nullptr && "Fatal error -- cannot call bindings::tests functions on unloaded library");
  if (!lib_) {
    std::cerr << "Fatal error -- cannot call bindings::tests functions on unloaded library\n";
    return nullptr;
  }
#ifdef WIN32
  FARPROC symbol = ::GetProcAddress((HMODULE) lib_, func);
#else
  void* symbol = dlsym(lib_, func);
#endif
  if (!symbol) {
    std::cerr << "Fatal error -- bindings::tests library is missing expected symbol: " << func << "\n";
  }
  assert(symbol != nullptr);
  return reinterpret_cast<FuncType>(symbol);
}

DynamicLookup:: DynamicLookup(const std::string& path, const std::string& alt_path) {
  path_ = path;
#ifdef WIN32
  // convert to wide string to support non-ascii paths
  int size_needed = MultiByteToWideChar(CP_UTF8, 0, path.c_str(), (int)path.size(), NULL, 0);
  std::wstring wstrTo(size_needed, 0);
  MultiByteToWideChar(CP_UTF8, 0, path.c_str(), (int)path.size(), &wstrTo[0], size_needed);
  lib_ = ::LoadLibraryW(wstrTo.c_str());
  if (!lib_ && !alt_path.empty()) {
    size_needed = MultiByteToWideChar(CP_UTF8, 0, alt_path.c_str(), (int)alt_path.size(), NULL, 0);
    wstrTo = std::wstring(size_needed, 0);
    MultiByteToWideChar(CP_UTF8, 0, alt_path.c_str(), (int)alt_path.size(), &wstrTo[0], size_needed);
    lib_ = ::LoadLibraryW(wstrTo.c_str());
  }
#else
  lib_ = dlopen(path.c_str(), RTLD_LAZY);
  if (!lib_ && !alt_path.empty()) {
    lib_ = dlopen(alt_path.c_str(), RTLD_LAZY);
  }
#endif
  if (!lib_) {
    std::cerr << "bindings::tests library not found! Include \""<< path <<"\" in library search path.\n";
    return;
  }

  // Check version compatibility. Note that we haven't finished the constructor, so we can't
  // just use the API method as it would cause a recursive call stack.
  lib::Version header_version = lib::getHeaderVersion();
  lib::Version lib_version(-1,0,0,0);
  auto bindings_tests_getLibraryVersion = getFunc<void(*)(int*, int*, int*, int*)>("bindings_tests_getLibraryVersion");
  bindings_tests_getLibraryVersion(&lib_version.major_, &lib_version.minor_, &lib_version.patch_, &lib_version.build_);

  bool is_compatible = (lib_version.major_ == header_version.major_ &&
                        lib_version.minor_ >= header_version.minor_ &&
                        (lib_version.minor_ > header_version.minor_ || lib_version.patch_ >= header_version.patch_));
  if (!is_compatible) {
    std::cerr << "Incompatible bindings::tests library version found!\n";
#ifdef WIN32
    if (!::FreeLibrary((HMODULE)lib_))
      std::cerr << "Error closing bindings::tests library!\n";
#else
    if (dlclose(lib_) != 0)
      std::cerr << "Error closing bindings::tests library!\n";
#endif
    lib_ = nullptr;
  }

}

} // namespace bindings
} // namespace tests
