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
        # The length check lives once in the Java implementation. A mismatch
        # returns the type default and sets the last error.
        bindings_tests.Errors.clear_last_error()
        self.assertEqual(0.0, bindings_tests.Arrays.dot_product([1.0, 2.0, 3.0], [4.0, 5.0]))
        self.assertIn("elements", bindings_tests.Errors.get_last_error())
        bindings_tests.Errors.clear_last_error()

    def test_add_offset(self):
        # Span in, numpy array out. The wrapper copies out of the library-owned
        # buffer before returning.
        result = bindings_tests.Arrays.add_offset([1.0, 2.0, 3.0], 10.0)
        self.assertEqual([11.0, 12.0, 13.0], list(result))

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

    def setUp(self):
        bindings_tests.Errors.clear_last_error()

    def test_require_non_negative(self):
        bindings_tests.Errors.require_non_negative(5)

        # A throwing void method is promoted to an int status code, and the
        # wrapper turns a non-zero status into a RuntimeError.
        with self.assertRaises(RuntimeError):
            bindings_tests.Errors.require_non_negative(-1)

        self.assertIn("must not be negative", bindings_tests.Errors.get_last_error())

    def test_defaults_on_failure(self):
        # Methods with a real return type get a type specific default on
        # failure. The generated wrapper does not check those, so nothing is
        # raised here and the Python result matches the raw C ABI.
        self.assertEqual(5, bindings_tests.Errors.divide_int32(10, 2))
        self.assertEqual(0, bindings_tests.Errors.divide_int32(1, 0))

        self.assertEqual(1.5, bindings_tests.Errors.failing_float64(False))
        self.assertEqual(0.0, bindings_tests.Errors.failing_float64(True))

        self.assertEqual(True, bindings_tests.Errors.failing_bool(False))
        self.assertEqual(False, bindings_tests.Errors.failing_bool(True))

        self.assertEqual("ok", bindings_tests.Errors.failing_string(False))
        # A null char* comes back as an empty string
        self.assertEqual("", bindings_tests.Errors.failing_string(True))

    def test_never_throws(self):
        # Marked Noexcept, so there is no status code to check
        bindings_tests.Errors.never_throws()

    def test_clear(self):
        bindings_tests.Errors.divide_int32(1, 0)
        bindings_tests.Errors.clear_last_error()
        self.assertEqual("", bindings_tests.Errors.get_last_error())


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

    This counter does not share static fields, the handle registry or the last
    error slot with the default isolate, which is why it gets its own generated
    last error accessor.
    """

    def test_counter(self):
        bindings_tests.Isolated.reset()
        bindings_tests.Isolated.increment()
        bindings_tests.Isolated.increment()
        bindings_tests.Isolated.increment()
        self.assertEqual(3, bindings_tests.Isolated.get_counter())

    def test_always_fails(self):
        with self.assertRaisesRegex(RuntimeError, "secondary isolate"):
            bindings_tests.Isolated.always_fails()
        # The wrappers use the generated per-isolate accessor internally, so the
        # raw symbol is asserted through the loaded library handle.
        message = bindings_tests.libbindings_tests.bindings_tests_secondary_getLastError()
        self.assertIn("secondary isolate", message.decode("utf-8"))


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

    def test_null_callback_is_a_noop(self):
        state = CallbacksTest._State()
        user_data = ctypes.cast(ctypes.pointer(state), ctypes.c_void_p)
        null_callback = bindings_tests.IndexCallbackFunction()

        bindings_tests.Callbacks.invoke_times(null_callback, user_data, 5)
        bindings_tests.Callbacks.invoke_once(null_callback, user_data, 7)

        self.assertEqual(0, state.count)


if __name__ == "__main__":
    unittest.main()
