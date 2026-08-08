# -*- coding: utf-8 -*-
"""Runtime test for the generated Python wrapper of the bindings_tests library.

Asserts the same value table as the C, C++ and MATLAB suites, so a divergence
between languages shows up as a failure here.

Run after ``mvn verify -Pnative`` from the bindings-tests module root::

    python -m unittest discover -s src/test/python

The generated wrapper loads the shared library from
``<bindings>/lib/<platform>/`` relative to bindings_tests.py, so the built
library has to be copied there first (see .github/workflows/native.yml).
"""

import ctypes
import gc
import math
import os
import sys
import unittest

# The generated module lives in the build output, so put it on the path first
_THIS_DIR = os.path.dirname(os.path.abspath(__file__))
_BINDINGS_DIR = os.path.abspath(
    os.path.join(_THIS_DIR, "..", "..", "..", "target", "image", "static", "bindings")
)
if _BINDINGS_DIR not in sys.path:
    sys.path.insert(0, _BINDINGS_DIR)

import bindings_tests  # noqa: E402

try:
    import numpy as np
except ImportError:  # pragma: no cover
    np = None


class MathTest(unittest.TestCase):
    """Every primitive width that can cross the ABI, plus size_t."""

    def test_bool(self):
        self.assertEqual(False, bindings_tests.Math.negate(True))
        self.assertEqual(True, bindings_tests.Math.negate(False))

    def test_char(self):
        # A Java char maps to a 16 bit integer, so the wrapper takes and returns
        # a code unit rather than a one character string.
        self.assertEqual(ord("B"), bindings_tests.Math.next_char(ord("A")))

    def test_integers(self):
        self.assertEqual(120, bindings_tests.Math.add_int8(100, 20))
        self.assertEqual(30700, bindings_tests.Math.add_int16(30000, 700))
        self.assertEqual(5, bindings_tests.Math.add_int32(2, 3))
        # 2^33 + 1. Catches platforms that truncate a Java long to 32 bit.
        self.assertEqual(8589934593, bindings_tests.Math.add_int64(8589934592, 1))

    def test_floats(self):
        self.assertEqual(0.75, bindings_tests.Math.add_float32(0.5, 0.25))
        self.assertEqual(0.75, bindings_tests.Math.add_float64(0.5, 0.25))

    def test_size(self):
        self.assertEqual(9, bindings_tests.Math.add_size(4, 5))
        self.assertEqual(7, bindings_tests.Math.size_to_int32(7))

    def test_limits(self):
        self.assertEqual(2147483647, bindings_tests.Math.max_int32())
        self.assertEqual(9223372036854775807, bindings_tests.Math.max_size())


class CounterTest(unittest.TestCase):
    """Object handle lifecycle: constructor, methods, properties, destructor."""

    def test_instance(self):
        bindings_tests.Counter.reset_closed_count()
        self.assertEqual(0, bindings_tests.Counter.get_closed_count())

        counter = bindings_tests.Counter(10)
        counter.increment()
        counter.increment()
        self.assertEqual(12, counter.value)

        counter.add(5)
        self.assertEqual(17, counter.value)

        counter.value = 3
        self.assertEqual(3, counter.value)

        self.assertFalse(counter.is_closed())

        # The single constructor defaults 'start' to 0, so the no-argument form works
        default_counter = bindings_tests.Counter()
        self.assertEqual(0, default_counter.value)

        # Dropping the last reference runs __del__, which releases the handle
        del counter
        del default_counter
        gc.collect()
        self.assertEqual(2, bindings_tests.Counter.get_closed_count())


class NamingTest(unittest.TestCase):
    """cPrefix and useCamelCase, which rename C symbols and nothing else."""

    def test_wrapper_is_unaffected(self):
        naming = bindings_tests.Naming(7)
        self.assertEqual(7, naming.get_start_value())
        self.assertEqual(0, bindings_tests.Naming().get_start_value())
        self.assertEqual(5, bindings_tests.Naming.add_two_numbers(2, 3))

    def test_symbols_carry_the_custom_prefix(self):
        # Python is snake case regardless, so the wrapper alone cannot tell the two
        # options apart. The registered symbol can, and it is what the C suite links.
        self.assertTrue(hasattr(bindings_tests.libbindings_tests, "custom_prefix_Naming_get_start_value"))
        self.assertFalse(hasattr(bindings_tests.libbindings_tests, "bindings_tests_Naming_getStartValue"))


class StringsTest(unittest.TestCase):
    """String marshalling in both directions, plus char** arrays."""

    def test_echo(self):
        self.assertEqual("hello", bindings_tests.Strings.echo("hello"))

    def test_echo_non_ascii(self):
        # This file is UTF-8. The literal must survive the round trip unchanged.
        value = "naïve café"
        self.assertEqual(value, bindings_tests.Strings.echo(value))

    def test_concat(self):
        self.assertEqual("abcd", bindings_tests.Strings.concat("ab", "cd"))

    def test_lengths(self):
        # 6 UTF-8 bytes but only 5 UTF-16 code units
        self.assertEqual(6, bindings_tests.Strings.utf8_length("naïve"))
        self.assertEqual(5, bindings_tests.Strings.utf16_length("naïve"))

    def test_empty_and_null(self):
        self.assertEqual("", bindings_tests.Strings.empty())
        # The wrapper turns a null char* into an empty string, so null and ""
        # are indistinguishable here. The C suite checks the pointer itself.
        self.assertEqual("", bindings_tests.Strings.null_string())

    def test_join(self):
        self.assertEqual("a-b-c", bindings_tests.Strings.join(["a", "b", "c"], 3, "-"))

    def test_repeat_grows_scratch_buffer(self):
        # 200 characters, which forces the 128 byte scratch buffer to grow
        self.assertEqual(200, len(bindings_tests.Strings.repeat("0123456789", 20)))


@unittest.skipIf(np is None, "numpy is required for the array bindings")
class ArraysTest(unittest.TestCase):
    """The (pointer, length) parameter pattern for input and output buffers.

    The generated wrapper converts sequences with numpy, so the buffers below
    are numpy arrays to make sure the library writes into memory we can read
    back rather than into a temporary copy.
    """

    def test_sum_float64(self):
        values = np.array([1.0, 2.0, 3.5], dtype=np.float64)
        self.assertEqual(6.5, bindings_tests.Arrays.sum_float64(values, 3))

    def test_sum_int32(self):
        values = np.array([1, 2, 3], dtype=np.int32)
        self.assertEqual(6, bindings_tests.Arrays.sum_int32(values, 3))

    def test_fill_float64(self):
        target = np.zeros(4, dtype=np.float64)
        bindings_tests.Arrays.fill_float64(target, 4, 2.5)
        self.assertEqual([2.5, 2.5, 2.5, 2.5], list(target))

    def test_cumulative_sum(self):
        source = np.array([1.0, 2.0, 3.0, 4.0], dtype=np.float64)
        target = np.zeros(4, dtype=np.float64)
        bindings_tests.Arrays.cumulative_sum(source, target, 4)
        self.assertEqual([1.0, 3.0, 6.0, 10.0], list(target))

    def test_reverse_float64(self):
        values = np.array([1.0, 2.0, 3.0, 4.0], dtype=np.float64)
        bindings_tests.Arrays.reverse_float64(values, 4)
        self.assertEqual([4.0, 3.0, 2.0, 1.0], list(values))


class SpansTest(unittest.TestCase):
    """Pointer and length crossing as one by-value struct, so no count arguments."""

    def test_dot_product(self):
        # Plain lists work because the wrapper converts through numpy
        self.assertEqual(32.0, bindings_tests.Arrays.dot_product([1.0, 2.0, 3.0], [4.0, 5.0, 6.0]))

    def test_length_mismatch(self):
        # The length check lives once in the Java implementation, and the wrapper
        # raises with its message rather than handing back a silent zero.
        with self.assertRaises(RuntimeError) as caught:
            bindings_tests.Arrays.dot_product([1.0, 2.0, 3.0], [4.0, 5.0])
        self.assertIn("elements", str(caught.exception))

    def test_add_offset(self):
        # Span in, numpy array out. The wrapper copies out of the library-owned
        # buffer before returning.
        result = bindings_tests.Arrays.add_offset([1.0, 2.0, 3.0], 10.0)
        self.assertEqual([11.0, 12.0, 13.0], list(result))

    def test_add_offset_int32(self):
        # Typed spans repeat the same shape per element type
        result = bindings_tests.Arrays.add_offset_int32([1, 2, 3], 10)
        self.assertEqual([11, 12, 13], list(result))

    def test_add_offset_float32(self):
        result = bindings_tests.Arrays.add_offset_float32([1.5, 2.5, 3.5], 10.0)
        self.assertEqual([11.5, 12.5, 13.5], list(result))

    def test_sum_uint8(self):
        # Raw uint8 buffer with an explicit count, the shape an image row uses.
        # The sum is unsigned, 250 counts as 250 and not -6.
        values = np.array([250, 10, 5], dtype=np.uint8)
        self.assertEqual(265, bindings_tests.Arrays.sum_uint8(values, 3))

    def test_add_offset_uint8(self):
        # Byte spans carry binary data, so an embedded zero must survive the
        # roundtrip. 246 + 10 wraps to 0 in uint8.
        result = bindings_tests.Arrays.add_offset_uint8([1, 246, 3], 10)
        self.assertEqual([11, 0, 13], list(result))

    def test_get_sequence(self):
        self.assertEqual([0.0, 1.0, 2.0, 3.0, 4.0], list(bindings_tests.Arrays.get_sequence(5)))

    def test_get_sequence_empty(self):
        self.assertEqual(0, len(bindings_tests.Arrays.get_sequence(0)))

    def test_get_sequence_grows_scratch_buffer(self):
        # 200 elements, which forces the 128 element scratch buffer to grow
        result = bindings_tests.Arrays.get_sequence(200)
        self.assertEqual(200, len(result))
        self.assertEqual(199.0, result[199])

    def test_results_survive_next_call(self):
        # Each returned array must be an independent copy of the scratch buffer
        first = bindings_tests.Arrays.get_sequence(3)
        second = bindings_tests.Arrays.add_offset([5.0, 5.0, 5.0], 1.0)
        self.assertEqual([0.0, 1.0, 2.0], list(first))
        self.assertEqual([6.0, 6.0, 6.0], list(second))


class StructsTest(unittest.TestCase):
    """A user defined struct crossing by value, surfaced as a dataclass.

    The wrapper packs the dataclass into its ctypes mirror at the call site, so
    the bool field converts to the int32 the layout declares.
    """

    def test_total_power(self):
        status = bindings_tests.StatusInfo(voltage=48.0, state=2, enabled=True)
        self.assertEqual(120.0, bindings_tests.Structs.total_power(status, 2.5))
        self.assertEqual(48.0, bindings_tests.Structs.checked_voltage(status))

    def test_state_is_read(self):
        # State 1 is not the running state, so the power is zero while the voltage still reads
        status = bindings_tests.StatusInfo(voltage=48.0, state=1, enabled=True)
        self.assertEqual(0.0, bindings_tests.Structs.total_power(status, 2.5))
        self.assertEqual(48.0, bindings_tests.Structs.checked_voltage(status))

    def test_disabled_raises(self):
        status = bindings_tests.StatusInfo(voltage=48.0, state=2, enabled=False)
        self.assertEqual(0.0, bindings_tests.Structs.total_power(status, 2.5))
        with self.assertRaises(bindings_tests.Error) as caught:
            bindings_tests.Structs.checked_voltage(status)
        self.assertIn("disabled", str(caught.exception))
        self.assertEqual("bindings_tests:IllegalArgumentException", caught.exception.id)

    def test_fill_field_types(self):
        # The dataclass skips the pointer fields, only the code unit comes back
        result = bindings_tests.Structs.fill_field_types(
            ord("A"),
            np.array([1, 2], dtype=np.int16),
            np.array([3, 4], dtype=np.int64),
            None)
        self.assertEqual(ord("A"), result.unit)

    def test_get_status_returns_a_status_info(self):
        # get_status/get_checked_status present the same struct the other way round, as
        # a dataclass return keyed by index rather than an input struct the caller builds.
        status = bindings_tests.Structs.get_status(0)
        self.assertIsInstance(status, bindings_tests.StatusInfo)
        self.assertEqual(48.0, status.voltage)
        self.assertEqual(2, status.state)
        self.assertIs(True, status.enabled)

        status = bindings_tests.Structs.get_status(1)
        self.assertEqual(12.5, status.voltage)
        self.assertEqual(1, status.state)
        self.assertIs(False, status.enabled)

    def test_get_checked_status_raises_on_negative_index(self):
        status = bindings_tests.Structs.get_checked_status(0)
        self.assertEqual(48.0, status.voltage)

        with self.assertRaises(bindings_tests.Error) as caught:
            bindings_tests.Structs.get_checked_status(-1)
        self.assertIn("negative", str(caught.exception))
        self.assertEqual("bindings_tests:IllegalArgumentException", caught.exception.id)


class ShapesTest(unittest.TestCase):
    """Inheritance, including the generated upcast."""

    def test_circle(self):
        circle = bindings_tests.Circle(2.0)
        self.assertAlmostEqual(math.pi * 4.0, circle.area(), delta=1e-9)
        self.assertEqual(0, circle.side_count())
        self.assertEqual("circle", circle.name)

    def test_square(self):
        square = bindings_tests.Square(3.0)
        self.assertEqual(9.0, square.area())
        self.assertEqual(4, square.side_count())
        self.assertEqual("square", square.name)

    def test_through_base_type(self):
        # The wrapper models the hierarchy with real Python inheritance and runs
        # the generated cast function in the subclass __init__, so calling the
        # base implementation goes through the Shape pointer.
        circle = bindings_tests.Circle(2.0)
        square = bindings_tests.Square(3.0)

        self.assertIsInstance(circle, bindings_tests.Shape)
        self.assertIsInstance(square, bindings_tests.Shape)

        self.assertAlmostEqual(math.pi * 4.0, bindings_tests.Shape.area(circle), delta=1e-9)
        self.assertEqual("circle", bindings_tests.Shape.name.fget(circle))
        self.assertEqual(9.0, bindings_tests.Shape.area(square))
        self.assertEqual("square", bindings_tests.Shape.name.fget(square))
        # TODO: uncertain. `name` is a property on Shape, so reaching the base
        # implementation explicitly needs fget. If the generator ever emits a
        # plain method instead, this becomes bindings_tests.Shape.get_name(...).


class PaletteTest(unittest.TestCase):
    """Enums, surfaced as an IntEnum with upper case names."""

    def test_next(self):
        Color = bindings_tests.Color
        self.assertEqual(Color.GREEN, bindings_tests.Palette.next(Color.RED))
        self.assertEqual(Color.BLUE, bindings_tests.Palette.next(Color.GREEN))
        self.assertEqual(Color.RED, bindings_tests.Palette.next(Color.BLUE))

    def test_name_and_ordinal(self):
        Color = bindings_tests.Color
        self.assertEqual("Green", bindings_tests.Palette.name_of(Color.GREEN))
        self.assertEqual(2, bindings_tests.Palette.ordinal_of(Color.BLUE))

    def test_describe_uses_both_enums(self):
        # Two enums declared in the same class, used in the same signature
        self.assertEqual("Red Cross", bindings_tests.Palette.describe(
            bindings_tests.Color.RED, bindings_tests.Marker.CROSS))

    def test_from_ordinal(self):
        Color = bindings_tests.Color
        self.assertEqual(Color.GREEN, bindings_tests.Palette.from_ordinal(1))
        self.assertEqual(Color.RED, bindings_tests.Palette.from_ordinal(99))


class ErrorsTest(unittest.TestCase):
    """Error propagation across the ABI."""

    def test_require_non_negative(self):
        bindings_tests.Errors.require_non_negative(5)

        # A throwing void method reports through the error struct, which the
        # wrapper turns into a bindings_tests.Error.
        with self.assertRaises(bindings_tests.Error) as caught:
            bindings_tests.Errors.require_non_negative(-1)
        self.assertIn("must not be negative", str(caught.exception))
        self.assertEqual("bindings_tests:IllegalArgumentException", caught.exception.id)

    def test_value_returns_raise(self):
        # Methods with a real return type report through the same error struct, so
        # a failure raises here too and the id travels with the message.
        self.assertEqual(5, bindings_tests.Errors.divide_int32(10, 2))
        with self.assertRaises(RuntimeError) as caught:
            bindings_tests.Errors.divide_int32(1, 0)
        self.assertIn("division by zero", str(caught.exception))
        self.assertIn("bindings_tests:ArithmeticException", str(caught.exception))

        # The id travels alongside the message as structured data
        self.assertIsInstance(caught.exception, bindings_tests.Error)
        self.assertEqual("bindings_tests:ArithmeticException", caught.exception.id)

        self.assertEqual(1.5, bindings_tests.Errors.failing_float64(False))
        with self.assertRaises(RuntimeError):
            bindings_tests.Errors.failing_float64(True)

        self.assertEqual(True, bindings_tests.Errors.failing_bool(False))
        with self.assertRaises(RuntimeError):
            bindings_tests.Errors.failing_bool(True)

        self.assertEqual("ok", bindings_tests.Errors.failing_string(False))
        with self.assertRaises(RuntimeError):
            bindings_tests.Errors.failing_string(True)

        # An exception without a message falls back to a placeholder, the id still arrives
        with self.assertRaises(RuntimeError) as caught:
            bindings_tests.Errors.fail_without_message()
        self.assertIn("unknown error", str(caught.exception))
        self.assertEqual("bindings_tests:IllegalStateException", caught.exception.id)

    def test_never_throws(self):
        # Marked Noexcept, so there is no status code to check
        bindings_tests.Errors.never_throws()

    def test_success_after_failure(self):
        # The struct is cleared on every call, so an earlier failure does not linger
        with self.assertRaises(RuntimeError):
            bindings_tests.Errors.divide_int32(1, 0)
        self.assertEqual(3, bindings_tests.Errors.divide_int32(9, 3))


class OverloadsTest(unittest.TestCase):
    """Default arguments and the Python specific convenience overload."""

    def test_scale_defaults(self):
        self.assertEqual(12, bindings_tests.Overloads.scale(3, 4))
        self.assertEqual(10, bindings_tests.Overloads.scale())
        self.assertEqual(30, bindings_tests.Overloads.scale(3))

    def test_greet_default(self):
        self.assertEqual("Hello, Bob", bindings_tests.Overloads.greet("Bob"))
        self.assertEqual("Hi, Bob", bindings_tests.Overloads.greet("Bob", "Hi"))

    def test_renamed(self):
        self.assertEqual(7, bindings_tests.Overloads.renamed())
        self.assertFalse(hasattr(bindings_tests.Overloads, "original_name"))
        self.assertFalse(hasattr(bindings_tests.Overloads, "originalName"))

    def test_hidden_is_absent(self):
        self.assertFalse(hasattr(bindings_tests.Overloads, "hidden"))

    def test_ignored_class_is_absent(self):
        # TestIgnored is annotated with ignore = true, so the whole class is gone
        self.assertFalse(hasattr(bindings_tests, "Ignored"))

    def test_total(self):
        # The pythonOverload replaces the signature rather than adding to it, so the
        # pre-summed (sum, factor) form only exists in C. Here it takes a collection.
        self.assertEqual(12, bindings_tests.Overloads.total([1, 2, 3], 2))
        self.assertEqual(6, bindings_tests.Overloads.total([1, 2, 3]))

    def test_gain_defaults(self):
        # Rendered as 'invert: bool = False, scale: float = 1e-3'
        self.assertEqual(1e-3, bindings_tests.Overloads.gain())
        self.assertEqual(-1e-3, bindings_tests.Overloads.gain(True))
        self.assertEqual(2.0, bindings_tests.Overloads.gain(False, 2.0))

    def test_echo_index(self):
        # The pythonOverload only sets a postBody, which clamps negatives right
        # before the call. Valid indices pass through exactly as they do in C.
        self.assertEqual(3, bindings_tests.Overloads.echo_index(3))
        self.assertEqual(0, bindings_tests.Overloads.echo_index(-5))

    def test_answer(self):
        # 42 * multiplier everywhere except C++, where the hand-written cppBody
        # returns 21 * multiplier
        self.assertEqual(84, bindings_tests.Overloads.answer(2))


class IndicesTest(unittest.TestCase):
    """@CLibIndex is a no-op in Python: the wrapper is zero based, same as Java."""

    def test_echo(self):
        self.assertEqual(3, bindings_tests.Indices.echo(3))

    def test_one_sided_conversions(self):
        self.assertEqual(4, bindings_tests.Indices.count_up_to(3))
        self.assertEqual(4, bindings_tests.Indices.last_index_of(5))

    def test_defaults(self):
        self.assertEqual(12, bindings_tests.Indices.offset_from(1, 2))
        self.assertEqual(0, bindings_tests.Indices.offset_from())
        self.assertEqual(0, bindings_tests.Indices.offset_from(0, 0))

    def test_non_zero_default(self):
        # The default is zero based in Python, same as Java
        self.assertEqual(5, bindings_tests.Indices.from_row())
        self.assertEqual(2, bindings_tests.Indices.from_row(2))

    def test_size_index(self):
        self.assertEqual(3, bindings_tests.Indices.echo_size(3))


class IsolatedTest(unittest.TestCase):
    """State confined to a second GraalVM isolate.

    This counter does not share static fields or the handle registry with the
    default isolate.
    """

    def test_counter(self):
        bindings_tests.Isolated.reset()
        bindings_tests.Isolated.increment()
        bindings_tests.Isolated.increment()
        bindings_tests.Isolated.increment()
        self.assertEqual(3, bindings_tests.Isolated.get_counter())

    def test_always_fails(self):
        # The error struct is caller-owned, so the secondary isolate reports the same way
        with self.assertRaises(bindings_tests.Error) as ctx:
            bindings_tests.Isolated.always_fails()
        self.assertIn("secondary isolate", str(ctx.exception))
        self.assertEqual("bindings_tests:IllegalStateException", ctx.exception.id)


class CallbacksTest(unittest.TestCase):
    """Synchronous callbacks into a caller-provided function pointer."""

    class _State(ctypes.Structure):
        _fields_ = [("count", ctypes.c_int), ("indices", ctypes.c_int * 16)]

    def _make_callback(self):
        def handler(user_data, index):
            state = ctypes.cast(user_data, ctypes.POINTER(CallbacksTest._State)).contents
            if state.count < 16:
                state.indices[state.count] = index
            state.count += 1

        # Keep a reference to the ctypes trampoline for as long as it is in use
        return bindings_tests.IndexCallbackFunction(handler)

    def test_invoke_times(self):
        state = CallbacksTest._State()
        user_data = ctypes.cast(ctypes.pointer(state), ctypes.c_void_p)
        callback = self._make_callback()

        bindings_tests.Callbacks.invoke_times(callback, user_data, 5)

        # Fires synchronously, five times, with indices 0..4, before returning
        self.assertEqual(5, state.count)
        self.assertEqual([0, 1, 2, 3, 4], list(state.indices)[:5])

    def test_invoke_once(self):
        state = CallbacksTest._State()
        user_data = ctypes.cast(ctypes.pointer(state), ctypes.c_void_p)
        callback = self._make_callback()

        bindings_tests.Callbacks.invoke_once(callback, user_data, 7)

        self.assertEqual(1, state.count)
        self.assertEqual(7, state.indices[0])

    def test_invoke_checked(self):
        state = CallbacksTest._State()
        user_data = ctypes.cast(ctypes.pointer(state), ctypes.c_void_p)
        callback = self._make_callback()

        bindings_tests.Callbacks.invoke_checked(callback, user_data, 3)

        self.assertEqual(1, state.count)
        self.assertEqual(3, state.indices[0])

        # The callback fires before the validation, so a failing call still records it
        state = CallbacksTest._State()
        user_data = ctypes.cast(ctypes.pointer(state), ctypes.c_void_p)
        with self.assertRaises(bindings_tests.Error) as caught:
            bindings_tests.Callbacks.invoke_checked(callback, user_data, -1)
        self.assertIn("must not be negative", str(caught.exception))
        self.assertEqual("bindings_tests:IllegalArgumentException", caught.exception.id)
        self.assertEqual(1, state.count)
        self.assertEqual(-1, state.indices[0])

    def test_null_callback_is_a_noop(self):
        state = CallbacksTest._State()
        user_data = ctypes.cast(ctypes.pointer(state), ctypes.c_void_p)
        null_callback = bindings_tests.IndexCallbackFunction()

        bindings_tests.Callbacks.invoke_times(null_callback, user_data, 5)
        bindings_tests.Callbacks.invoke_once(null_callback, user_data, 7)

        self.assertEqual(0, state.count)

    def test_plain_function(self):
        seen = []

        def handler(user_data, index):
            seen.append(index)

        # Any callable works, the wrapper builds the ctypes prototype around it
        bindings_tests.Callbacks.invoke_times(handler, None, 5)

        self.assertEqual([0, 1, 2, 3, 4], seen)

    def test_lambda_with_captured_state(self):
        seen = []

        # The closure carries the state, so user_data has nothing left to do
        bindings_tests.Callbacks.invoke_once(lambda user_data, index: seen.append(index), None, 7)

        self.assertEqual([7], seen)

    def test_callback_on_worker_thread(self):
        seen = []

        # ctypes attaches the foreign thread and takes the GIL, so the callback runs
        # there like any other one, and the join makes the result visible here
        bindings_tests.Callbacks.invoke_on_worker_thread(lambda user_data, index: seen.append(index), None, 4)

        self.assertEqual([4], seen)

    def test_value_returning_callback(self):
        # The wrapper's restype carries the value back, which is the solver-objective shape
        self.assertEqual(ctypes.c_double, bindings_tests.ScaleCallbackFunction._restype_)

        result = bindings_tests.Callbacks.apply_scaled(lambda user_data, value: value * 2.0, None, 21.0)

        self.assertIsInstance(result, float)
        self.assertEqual(42.0, result)

    def test_notify_once(self):
        seen = []

        # The callback carries nothing but its userData
        bindings_tests.Callbacks.notify_once(lambda user_data: seen.append(1), None)

        self.assertEqual([1], seen)

    def test_constructor_takes_a_callback(self):
        seen = []

        # The callback fires while the handle is being created, before __init__ returns
        notified = bindings_tests.Notified(lambda user_data: seen.append(1), None)
        self.assertEqual([1], seen)

        del notified
        gc.collect()

    def test_counted_constructor_callback(self):
        seen = []

        # The indices all arrive during construction, so the instance still owns the
        # trampoline afterwards even though nothing will fire again
        counted = bindings_tests.Counted(lambda user_data, index: seen.append(index), None, 3)
        self.assertEqual([0, 1, 2], seen)
        self.assertIsNotNone(counted._func)

        del counted
        gc.collect()

    def test_stored_constructor_owns_the_callback(self):
        seen = []
        closed = bindings_tests.Stored.get_closed_count()

        # Nothing fires during construction. The handle owns the trampoline, which is what
        # keeps it alive for the later calls that do fire.
        stored = bindings_tests.Stored(lambda user_data, index: seen.append(index), None)
        self.assertEqual([], seen)

        stored.fire(2)
        stored.fire(5)
        self.assertEqual([2, 5], seen)

        del stored
        gc.collect()
        self.assertEqual(closed + 1, bindings_tests.Stored.get_closed_count())

    def test_method_wrapper_is_not_retained(self):
        # A fresh lambda per call is the IK-loop shape, and nothing may outlive the call
        # that passed it. The module therefore has no process-lifetime cache at all.
        for i in range(10):
            bindings_tests.Callbacks.invoke_once(lambda user_data, index: None, None, i)

        self.assertFalse(hasattr(bindings_tests, "_callback_wrappers"))
        self.assertFalse(hasattr(bindings_tests, "_wrap_callback"))


if __name__ == "__main__":
    unittest.main()
