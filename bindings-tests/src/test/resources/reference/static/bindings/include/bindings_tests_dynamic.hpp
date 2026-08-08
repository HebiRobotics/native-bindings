#pragma once

#include <cassert>
#include <cstddef>
#include <cstdint>
#include <functional>
#include <iostream>
#include <memory>
#include <stdexcept>
#include <string>
#include <type_traits>
#include <utility>
#include <vector>

#ifdef WIN32
#include <Windows.h>
#else
#include <dlfcn.h>
#endif

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
struct FieldTypes {
  uint16_t unit;
  const int16_t* shorts;
  int64_t* longs;
  void* context;
};

// Plain data struct, passed by value. Layout changes are not ABI backwards compatible
struct StatusInfo {
  double voltage;
  int32_t state;
  int32_t enabled;
};

// ==== Forward Declarations ====
class Counted;
class Counter;
class Naming;
class Notified;
class PlainStored;
class Shape;
class Circle;
class Square;
class Stored;
using IndexCallbackFunction = void (*)(void* userData, int index);
using NotifyCallbackFunction = void (*)(void* userData);
using PlainIndexCallbackFunction = void (*)(int index);
using PlainNotifyCallbackFunction = void (*)();
using ScaleCallbackFunction = double (*)(void* userData, double value);
using StoredCallbackFunction = void (*)(void* userData, int index);
using TransformCallbackFunction = void (*)(void* userData, const double* input, double* output);

namespace internal {
using CountedPtr = struct Counted_*;
using CounterPtr = struct Counter_*;
using NamingPtr = struct Naming_*;
using NotifiedPtr = struct Notified_*;
using PlainStoredPtr = struct PlainStored_*;
using ShapePtr = struct Shape_*;
using CirclePtr = struct Circle_*;
using SquarePtr = struct Square_*;
using StoredPtr = struct Stored_*;
struct ByteSpan {
  const uint8_t* data;
  size_t length;
};
struct DoubleSpan {
  const double* data;
  size_t length;
};
struct FloatSpan {
  const float* data;
  size_t length;
};
struct IntSpan {
  const int32_t* data;
  size_t length;
};
// The ABI channel is a non-const void*, so a const callable still has to lose its
// constness on the way in. The trampoline below puts it back.
template <typename F>
void* toUserData(F& fn) {
  return const_cast<void*>(static_cast<const void*>(&fn));
}
// Calls the closure that arrived through IndexCallbackFunction's userData channel. The caller owns
// it, on the stack for a call, on the heap for a handle that stores it past the call.
template <typename F>
void invokeIndexCallbackFunction(void* userData, int index) {
  (*static_cast<F*>(userData))(index);
}
// Calls the closure that arrived through NotifyCallbackFunction's userData channel. The caller owns
// it, on the stack for a call, on the heap for a handle that stores it past the call.
template <typename F>
void invokeNotifyCallbackFunction(void* userData) {
  (*static_cast<F*>(userData))();
}
// Calls the closure that arrived through ScaleCallbackFunction's userData channel. The caller owns
// it, on the stack for a call, on the heap for a handle that stores it past the call.
template <typename F>
double invokeScaleCallbackFunction(void* userData, double value) {
  return (*static_cast<F*>(userData))(value);
}
// Calls the closure that arrived through StoredCallbackFunction's userData channel. The caller owns
// it, on the stack for a call, on the heap for a handle that stores it past the call.
template <typename F>
void invokeStoredCallbackFunction(void* userData, int index) {
  (*static_cast<F*>(userData))(index);
}
// Calls the closure that arrived through TransformCallbackFunction's userData channel. The caller owns
// it, on the stack for a call, on the heap for a handle that stores it past the call.
template <typename F>
void invokeTransformCallbackFunction(void* userData, const double* input, double* output) {
  (*static_cast<F*>(userData))(input, output);
}
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
 * @brief A handle whose construction invokes a callback once per index.
 */
class Counted {
public:

  /**
   * @brief Creates the handle, invoking the callback with indices 0 to times-1 before returning it.
   *
   * @param func 
   * @param userData 
   * @param times 
   * @return A handle whose construction invokes a callback once per index.
   * @throw on internal errors
   */
  Counted(IndexCallbackFunction func, void* userData, int times);
  // Takes ownership of the callable, so the userData argument disappears from the
  // signature. It lives on the heap because the library keeps its address for as long
  // as this handle exists, and that has to survive the wrapper being moved. It must
  // not throw: the call crosses a C ABI that no exception may unwind.
  template <typename F, typename = typename std::enable_if<!std::is_convertible<F, IndexCallbackFunction>::value
      && !std::is_same<typename std::decay<F>::type, Counted>::value>::type>
  Counted(F&& func, int times);

  /**
   * @details 
   * Total number of handles of this type that have been released so far in this
   * isolate. Increments exactly once per released handle.
   */
  static int getClosedCount() noexcept;
  Counted(Counted&& from) noexcept;
  Counted& operator=(Counted&& from) noexcept;
  ~Counted() noexcept;
private:
  void cleanup() noexcept;
  internal::CountedPtr ptr_{};
  std::unique_ptr<std::function<void(int)>> callback_{};
};


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
 * @brief A handle whose construction invokes a callback.
 */
class Notified {
public:

  /**
   * @brief Creates the handle and invokes the callback once before returning it.
   *
   * @param func 
   * @param userData 
   * @return A handle whose construction invokes a callback.
   * @throw on internal errors
   */
  Notified(NotifyCallbackFunction func, void* userData);
  // Takes ownership of the callable, so the userData argument disappears from the
  // signature. It lives on the heap because the library keeps its address for as long
  // as this handle exists, and that has to survive the wrapper being moved. It must
  // not throw: the call crosses a C ABI that no exception may unwind.
  template <typename F, typename = typename std::enable_if<!std::is_convertible<F, NotifyCallbackFunction>::value
      && !std::is_same<typename std::decay<F>::type, Notified>::value>::type>
  Notified(F&& func);
  Notified(Notified&& from) noexcept;
  Notified& operator=(Notified&& from) noexcept;
  ~Notified() noexcept;
private:
  void cleanup() noexcept;
  internal::NotifiedPtr ptr_{};
  std::unique_ptr<std::function<void()>> callback_{};
};


/**
 * @brief A handle that invokes a callback during construction and stores it for later calls.
 */
class PlainStored {
public:

  /**
   * @details 
   * Invokes the callback with indices 0 to times-1 before returning the handle, and
   * keeps it for every later call to fire(), so it has to stay alive for as long as
   * the handle does.
   *
   * @param func 
   * @param times 
   * @return A handle that invokes a callback during construction and stores it for later calls.
   * @throw on internal errors
   */
  PlainStored(PlainIndexCallbackFunction func, size_t times);

  /**
   * @brief Invokes the stored callback once with the given index, on the calling thread.
   *
   * @param index 
   */
  void fire(int index) noexcept;

  /**
   * @details 
   * Total number of handles of this type that have been released so far in this
   * isolate. Increments exactly once per released handle.
   */
  static int getClosedCount() noexcept;
  PlainStored(PlainStored&& from) noexcept;
  PlainStored& operator=(PlainStored&& from) noexcept;
  ~PlainStored() noexcept;
private:
  void cleanup() noexcept;
  internal::PlainStoredPtr ptr_{};
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
 * @brief A handle that stores a callback and invokes it during later calls.
 */
class Stored {
public:

  /**
   * @details 
   * Stores the callback and returns without invoking it. Every later call to
   * fire() runs it on the calling thread, so it has to stay alive for as long
   * as the handle does.
   *
   * @param func 
   * @param userData 
   * @return A handle that stores a callback and invokes it during later calls.
   * @throw on internal errors
   */
  Stored(StoredCallbackFunction func, void* userData);
  // Takes ownership of the callable, so the userData argument disappears from the
  // signature. It lives on the heap because the library keeps its address for as long
  // as this handle exists, and that has to survive the wrapper being moved. It must
  // not throw: the call crosses a C ABI that no exception may unwind.
  template <typename F, typename = typename std::enable_if<!std::is_convertible<F, StoredCallbackFunction>::value
      && !std::is_same<typename std::decay<F>::type, Stored>::value>::type>
  Stored(F&& func);

  /**
   * @brief Invokes the stored callback once with the given index, on the calling thread.
   *
   * @param index 
   */
  void fire(int index) noexcept;

  /**
   * @details 
   * Total number of handles of this type that have been released so far in this
   * isolate. Increments exactly once per released handle.
   */
  static int getClosedCount() noexcept;
  Stored(Stored&& from) noexcept;
  Stored& operator=(Stored&& from) noexcept;
  ~Stored() noexcept;
private:
  void cleanup() noexcept;
  internal::StoredPtr ptr_{};
  std::unique_ptr<std::function<void(int)>> callback_{};
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
 * @brief Returns a new array with the offset added to every element. Checks an int32 span in and an array out.
 *
 * @param values 
 * @param offset 
 */
std::vector<int32_t> addOffsetInt32(internal::IntSpan values, int offset) noexcept;
std::vector<int32_t> addOffsetInt32(const std::vector<int32_t>& values, int offset) noexcept;

/**
 * @brief Returns a new array with the offset added to every element. Checks a float span in and an array out.
 *
 * @param values 
 * @param offset 
 */
std::vector<float> addOffsetFloat32(internal::FloatSpan values, float offset) noexcept;
std::vector<float> addOffsetFloat32(const std::vector<float>& values, float offset) noexcept;

/**
 * @brief Sum of the unsigned byte values. Checks the raw uint8 pointer with an explicit count, the shape an image row uses.
 *
 * @param values 
 * @param count 
 */
int sumUint8(uint8_t* values, size_t count) noexcept;

/**
 * @brief Returns a new array with the offset added to every element. Checks a byte span in and an array out, including embedded zeros that would truncate a string.
 *
 * @param values 
 * @param offset 
 */
std::vector<uint8_t> addOffsetUint8(internal::ByteSpan values, char offset) noexcept;
std::vector<uint8_t> addOffsetUint8(const std::vector<uint8_t>& values, char offset) noexcept;

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
// The callable holds the state, so these two need no userData argument. The closure
// stays on the caller's stack for the duration of the call and neither one allocates.
// It must not throw: the call crosses a C ABI that no exception may unwind, so a
// throw terminates.
template <typename F, typename = typename std::enable_if<!std::is_convertible<F, IndexCallbackFunction>::value>::type>
void invokeTimes(F&& func, size_t times) noexcept {
  invokeTimes(&internal::invokeIndexCallbackFunction<typename std::remove_reference<F>::type>, internal::toUserData(func), times);
}
void invokeTimes(const std::function<void(int)>& func, size_t times) noexcept;

/**
 * @brief Invokes the callback once with the given index. Returns after it completes.
 *
 * @param func 
 * @param userData 
 * @param index 
 */
void invokeOnce(IndexCallbackFunction func, void* userData, int index) noexcept;
// The callable holds the state, so these two need no userData argument. The closure
// stays on the caller's stack for the duration of the call and neither one allocates.
// It must not throw: the call crosses a C ABI that no exception may unwind, so a
// throw terminates.
template <typename F, typename = typename std::enable_if<!std::is_convertible<F, IndexCallbackFunction>::value>::type>
void invokeOnce(F&& func, int index) noexcept {
  invokeOnce(&internal::invokeIndexCallbackFunction<typename std::remove_reference<F>::type>, internal::toUserData(func), index);
}
void invokeOnce(const std::function<void(int)>& func, int index) noexcept;

/**
 * @details 
 * Invokes the callback once with the given index, then throws if the index is
 * negative. The invocation happens before the validation on purpose, so that a
 * caller can observe both failures in one call.
 *
 * @param func 
 * @param userData 
 * @param index 
 * @throw index is negative
 */
void invokeChecked(IndexCallbackFunction func, void* userData, int index);
// The callable holds the state, so these two need no userData argument. The closure
// stays on the caller's stack for the duration of the call and neither one allocates.
// It must not throw: the call crosses a C ABI that no exception may unwind, so a
// throw terminates.
template <typename F, typename = typename std::enable_if<!std::is_convertible<F, IndexCallbackFunction>::value>::type>
void invokeChecked(F&& func, int index) {
  invokeChecked(&internal::invokeIndexCallbackFunction<typename std::remove_reference<F>::type>, internal::toUserData(func), index);
}
void invokeChecked(const std::function<void(int)>& func, int index);

/**
 * @details 
 * Invokes the callback once with the given index on a worker thread, and joins that
 * thread before returning. The invocation therefore completes within the call but not
 * on the calling thread, which is the shape a solver has when it fans an objective
 * out to workers. The callback must be thread safe.
 *
 * @param func 
 * @param userData 
 * @param index 
 */
void invokeOnWorkerThread(IndexCallbackFunction func, void* userData, int index) noexcept;
// The callable holds the state, so these two need no userData argument. The closure
// stays on the caller's stack for the duration of the call and neither one allocates.
// It must not throw: the call crosses a C ABI that no exception may unwind, so a
// throw terminates.
template <typename F, typename = typename std::enable_if<!std::is_convertible<F, IndexCallbackFunction>::value>::type>
void invokeOnWorkerThread(F&& func, int index) noexcept {
  invokeOnWorkerThread(&internal::invokeIndexCallbackFunction<typename std::remove_reference<F>::type>, internal::toUserData(func), index);
}
void invokeOnWorkerThread(const std::function<void(int)>& func, int index) noexcept;

/**
 * @brief Invokes the callback once. Returns after it completes.
 *
 * @param func 
 * @param userData 
 */
void notifyOnce(NotifyCallbackFunction func, void* userData) noexcept;
// The callable holds the state, so these two need no userData argument. The closure
// stays on the caller's stack for the duration of the call and neither one allocates.
// It must not throw: the call crosses a C ABI that no exception may unwind, so a
// throw terminates.
template <typename F, typename = typename std::enable_if<!std::is_convertible<F, NotifyCallbackFunction>::value>::type>
void notifyOnce(F&& func) noexcept {
  notifyOnce(&internal::invokeNotifyCallbackFunction<typename std::remove_reference<F>::type>, internal::toUserData(func));
}
void notifyOnce(const std::function<void()>& func) noexcept;

/**
 * @details 
 * Passes the value to the callback and returns what the callback produced. A null
 * function pointer leaves the value unchanged.
 *
 * @param func 
 * @param userData 
 * @param value 
 */
double applyScaled(ScaleCallbackFunction func, void* userData, double value) noexcept;
// The callable holds the state, so these two need no userData argument. The closure
// stays on the caller's stack for the duration of the call and neither one allocates.
// It must not throw: the call crosses a C ABI that no exception may unwind, so a
// throw terminates.
template <typename F, typename = typename std::enable_if<!std::is_convertible<F, ScaleCallbackFunction>::value>::type>
double applyScaled(F&& func, double value) noexcept {
  return applyScaled(&internal::invokeScaleCallbackFunction<typename std::remove_reference<F>::type>, internal::toUserData(func), value);
}
double applyScaled(const std::function<double(double)>& func, double value) noexcept;

/**
 * @details 
 * Invokes the callback once with the two buffers and returns after it completes.
 * Both buffers stay owned by the caller, and how many elements they hold is
 * between the caller and the callback, since a raw pointer carries no length.
 * The input is not verified! A null function pointer leaves output untouched.
 *
 * @param func 
 * @param userData 
 * @param input 
 * @param output 
 */
void transformOnce(TransformCallbackFunction func, void* userData, const double* input, double* output) noexcept;
void transformOnce(TransformCallbackFunction func, void* userData, const std::vector<double>& input, double* output) noexcept;
// The callable holds the state, so these two need no userData argument. The closure
// stays on the caller's stack for the duration of the call and neither one allocates.
// It must not throw: the call crosses a C ABI that no exception may unwind, so a
// throw terminates.
template <typename F, typename = typename std::enable_if<!std::is_convertible<F, TransformCallbackFunction>::value>::type>
void transformOnce(F&& func, const double* input, double* output) noexcept {
  transformOnce(&internal::invokeTransformCallbackFunction<typename std::remove_reference<F>::type>, internal::toUserData(func), input, output);
}
void transformOnce(const std::function<void(const double*, double*)>& func, const double* input, double* output) noexcept;

/**
 * @details 
 * Invokes the callback exactly 'times' times with indices 0 to times-1, then
 * returns. A null function pointer is ignored, and a non-positive count does
 * nothing.
 *
 * @param func 
 * @param times 
 */
void invokePlainTimes(PlainIndexCallbackFunction func, size_t times) noexcept;

/**
 * @details 
 * Invokes the callback once with the given index, then throws if the index is
 * negative. The invocation happens before the validation on purpose, so that a
 * caller can observe both failures in one call.
 *
 * @param func 
 * @param index 
 * @throw index is negative
 */
void invokePlainChecked(PlainIndexCallbackFunction func, int index);

/**
 * @details 
 * Invokes the callback once with the given index on a worker thread, and joins that
 * thread before returning. The invocation therefore completes within the call but not
 * on the calling thread, which is the shape a solver has when it fans an objective
 * out to workers. The callback must be thread safe.
 *
 * @param func 
 * @param index 
 */
void invokePlainOnWorkerThread(PlainIndexCallbackFunction func, int index) noexcept;

/**
 * @brief Invokes the callback once. Returns after it completes.
 *
 * @param func 
 */
void notifyPlainOnce(PlainNotifyCallbackFunction func) noexcept;
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
 * Fills the out parameter from the arguments. Pins the field types StatusInfo
 * leaves out: the char code unit crosses as uint16, the pointers pass through
 * untouched so the caller can verify them against what it handed in.
 *
 * @param unit 
 * @param shorts 
 * @param longs 
 * @param context 
 */
FieldTypes fillFieldTypes(unsigned short unit, short* shorts, long long int* longs, void* context) noexcept;

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
// Counted
inline Counted::Counted(IndexCallbackFunction func, void* userData, int times) {
  static auto bindings_tests_Counted_create = DynamicLookup::instance().getFunc<internal::CountedPtr(*)(internal::ErrorInfo*, IndexCallbackFunction, void*, int)>("bindings_tests_Counted_create");
  internal::ErrorInfo error_ = {};
  ptr_ = bindings_tests_Counted_create(&error_, func, userData, times);
  checkError(error_);
  checkNotNull(ptr_, "Could not create Counted");
}
template <typename F, typename Enable>
inline Counted::Counted(F&& func, int times)
    : callback_(new std::function<void(int)>(std::forward<F>(func))) {
  static auto bindings_tests_Counted_create = DynamicLookup::instance().getFunc<internal::CountedPtr(*)(internal::ErrorInfo*, IndexCallbackFunction, void*, int)>("bindings_tests_Counted_create");
  internal::ErrorInfo error_ = {};
  ptr_ = bindings_tests_Counted_create(&error_, &internal::invokeIndexCallbackFunction<std::function<void(int)>>, callback_.get(), times);
  checkError(error_);
  checkNotNull(ptr_, "Could not create Counted");
}
inline int Counted::getClosedCount() noexcept {
  static auto bindings_tests_Counted_getClosedCount = DynamicLookup::instance().getFunc<int(*)()>("bindings_tests_Counted_getClosedCount");
  return bindings_tests_Counted_getClosedCount();
}
inline void Counted::cleanup() noexcept {
  if (ptr_ != nullptr) {
    static auto bindings_tests_Counted_release = DynamicLookup::instance().getFunc<void(*)(internal::CountedPtr)>("bindings_tests_Counted_release");
    bindings_tests_Counted_release(ptr_);
  }
}
inline Counted::Counted(Counted&& from) noexcept : ptr_(from.ptr_), callback_(std::move(from.callback_)) {
  from.ptr_ = nullptr;
};
inline Counted& Counted::operator=(Counted&& from) noexcept {
  cleanup();
  ptr_ = from.ptr_;
  from.ptr_ = nullptr;
  callback_ = std::move(from.callback_);
  return *this;
};
inline Counted::~Counted() noexcept {
  cleanup();
}

// Counter
inline Counter::Counter(int start) {
  static auto bindings_tests_Counter_create = DynamicLookup::instance().getFunc<internal::CounterPtr(*)(internal::ErrorInfo*, int)>("bindings_tests_Counter_create");
  internal::ErrorInfo error_ = {};
  ptr_ = bindings_tests_Counter_create(&error_, start);
  checkError(error_);
  checkNotNull(ptr_, "Could not create Counter");
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

// Naming
inline Naming::Naming(int startValue) {
  static auto custom_prefix_Naming_create = DynamicLookup::instance().getFunc<internal::NamingPtr(*)(internal::ErrorInfo*, int)>("custom_prefix_Naming_create");
  internal::ErrorInfo error_ = {};
  ptr_ = custom_prefix_Naming_create(&error_, startValue);
  checkError(error_);
  checkNotNull(ptr_, "Could not create Naming");
}
inline int Naming::getStartValue() const noexcept {
  static auto custom_prefix_Naming_get_start_value = DynamicLookup::instance().getFunc<int(*)(internal::NamingPtr)>("custom_prefix_Naming_get_start_value");
  return custom_prefix_Naming_get_start_value(ptr_);
}
inline int Naming::addTwoNumbers(int first, int second) noexcept {
  static auto custom_prefix_Naming_add_two_numbers = DynamicLookup::instance().getFunc<int(*)(int, int)>("custom_prefix_Naming_add_two_numbers");
  return custom_prefix_Naming_add_two_numbers(first, second);
}
inline void Naming::cleanup() noexcept {
  if (ptr_ != nullptr) {
    static auto custom_prefix_Naming_release = DynamicLookup::instance().getFunc<void(*)(internal::NamingPtr)>("custom_prefix_Naming_release");
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

// Notified
inline Notified::Notified(NotifyCallbackFunction func, void* userData) {
  static auto bindings_tests_Notified_create = DynamicLookup::instance().getFunc<internal::NotifiedPtr(*)(internal::ErrorInfo*, NotifyCallbackFunction, void*)>("bindings_tests_Notified_create");
  internal::ErrorInfo error_ = {};
  ptr_ = bindings_tests_Notified_create(&error_, func, userData);
  checkError(error_);
  checkNotNull(ptr_, "Could not create Notified");
}
template <typename F, typename Enable>
inline Notified::Notified(F&& func)
    : callback_(new std::function<void()>(std::forward<F>(func))) {
  static auto bindings_tests_Notified_create = DynamicLookup::instance().getFunc<internal::NotifiedPtr(*)(internal::ErrorInfo*, NotifyCallbackFunction, void*)>("bindings_tests_Notified_create");
  internal::ErrorInfo error_ = {};
  ptr_ = bindings_tests_Notified_create(&error_, &internal::invokeNotifyCallbackFunction<std::function<void()>>, callback_.get());
  checkError(error_);
  checkNotNull(ptr_, "Could not create Notified");
}
inline void Notified::cleanup() noexcept {
  if (ptr_ != nullptr) {
    static auto bindings_tests_Notified_release = DynamicLookup::instance().getFunc<void(*)(internal::NotifiedPtr)>("bindings_tests_Notified_release");
    bindings_tests_Notified_release(ptr_);
  }
}
inline Notified::Notified(Notified&& from) noexcept : ptr_(from.ptr_), callback_(std::move(from.callback_)) {
  from.ptr_ = nullptr;
};
inline Notified& Notified::operator=(Notified&& from) noexcept {
  cleanup();
  ptr_ = from.ptr_;
  from.ptr_ = nullptr;
  callback_ = std::move(from.callback_);
  return *this;
};
inline Notified::~Notified() noexcept {
  cleanup();
}

// PlainStored
inline PlainStored::PlainStored(PlainIndexCallbackFunction func, size_t times) {
  static auto bindings_tests_PlainStored_create = DynamicLookup::instance().getFunc<internal::PlainStoredPtr(*)(internal::ErrorInfo*, PlainIndexCallbackFunction, size_t)>("bindings_tests_PlainStored_create");
  internal::ErrorInfo error_ = {};
  ptr_ = bindings_tests_PlainStored_create(&error_, func, times);
  checkError(error_);
  checkNotNull(ptr_, "Could not create PlainStored");
}
inline void PlainStored::fire(int index) noexcept {
  static auto bindings_tests_PlainStored_fire = DynamicLookup::instance().getFunc<void(*)(internal::PlainStoredPtr, int)>("bindings_tests_PlainStored_fire");
  bindings_tests_PlainStored_fire(ptr_, index);
}
inline int PlainStored::getClosedCount() noexcept {
  static auto bindings_tests_PlainStored_getClosedCount = DynamicLookup::instance().getFunc<int(*)()>("bindings_tests_PlainStored_getClosedCount");
  return bindings_tests_PlainStored_getClosedCount();
}
inline void PlainStored::cleanup() noexcept {
  if (ptr_ != nullptr) {
    static auto bindings_tests_PlainStored_release = DynamicLookup::instance().getFunc<void(*)(internal::PlainStoredPtr)>("bindings_tests_PlainStored_release");
    bindings_tests_PlainStored_release(ptr_);
  }
}
inline PlainStored::PlainStored(PlainStored&& from) noexcept : ptr_(from.ptr_) {
  from.ptr_ = nullptr;
};
inline PlainStored& PlainStored::operator=(PlainStored&& from) noexcept {
  cleanup();
  ptr_ = from.ptr_;
  from.ptr_ = nullptr;
  return *this;
};
inline PlainStored::~PlainStored() noexcept {
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
namespace internal {
// The null check happens here because the base class gets initialized before the constructor body runs
inline internal::CirclePtr createCirclePointer(double radius) {
  static auto bindings_tests_Circle_create = DynamicLookup::instance().getFunc<internal::CirclePtr(*)(internal::ErrorInfo*, double)>("bindings_tests_Circle_create");
  internal::ErrorInfo error_ = {};
  auto ptr = bindings_tests_Circle_create(&error_, radius);
  checkError(error_);
  checkNotNull(ptr, "Could not create Circle");
  return ptr;
}
}
inline Circle::Circle(double radius) : Circle(internal::createCirclePointer(radius)) {}
inline double Circle::getRadius() const noexcept {
  static auto bindings_tests_Circle_getRadius = DynamicLookup::instance().getFunc<double(*)(internal::CirclePtr)>("bindings_tests_Circle_getRadius");
  return bindings_tests_Circle_getRadius(ptr_);
}
inline CircleColor Circle::getColor() const noexcept {
  static auto bindings_tests_Circle_getColor = DynamicLookup::instance().getFunc<CircleColor(*)(internal::CirclePtr)>("bindings_tests_Circle_getColor");
  return bindings_tests_Circle_getColor(ptr_);
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
namespace internal {
// The null check happens here because the base class gets initialized before the constructor body runs
inline internal::SquarePtr createSquarePointer(double side) {
  static auto bindings_tests_Square_create = DynamicLookup::instance().getFunc<internal::SquarePtr(*)(internal::ErrorInfo*, double)>("bindings_tests_Square_create");
  internal::ErrorInfo error_ = {};
  auto ptr = bindings_tests_Square_create(&error_, side);
  checkError(error_);
  checkNotNull(ptr, "Could not create Square");
  return ptr;
}
}
inline Square::Square(double side) : Square(internal::createSquarePointer(side)) {}
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

// Stored
inline Stored::Stored(StoredCallbackFunction func, void* userData) {
  static auto bindings_tests_Stored_create = DynamicLookup::instance().getFunc<internal::StoredPtr(*)(internal::ErrorInfo*, StoredCallbackFunction, void*)>("bindings_tests_Stored_create");
  internal::ErrorInfo error_ = {};
  ptr_ = bindings_tests_Stored_create(&error_, func, userData);
  checkError(error_);
  checkNotNull(ptr_, "Could not create Stored");
}
template <typename F, typename Enable>
inline Stored::Stored(F&& func)
    : callback_(new std::function<void(int)>(std::forward<F>(func))) {
  static auto bindings_tests_Stored_create = DynamicLookup::instance().getFunc<internal::StoredPtr(*)(internal::ErrorInfo*, StoredCallbackFunction, void*)>("bindings_tests_Stored_create");
  internal::ErrorInfo error_ = {};
  ptr_ = bindings_tests_Stored_create(&error_, &internal::invokeStoredCallbackFunction<std::function<void(int)>>, callback_.get());
  checkError(error_);
  checkNotNull(ptr_, "Could not create Stored");
}
inline void Stored::fire(int index) noexcept {
  static auto bindings_tests_Stored_fire = DynamicLookup::instance().getFunc<void(*)(internal::StoredPtr, int)>("bindings_tests_Stored_fire");
  bindings_tests_Stored_fire(ptr_, index);
}
inline int Stored::getClosedCount() noexcept {
  static auto bindings_tests_Stored_getClosedCount = DynamicLookup::instance().getFunc<int(*)()>("bindings_tests_Stored_getClosedCount");
  return bindings_tests_Stored_getClosedCount();
}
inline void Stored::cleanup() noexcept {
  if (ptr_ != nullptr) {
    static auto bindings_tests_Stored_release = DynamicLookup::instance().getFunc<void(*)(internal::StoredPtr)>("bindings_tests_Stored_release");
    bindings_tests_Stored_release(ptr_);
  }
}
inline Stored::Stored(Stored&& from) noexcept : ptr_(from.ptr_), callback_(std::move(from.callback_)) {
  from.ptr_ = nullptr;
};
inline Stored& Stored::operator=(Stored&& from) noexcept {
  cleanup();
  ptr_ = from.ptr_;
  from.ptr_ = nullptr;
  callback_ = std::move(from.callback_);
  return *this;
};
inline Stored::~Stored() noexcept {
  cleanup();
}

// Arrays
inline double arrays::sumFloat64(const double* values, size_t count) noexcept {
  static auto bindings_tests_Arrays_sumFloat64 = DynamicLookup::instance().getFunc<double(*)(const double*, size_t)>("bindings_tests_Arrays_sumFloat64");
  return bindings_tests_Arrays_sumFloat64(values, count);
}
inline double arrays::sumFloat64(const std::vector<double>& values, size_t count) noexcept {
  return sumFloat64(values.data(), count);
}
inline int arrays::sumInt32(const int* values, size_t count) noexcept {
  static auto bindings_tests_Arrays_sumInt32 = DynamicLookup::instance().getFunc<int(*)(const int*, size_t)>("bindings_tests_Arrays_sumInt32");
  return bindings_tests_Arrays_sumInt32(values, count);
}
inline void arrays::fillFloat64(double* target, size_t count, double value) noexcept {
  static auto bindings_tests_Arrays_fillFloat64 = DynamicLookup::instance().getFunc<void(*)(double*, size_t, double)>("bindings_tests_Arrays_fillFloat64");
  bindings_tests_Arrays_fillFloat64(target, count, value);
}
inline void arrays::cumulativeSum(const double* source, double* target, size_t count) noexcept {
  static auto bindings_tests_Arrays_cumulativeSum = DynamicLookup::instance().getFunc<void(*)(const double*, double*, size_t)>("bindings_tests_Arrays_cumulativeSum");
  bindings_tests_Arrays_cumulativeSum(source, target, count);
}
inline void arrays::cumulativeSum(const std::vector<double>& source, double* target, size_t count) noexcept {
  cumulativeSum(source.data(), target, count);
}
inline void arrays::reverseFloat64(double* values, size_t count) noexcept {
  static auto bindings_tests_Arrays_reverseFloat64 = DynamicLookup::instance().getFunc<void(*)(double*, size_t)>("bindings_tests_Arrays_reverseFloat64");
  bindings_tests_Arrays_reverseFloat64(values, count);
}
inline double arrays::dotProduct(internal::DoubleSpan x, internal::DoubleSpan y) {
  static auto bindings_tests_Arrays_dotProduct = DynamicLookup::instance().getFunc<double(*)(internal::ErrorInfo*, internal::DoubleSpan, internal::DoubleSpan)>("bindings_tests_Arrays_dotProduct");
  internal::ErrorInfo error_ = {};
  auto result_ = bindings_tests_Arrays_dotProduct(&error_, x, y);
  checkError(error_);
  return result_;
}
inline double arrays::dotProduct(const std::vector<double>& x, const std::vector<double>& y) {
  return dotProduct({x.data(), x.size()}, {y.data(), y.size()});
}
inline std::vector<double> arrays::addOffset(internal::DoubleSpan values, double offset) noexcept {
  static auto bindings_tests_Arrays_addOffset = DynamicLookup::instance().getFunc<internal::DoubleSpan(*)(internal::DoubleSpan, double)>("bindings_tests_Arrays_addOffset");
  auto struct_ = bindings_tests_Arrays_addOffset(values, offset);
  return std::vector<double>(struct_.data, struct_.data + struct_.length); // copy before the next call invalidates it
}
inline std::vector<double> arrays::addOffset(const std::vector<double>& values, double offset) noexcept {
  return addOffset({values.data(), values.size()}, offset);
}
inline std::vector<int32_t> arrays::addOffsetInt32(internal::IntSpan values, int offset) noexcept {
  static auto bindings_tests_Arrays_addOffsetInt32 = DynamicLookup::instance().getFunc<internal::IntSpan(*)(internal::IntSpan, int)>("bindings_tests_Arrays_addOffsetInt32");
  auto struct_ = bindings_tests_Arrays_addOffsetInt32(values, offset);
  return std::vector<int32_t>(struct_.data, struct_.data + struct_.length); // copy before the next call invalidates it
}
inline std::vector<int32_t> arrays::addOffsetInt32(const std::vector<int32_t>& values, int offset) noexcept {
  return addOffsetInt32({values.data(), values.size()}, offset);
}
inline std::vector<float> arrays::addOffsetFloat32(internal::FloatSpan values, float offset) noexcept {
  static auto bindings_tests_Arrays_addOffsetFloat32 = DynamicLookup::instance().getFunc<internal::FloatSpan(*)(internal::FloatSpan, float)>("bindings_tests_Arrays_addOffsetFloat32");
  auto struct_ = bindings_tests_Arrays_addOffsetFloat32(values, offset);
  return std::vector<float>(struct_.data, struct_.data + struct_.length); // copy before the next call invalidates it
}
inline std::vector<float> arrays::addOffsetFloat32(const std::vector<float>& values, float offset) noexcept {
  return addOffsetFloat32({values.data(), values.size()}, offset);
}
inline int arrays::sumUint8(uint8_t* values, size_t count) noexcept {
  static auto bindings_tests_Arrays_sumUint8 = DynamicLookup::instance().getFunc<int(*)(uint8_t*, size_t)>("bindings_tests_Arrays_sumUint8");
  return bindings_tests_Arrays_sumUint8(values, count);
}
inline std::vector<uint8_t> arrays::addOffsetUint8(internal::ByteSpan values, char offset) noexcept {
  static auto bindings_tests_Arrays_addOffsetUint8 = DynamicLookup::instance().getFunc<internal::ByteSpan(*)(internal::ByteSpan, char)>("bindings_tests_Arrays_addOffsetUint8");
  auto struct_ = bindings_tests_Arrays_addOffsetUint8(values, offset);
  return std::vector<uint8_t>(struct_.data, struct_.data + struct_.length); // copy before the next call invalidates it
}
inline std::vector<uint8_t> arrays::addOffsetUint8(const std::vector<uint8_t>& values, char offset) noexcept {
  return addOffsetUint8({values.data(), values.size()}, offset);
}
inline std::vector<double> arrays::getSequence(size_t count) noexcept {
  static auto bindings_tests_Arrays_getSequence = DynamicLookup::instance().getFunc<internal::DoubleSpan(*)(size_t)>("bindings_tests_Arrays_getSequence");
  auto struct_ = bindings_tests_Arrays_getSequence(count);
  return std::vector<double>(struct_.data, struct_.data + struct_.length); // copy before the next call invalidates it
}

// Callbacks
inline void callbacks::invokeTimes(IndexCallbackFunction func, void* userData, size_t times) noexcept {
  static auto bindings_tests_Callbacks_invokeTimes = DynamicLookup::instance().getFunc<void(*)(IndexCallbackFunction, void*, size_t)>("bindings_tests_Callbacks_invokeTimes");
  bindings_tests_Callbacks_invokeTimes(func, userData, times);
}
inline void callbacks::invokeTimes(const std::function<void(int)>& func, size_t times) noexcept {
  invokeTimes(&internal::invokeIndexCallbackFunction<const std::function<void(int)>>, internal::toUserData(func), times);
}
inline void callbacks::invokeOnce(IndexCallbackFunction func, void* userData, int index) noexcept {
  static auto bindings_tests_Callbacks_invokeOnce = DynamicLookup::instance().getFunc<void(*)(IndexCallbackFunction, void*, int)>("bindings_tests_Callbacks_invokeOnce");
  bindings_tests_Callbacks_invokeOnce(func, userData, index);
}
inline void callbacks::invokeOnce(const std::function<void(int)>& func, int index) noexcept {
  invokeOnce(&internal::invokeIndexCallbackFunction<const std::function<void(int)>>, internal::toUserData(func), index);
}
inline void callbacks::invokeChecked(IndexCallbackFunction func, void* userData, int index) {
  static auto bindings_tests_Callbacks_invokeChecked = DynamicLookup::instance().getFunc<void(*)(internal::ErrorInfo*, IndexCallbackFunction, void*, int)>("bindings_tests_Callbacks_invokeChecked");
  internal::ErrorInfo error_ = {};
  bindings_tests_Callbacks_invokeChecked(&error_, func, userData, index);
  checkError(error_);
}
inline void callbacks::invokeChecked(const std::function<void(int)>& func, int index) {
  invokeChecked(&internal::invokeIndexCallbackFunction<const std::function<void(int)>>, internal::toUserData(func), index);
}
inline void callbacks::invokeOnWorkerThread(IndexCallbackFunction func, void* userData, int index) noexcept {
  static auto bindings_tests_Callbacks_invokeOnWorkerThread = DynamicLookup::instance().getFunc<void(*)(IndexCallbackFunction, void*, int)>("bindings_tests_Callbacks_invokeOnWorkerThread");
  bindings_tests_Callbacks_invokeOnWorkerThread(func, userData, index);
}
inline void callbacks::invokeOnWorkerThread(const std::function<void(int)>& func, int index) noexcept {
  invokeOnWorkerThread(&internal::invokeIndexCallbackFunction<const std::function<void(int)>>, internal::toUserData(func), index);
}
inline void callbacks::notifyOnce(NotifyCallbackFunction func, void* userData) noexcept {
  static auto bindings_tests_Callbacks_notifyOnce = DynamicLookup::instance().getFunc<void(*)(NotifyCallbackFunction, void*)>("bindings_tests_Callbacks_notifyOnce");
  bindings_tests_Callbacks_notifyOnce(func, userData);
}
inline void callbacks::notifyOnce(const std::function<void()>& func) noexcept {
  notifyOnce(&internal::invokeNotifyCallbackFunction<const std::function<void()>>, internal::toUserData(func));
}
inline double callbacks::applyScaled(ScaleCallbackFunction func, void* userData, double value) noexcept {
  static auto bindings_tests_Callbacks_applyScaled = DynamicLookup::instance().getFunc<double(*)(ScaleCallbackFunction, void*, double)>("bindings_tests_Callbacks_applyScaled");
  return bindings_tests_Callbacks_applyScaled(func, userData, value);
}
inline double callbacks::applyScaled(const std::function<double(double)>& func, double value) noexcept {
  return applyScaled(&internal::invokeScaleCallbackFunction<const std::function<double(double)>>, internal::toUserData(func), value);
}
inline void callbacks::transformOnce(TransformCallbackFunction func, void* userData, const double* input, double* output) noexcept {
  static auto bindings_tests_Callbacks_transformOnce = DynamicLookup::instance().getFunc<void(*)(TransformCallbackFunction, void*, const double*, double*)>("bindings_tests_Callbacks_transformOnce");
  bindings_tests_Callbacks_transformOnce(func, userData, input, output);
}
inline void callbacks::transformOnce(TransformCallbackFunction func, void* userData, const std::vector<double>& input, double* output) noexcept {
  transformOnce(func, userData, input.data(), output);
}
inline void callbacks::transformOnce(const std::function<void(const double*, double*)>& func, const double* input, double* output) noexcept {
  transformOnce(&internal::invokeTransformCallbackFunction<const std::function<void(const double*, double*)>>, internal::toUserData(func), input, output);
}
inline void callbacks::invokePlainTimes(PlainIndexCallbackFunction func, size_t times) noexcept {
  static auto bindings_tests_Callbacks_invokePlainTimes = DynamicLookup::instance().getFunc<void(*)(PlainIndexCallbackFunction, size_t)>("bindings_tests_Callbacks_invokePlainTimes");
  bindings_tests_Callbacks_invokePlainTimes(func, times);
}
inline void callbacks::invokePlainChecked(PlainIndexCallbackFunction func, int index) {
  static auto bindings_tests_Callbacks_invokePlainChecked = DynamicLookup::instance().getFunc<void(*)(internal::ErrorInfo*, PlainIndexCallbackFunction, int)>("bindings_tests_Callbacks_invokePlainChecked");
  internal::ErrorInfo error_ = {};
  bindings_tests_Callbacks_invokePlainChecked(&error_, func, index);
  checkError(error_);
}
inline void callbacks::invokePlainOnWorkerThread(PlainIndexCallbackFunction func, int index) noexcept {
  static auto bindings_tests_Callbacks_invokePlainOnWorkerThread = DynamicLookup::instance().getFunc<void(*)(PlainIndexCallbackFunction, int)>("bindings_tests_Callbacks_invokePlainOnWorkerThread");
  bindings_tests_Callbacks_invokePlainOnWorkerThread(func, index);
}
inline void callbacks::notifyPlainOnce(PlainNotifyCallbackFunction func) noexcept {
  static auto bindings_tests_Callbacks_notifyPlainOnce = DynamicLookup::instance().getFunc<void(*)(PlainNotifyCallbackFunction)>("bindings_tests_Callbacks_notifyPlainOnce");
  bindings_tests_Callbacks_notifyPlainOnce(func);
}

// Errors
inline void errors::requireNonNegative(int value) {
  static auto bindings_tests_Errors_requireNonNegative = DynamicLookup::instance().getFunc<void(*)(internal::ErrorInfo*, int)>("bindings_tests_Errors_requireNonNegative");
  internal::ErrorInfo error_ = {};
  bindings_tests_Errors_requireNonNegative(&error_, value);
  checkError(error_);
}
inline int errors::divideInt32(int a, int b) {
  static auto bindings_tests_Errors_divideInt32 = DynamicLookup::instance().getFunc<int(*)(internal::ErrorInfo*, int, int)>("bindings_tests_Errors_divideInt32");
  internal::ErrorInfo error_ = {};
  auto result_ = bindings_tests_Errors_divideInt32(&error_, a, b);
  checkError(error_);
  return result_;
}
inline double errors::failingFloat64(bool shouldFail) {
  static auto bindings_tests_Errors_failingFloat64 = DynamicLookup::instance().getFunc<double(*)(internal::ErrorInfo*, int)>("bindings_tests_Errors_failingFloat64");
  internal::ErrorInfo error_ = {};
  auto result_ = bindings_tests_Errors_failingFloat64(&error_, shouldFail);
  checkError(error_);
  return result_;
}
inline bool errors::failingBool(bool shouldFail) {
  static auto bindings_tests_Errors_failingBool = DynamicLookup::instance().getFunc<int(*)(internal::ErrorInfo*, int)>("bindings_tests_Errors_failingBool");
  internal::ErrorInfo error_ = {};
  auto result_ = bindings_tests_Errors_failingBool(&error_, shouldFail);
  checkError(error_);
  return result_;
}
inline std::string errors::failingString(bool shouldFail) {
  static auto bindings_tests_Errors_failingString = DynamicLookup::instance().getFunc<const char*(*)(internal::ErrorInfo*, int)>("bindings_tests_Errors_failingString");
  internal::ErrorInfo error_ = {};
  auto ptr = bindings_tests_Errors_failingString(&error_, shouldFail);
  checkError(error_);
  return !ptr ? std::string() : std::string(ptr); // copy utf8
}
inline int errors::failWithoutMessage() {
  static auto bindings_tests_Errors_failWithoutMessage = DynamicLookup::instance().getFunc<int(*)(internal::ErrorInfo*)>("bindings_tests_Errors_failWithoutMessage");
  internal::ErrorInfo error_ = {};
  auto result_ = bindings_tests_Errors_failWithoutMessage(&error_);
  checkError(error_);
  return result_;
}
inline void errors::neverThrows() noexcept {
  static auto bindings_tests_Errors_neverThrows = DynamicLookup::instance().getFunc<void(*)()>("bindings_tests_Errors_neverThrows");
  bindings_tests_Errors_neverThrows();
}

// Indices
inline int indices::echo(int index) noexcept {
  static auto bindings_tests_Indices_echo = DynamicLookup::instance().getFunc<int(*)(int)>("bindings_tests_Indices_echo");
  return bindings_tests_Indices_echo(index);
}
inline int indices::countUpTo(int index) noexcept {
  static auto bindings_tests_Indices_countUpTo = DynamicLookup::instance().getFunc<int(*)(int)>("bindings_tests_Indices_countUpTo");
  return bindings_tests_Indices_countUpTo(index);
}
inline int indices::lastIndexOf(int count) noexcept {
  static auto bindings_tests_Indices_lastIndexOf = DynamicLookup::instance().getFunc<int(*)(int)>("bindings_tests_Indices_lastIndexOf");
  return bindings_tests_Indices_lastIndexOf(count);
}
inline int indices::offsetFrom(int row, int col) noexcept {
  static auto bindings_tests_Indices_offsetFrom = DynamicLookup::instance().getFunc<int(*)(int, int)>("bindings_tests_Indices_offsetFrom");
  return bindings_tests_Indices_offsetFrom(row, col);
}
inline int indices::fromRow(int row) noexcept {
  static auto bindings_tests_Indices_fromRow = DynamicLookup::instance().getFunc<int(*)(int)>("bindings_tests_Indices_fromRow");
  return bindings_tests_Indices_fromRow(row);
}
inline size_t indices::echoSize(size_t index) noexcept {
  static auto bindings_tests_Indices_echoSize = DynamicLookup::instance().getFunc<size_t(*)(size_t)>("bindings_tests_Indices_echoSize");
  return bindings_tests_Indices_echoSize(index);
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
  static auto bindings_tests_Isolated_alwaysFails = DynamicLookup::instance().getFunc<void(*)(internal::ErrorInfo*)>("bindings_tests_Isolated_alwaysFails");
  internal::ErrorInfo error_ = {};
  bindings_tests_Isolated_alwaysFails(&error_);
  checkError(error_);
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
inline double overloads::gain(bool invert, double scale) noexcept {
  static auto bindings_tests_Overloads_gain = DynamicLookup::instance().getFunc<double(*)(int, double)>("bindings_tests_Overloads_gain");
  return bindings_tests_Overloads_gain(invert, scale);
}
inline int overloads::echoIndex(int index) noexcept {
  static auto bindings_tests_Overloads_echoIndex = DynamicLookup::instance().getFunc<int(*)(int)>("bindings_tests_Overloads_echoIndex");
  return bindings_tests_Overloads_echoIndex(index);
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
inline std::string palette::describe(Color color, Marker marker) noexcept {
  static auto bindings_tests_Palette_describe = DynamicLookup::instance().getFunc<const char*(*)(Color, Marker)>("bindings_tests_Palette_describe");
  auto ptr = bindings_tests_Palette_describe(color, marker);
  return !ptr ? std::string() : std::string(ptr); // copy utf8
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
inline std::string strings::join(const char** values, size_t count, const char* separator) noexcept {
  static auto bindings_tests_Strings_join = DynamicLookup::instance().getFunc<const char*(*)(const char**, size_t, const char*)>("bindings_tests_Strings_join");
  auto ptr = bindings_tests_Strings_join(values, count, separator);
  return !ptr ? std::string() : std::string(ptr); // copy utf8
}
inline std::string strings::join(const char** values, size_t count, const std::string& separator) noexcept {
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

// Structs
inline FieldTypes structs::fillFieldTypes(unsigned short unit, short* shorts, long long int* longs, void* context) noexcept {
  static auto bindings_tests_Structs_fillFieldTypes = DynamicLookup::instance().getFunc<FieldTypes(*)(unsigned short, short*, long long int*, void*)>("bindings_tests_Structs_fillFieldTypes");
  return bindings_tests_Structs_fillFieldTypes(unit, shorts, longs, context);
}
inline double structs::totalPower(StatusInfo status, double currentAmps) noexcept {
  static auto bindings_tests_Structs_totalPower = DynamicLookup::instance().getFunc<double(*)(StatusInfo, double)>("bindings_tests_Structs_totalPower");
  return bindings_tests_Structs_totalPower(status, currentAmps);
}
inline double structs::checkedVoltage(StatusInfo status) {
  static auto bindings_tests_Structs_checkedVoltage = DynamicLookup::instance().getFunc<double(*)(internal::ErrorInfo*, StatusInfo)>("bindings_tests_Structs_checkedVoltage");
  internal::ErrorInfo error_ = {};
  auto result_ = bindings_tests_Structs_checkedVoltage(&error_, status);
  checkError(error_);
  return result_;
}
inline StatusInfo structs::getStatus(int index) noexcept {
  static auto bindings_tests_Structs_getStatus = DynamicLookup::instance().getFunc<StatusInfo(*)(int)>("bindings_tests_Structs_getStatus");
  return bindings_tests_Structs_getStatus(index);
}
inline StatusInfo structs::getCheckedStatus(int index) {
  static auto bindings_tests_Structs_getCheckedStatus = DynamicLookup::instance().getFunc<StatusInfo(*)(internal::ErrorInfo*, int)>("bindings_tests_Structs_getCheckedStatus");
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
