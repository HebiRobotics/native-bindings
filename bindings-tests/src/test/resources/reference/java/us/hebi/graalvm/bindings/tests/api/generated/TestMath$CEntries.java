package us.hebi.graalvm.bindings.tests.api.generated;

import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import us.hebi.graalvm.bindings.runtime.exceptions.ExceptionHandlers;
import us.hebi.graalvm.bindings.runtime.types.CTypes;
import us.hebi.graalvm.bindings.tests.api.TestMath;

public final class TestMath$CEntries {
    @CEntryPoint(
            name = "__bindings_tests_Math_negate"
    )
    public static boolean bindings_tests_Math_negate(IsolateThread _isolate, boolean value) {
        try {
            return TestMath.negate(value);
        } catch (Throwable throwable) {
            ExceptionHandlers.setLastError(throwable);
            return false;
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Math_nextChar"
    )
    public static char bindings_tests_Math_nextChar(IsolateThread _isolate, char value) {
        try {
            return TestMath.nextChar(value);
        } catch (Throwable throwable) {
            ExceptionHandlers.setLastError(throwable);
            return 0;
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Math_addInt8"
    )
    public static byte bindings_tests_Math_addInt8(IsolateThread _isolate, byte a, byte b) {
        try {
            return TestMath.addInt8(a, b);
        } catch (Throwable throwable) {
            ExceptionHandlers.setLastError(throwable);
            return 0;
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Math_addInt16"
    )
    public static short bindings_tests_Math_addInt16(IsolateThread _isolate, short a, short b) {
        try {
            return TestMath.addInt16(a, b);
        } catch (Throwable throwable) {
            ExceptionHandlers.setLastError(throwable);
            return 0;
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Math_addInt32"
    )
    public static int bindings_tests_Math_addInt32(IsolateThread _isolate, int a, int b) {
        try {
            return TestMath.addInt32(a, b);
        } catch (Throwable throwable) {
            ExceptionHandlers.setLastError(throwable);
            return 0;
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Math_addInt64"
    )
    public static long bindings_tests_Math_addInt64(IsolateThread _isolate, long a, long b) {
        try {
            return TestMath.addInt64(a, b);
        } catch (Throwable throwable) {
            ExceptionHandlers.setLastError(throwable);
            return 0;
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Math_addFloat32"
    )
    public static float bindings_tests_Math_addFloat32(IsolateThread _isolate, float a, float b) {
        try {
            return TestMath.addFloat32(a, b);
        } catch (Throwable throwable) {
            ExceptionHandlers.setLastError(throwable);
            return 0;
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Math_addFloat64"
    )
    public static double bindings_tests_Math_addFloat64(IsolateThread _isolate, double a,
            double b) {
        try {
            return TestMath.addFloat64(a, b);
        } catch (Throwable throwable) {
            ExceptionHandlers.setLastError(throwable);
            return 0;
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Math_addSize"
    )
    public static long bindings_tests_Math_addSize(IsolateThread _isolate, long a, long b) {
        try {
            return TestMath.addSize(CTypes.capSizeToLong(a), CTypes.capSizeToLong(b));
        } catch (Throwable throwable) {
            ExceptionHandlers.setLastError(throwable);
            return 0;
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Math_sizeToInt32"
    )
    public static int bindings_tests_Math_sizeToInt32(IsolateThread _isolate, long value) {
        try {
            return TestMath.sizeToInt32(CTypes.capSizeToInt(value));
        } catch (Throwable throwable) {
            ExceptionHandlers.setLastError(throwable);
            return 0;
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Math_maxInt32"
    )
    public static int bindings_tests_Math_maxInt32(IsolateThread _isolate) {
        try {
            return TestMath.maxInt32();
        } catch (Throwable throwable) {
            ExceptionHandlers.setLastError(throwable);
            return 0;
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Math_maxSize"
    )
    public static long bindings_tests_Math_maxSize(IsolateThread _isolate) {
        try {
            return TestMath.maxSize();
        } catch (Throwable throwable) {
            ExceptionHandlers.setLastError(throwable);
            return 0;
        }
    }
}
