package us.hebi.graalvm.bindings.tests.api.generated;

import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.WordFactory;
import us.hebi.graalvm.bindings.runtime.exceptions.ExceptionHandlers;
import us.hebi.graalvm.bindings.runtime.types.CTypes;
import us.hebi.graalvm.bindings.tests.api.NativeErrors;

public final class NativeErrors$CEntries {
    @CEntryPoint(
            name = "__bindings_tests_Errors_requireNonNegative"
    )
    public static int bindings_tests_Errors_requireNonNegative(IsolateThread _isolate, int value) {
        try {
            NativeErrors.requireNonNegative(value);
            return ExceptionHandlers.STATUS_CODE_SUCCESS;
        } catch (Throwable throwable) {
            ExceptionHandlers.setLastError(throwable);
            return ExceptionHandlers.returnErrorStatusCode(throwable);
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Errors_divideInt32"
    )
    public static int bindings_tests_Errors_divideInt32(IsolateThread _isolate, int a, int b) {
        try {
            return NativeErrors.divideInt32(a, b);
        } catch (Throwable throwable) {
            ExceptionHandlers.setLastError(throwable);
            return 0;
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Errors_failingFloat64"
    )
    public static double bindings_tests_Errors_failingFloat64(IsolateThread _isolate,
            boolean shouldFail) {
        try {
            return NativeErrors.failingFloat64(shouldFail);
        } catch (Throwable throwable) {
            ExceptionHandlers.setLastError(throwable);
            return 0;
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Errors_failingBool"
    )
    public static boolean bindings_tests_Errors_failingBool(IsolateThread _isolate,
            boolean shouldFail) {
        try {
            return NativeErrors.failingBool(shouldFail);
        } catch (Throwable throwable) {
            ExceptionHandlers.setLastError(throwable);
            return false;
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Errors_failingString"
    )
    public static CCharPointer bindings_tests_Errors_failingString(IsolateThread _isolate,
            boolean shouldFail) {
        try {
            return CTypes.toTempCharPointer(NativeErrors.failingString(shouldFail));
        } catch (Throwable throwable) {
            ExceptionHandlers.setLastError(throwable);
            return WordFactory.nullPointer();
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Errors_neverThrows"
    )
    public static void bindings_tests_Errors_neverThrows(IsolateThread _isolate) {
        try {
            NativeErrors.neverThrows();
        } catch (Throwable throwable) {
            ExceptionHandlers.setLastError(throwable);
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Errors_clearLastError"
    )
    public static void bindings_tests_Errors_clearLastError(IsolateThread _isolate) {
        try {
            NativeErrors.clearLastError();
        } catch (Throwable throwable) {
            ExceptionHandlers.setLastError(throwable);
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Errors_getLastError"
    )
    public static CCharPointer bindings_tests_Errors_getLastError(IsolateThread _isolate) {
        try {
            return CTypes.toTempCharPointer(NativeErrors.getLastError());
        } catch (Throwable throwable) {
            ExceptionHandlers.setLastError(throwable);
            return WordFactory.nullPointer();
        }
    }
}
