package us.hebi.graalvm.bindings.tests.api.generated;

import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.CDoublePointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.word.WordFactory;
import us.hebi.graalvm.bindings.runtime.pointers.CPointers;
import us.hebi.graalvm.bindings.runtime.structs.ByteSpan;
import us.hebi.graalvm.bindings.runtime.structs.CLibErrors;
import us.hebi.graalvm.bindings.runtime.structs.DoubleSpan;
import us.hebi.graalvm.bindings.runtime.structs.ErrorInfo;
import us.hebi.graalvm.bindings.runtime.structs.FloatSpan;
import us.hebi.graalvm.bindings.runtime.structs.IntSpan;
import us.hebi.graalvm.bindings.tests.api.TestArrays;

public final class TestArrays$CEntries {
    @CEntryPoint(
            name = "__bindings_tests_Arrays_sumFloat64"
    )
    public static double bindings_tests_Arrays_sumFloat64(IsolateThread _isolate,
            CDoublePointer values, long count) {
        try {
            return TestArrays.sumFloat64(values, CPointers.capSizeToInt(count));
        } catch (Throwable throwable) {
            // Declared noexcept, so there is no error struct to report through
            return 0;
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Arrays_sumInt32"
    )
    public static int bindings_tests_Arrays_sumInt32(IsolateThread _isolate, CIntPointer values,
            long count) {
        try {
            return TestArrays.sumInt32(values, CPointers.capSizeToInt(count));
        } catch (Throwable throwable) {
            // Declared noexcept, so there is no error struct to report through
            return 0;
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Arrays_fillFloat64"
    )
    public static void bindings_tests_Arrays_fillFloat64(IsolateThread _isolate,
            CDoublePointer target, long count, double value) {
        try {
            TestArrays.fillFloat64(target, CPointers.capSizeToInt(count), value);
        } catch (Throwable throwable) {
            // Declared noexcept, so there is no error struct to report through
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Arrays_cumulativeSum"
    )
    public static void bindings_tests_Arrays_cumulativeSum(IsolateThread _isolate,
            CDoublePointer source, CDoublePointer target, long count) {
        try {
            TestArrays.cumulativeSum(source, target, CPointers.capSizeToInt(count));
        } catch (Throwable throwable) {
            // Declared noexcept, so there is no error struct to report through
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Arrays_reverseFloat64"
    )
    public static void bindings_tests_Arrays_reverseFloat64(IsolateThread _isolate,
            CDoublePointer values, long count) {
        try {
            TestArrays.reverseFloat64(values, CPointers.capSizeToInt(count));
        } catch (Throwable throwable) {
            // Declared noexcept, so there is no error struct to report through
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Arrays_dotProduct"
    )
    public static double bindings_tests_Arrays_dotProduct(IsolateThread _isolate, ErrorInfo _error,
            DoubleSpan x, DoubleSpan y) {
        CLibErrors.clear(_error);
        try {
            return TestArrays.dotProduct(x, y);
        } catch (Throwable throwable) {
            CLibErrors.fill(throwable, _error, "bindings_tests");
            return 0;
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Arrays_addOffset"
    )
    public static void bindings_tests_Arrays_addOffset(IsolateThread _isolate, DoubleSpan values,
            double offset, DoubleSpan _structOut) {
        try {
            double[] _values = TestArrays.addOffset(values, offset);
            _structOut.setData(CPointers.toTempDoublePointer(_values));
            _structOut.setLength(_values == null ? 0 : _values.length);
        } catch (Throwable throwable) {
            // Declared noexcept, so there is no error struct to report through
            _structOut.setData(WordFactory.nullPointer());
            _structOut.setLength(0);
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Arrays_addOffsetInt32"
    )
    public static void bindings_tests_Arrays_addOffsetInt32(IsolateThread _isolate, IntSpan values,
            int offset, IntSpan _structOut) {
        try {
            int[] _values = TestArrays.addOffsetInt32(values, offset);
            _structOut.setData(CPointers.toTempIntPointer(_values));
            _structOut.setLength(_values == null ? 0 : _values.length);
        } catch (Throwable throwable) {
            // Declared noexcept, so there is no error struct to report through
            _structOut.setData(WordFactory.nullPointer());
            _structOut.setLength(0);
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Arrays_addOffsetFloat32"
    )
    public static void bindings_tests_Arrays_addOffsetFloat32(IsolateThread _isolate,
            FloatSpan values, float offset, FloatSpan _structOut) {
        try {
            float[] _values = TestArrays.addOffsetFloat32(values, offset);
            _structOut.setData(CPointers.toTempFloatPointer(_values));
            _structOut.setLength(_values == null ? 0 : _values.length);
        } catch (Throwable throwable) {
            // Declared noexcept, so there is no error struct to report through
            _structOut.setData(WordFactory.nullPointer());
            _structOut.setLength(0);
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Arrays_addOffsetUint8"
    )
    public static void bindings_tests_Arrays_addOffsetUint8(IsolateThread _isolate, ByteSpan values,
            byte offset, ByteSpan _structOut) {
        try {
            byte[] _values = TestArrays.addOffsetUint8(values, offset);
            _structOut.setData(CPointers.toTempBytePointer(_values));
            _structOut.setLength(_values == null ? 0 : _values.length);
        } catch (Throwable throwable) {
            // Declared noexcept, so there is no error struct to report through
            _structOut.setData(WordFactory.nullPointer());
            _structOut.setLength(0);
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Arrays_getSequence"
    )
    public static void bindings_tests_Arrays_getSequence(IsolateThread _isolate, long count,
            DoubleSpan _structOut) {
        try {
            double[] _values = TestArrays.getSequence(CPointers.capSizeToInt(count));
            _structOut.setData(CPointers.toTempDoublePointer(_values));
            _structOut.setLength(_values == null ? 0 : _values.length);
        } catch (Throwable throwable) {
            // Declared noexcept, so there is no error struct to report through
            _structOut.setData(WordFactory.nullPointer());
            _structOut.setLength(0);
        }
    }
}
