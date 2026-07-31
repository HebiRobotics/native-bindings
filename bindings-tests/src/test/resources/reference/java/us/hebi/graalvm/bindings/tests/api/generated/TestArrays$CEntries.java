package us.hebi.graalvm.bindings.tests.api.generated;

import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.CDoublePointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.word.WordFactory;
import us.hebi.graalvm.bindings.runtime.exceptions.ExceptionHandlers;
import us.hebi.graalvm.bindings.runtime.types.CTypes;
import us.hebi.graalvm.bindings.runtime.types.DoubleSpan;
import us.hebi.graalvm.bindings.tests.api.TestArrays;

public final class TestArrays$CEntries {
    @CEntryPoint(
            name = "__bindings_tests_Arrays_sumFloat64"
    )
    public static double bindings_tests_Arrays_sumFloat64(IsolateThread _isolate,
            CDoublePointer values, long count) {
        try {
            return TestArrays.sumFloat64(values, CTypes.capSizeToInt(count));
        } catch (Throwable throwable) {
            ExceptionHandlers.setLastError(throwable);
            return 0;
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Arrays_sumInt32"
    )
    public static int bindings_tests_Arrays_sumInt32(IsolateThread _isolate, CIntPointer values,
            long count) {
        try {
            return TestArrays.sumInt32(values, CTypes.capSizeToInt(count));
        } catch (Throwable throwable) {
            ExceptionHandlers.setLastError(throwable);
            return 0;
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Arrays_fillFloat64"
    )
    public static void bindings_tests_Arrays_fillFloat64(IsolateThread _isolate,
            CDoublePointer target, long count, double value) {
        try {
            TestArrays.fillFloat64(target, CTypes.capSizeToInt(count), value);
        } catch (Throwable throwable) {
            ExceptionHandlers.setLastError(throwable);
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Arrays_cumulativeSum"
    )
    public static void bindings_tests_Arrays_cumulativeSum(IsolateThread _isolate,
            CDoublePointer source, CDoublePointer target, long count) {
        try {
            TestArrays.cumulativeSum(source, target, CTypes.capSizeToInt(count));
        } catch (Throwable throwable) {
            ExceptionHandlers.setLastError(throwable);
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Arrays_reverseFloat64"
    )
    public static void bindings_tests_Arrays_reverseFloat64(IsolateThread _isolate,
            CDoublePointer values, long count) {
        try {
            TestArrays.reverseFloat64(values, CTypes.capSizeToInt(count));
        } catch (Throwable throwable) {
            ExceptionHandlers.setLastError(throwable);
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Arrays_dotProduct"
    )
    public static double bindings_tests_Arrays_dotProduct(IsolateThread _isolate, DoubleSpan x,
            DoubleSpan y) {
        try {
            return TestArrays.dotProduct(x, y);
        } catch (Throwable throwable) {
            ExceptionHandlers.setLastError(throwable);
            return 0;
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Arrays_addOffset"
    )
    public static void bindings_tests_Arrays_addOffset(IsolateThread _isolate, DoubleSpan values,
            double offset, DoubleSpan _spanOut) {
        try {
            double[] _values = TestArrays.addOffset(values, offset);
            _spanOut.setData(CTypes.toTempDoublePointer(_values));
            _spanOut.setLength(_values == null ? 0 : _values.length);
        } catch (Throwable throwable) {
            ExceptionHandlers.setLastError(throwable);
            _spanOut.setData(WordFactory.nullPointer());
            _spanOut.setLength(0);
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Arrays_getSequence"
    )
    public static void bindings_tests_Arrays_getSequence(IsolateThread _isolate, long count,
            DoubleSpan _spanOut) {
        try {
            double[] _values = TestArrays.getSequence(CTypes.capSizeToInt(count));
            _spanOut.setData(CTypes.toTempDoublePointer(_values));
            _spanOut.setLength(_values == null ? 0 : _values.length);
        } catch (Throwable throwable) {
            ExceptionHandlers.setLastError(throwable);
            _spanOut.setData(WordFactory.nullPointer());
            _spanOut.setLength(0);
        }
    }
}
