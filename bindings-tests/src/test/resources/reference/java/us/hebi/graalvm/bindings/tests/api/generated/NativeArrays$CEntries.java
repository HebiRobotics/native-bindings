package us.hebi.graalvm.bindings.tests.api.generated;

import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.CDoublePointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import us.hebi.graalvm.bindings.runtime.exceptions.ExceptionHandlers;
import us.hebi.graalvm.bindings.runtime.types.CTypes;
import us.hebi.graalvm.bindings.tests.api.NativeArrays;

public final class NativeArrays$CEntries {
    @CEntryPoint(
            name = "__bindings_tests_Arrays_sumFloat64"
    )
    public static double bindings_tests_Arrays_sumFloat64(IsolateThread _isolate,
            CDoublePointer values, long count) {
        try {
            return NativeArrays.sumFloat64(values, CTypes.capSizeToInt(count));
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
            return NativeArrays.sumInt32(values, CTypes.capSizeToInt(count));
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
            NativeArrays.fillFloat64(target, CTypes.capSizeToInt(count), value);
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
            NativeArrays.cumulativeSum(source, target, CTypes.capSizeToInt(count));
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
            NativeArrays.reverseFloat64(values, CTypes.capSizeToInt(count));
        } catch (Throwable throwable) {
            ExceptionHandlers.setLastError(throwable);
        }
    }
}
