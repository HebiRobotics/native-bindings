package us.hebi.graalvm.bindings.tests.api.generated;

import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.WordFactory;
import us.hebi.graalvm.bindings.runtime.exceptions.ExceptionHandlers;
import us.hebi.graalvm.bindings.runtime.types.CLibError;
import us.hebi.graalvm.bindings.runtime.types.CTypes;
import us.hebi.graalvm.bindings.runtime.types.ErrorInfo;
import us.hebi.graalvm.bindings.tests.api.TestErrors;

public final class TestErrors$CEntries {
    @CEntryPoint(
            name = "__bindings_tests_Errors_requireNonNegative"
    )
    public static int bindings_tests_Errors_requireNonNegative(IsolateThread _isolate,
            ErrorInfo _error, int value) {
        CLibError.clearStruct(_error);
        try {
            TestErrors.requireNonNegative(value);
            return ExceptionHandlers.STATUS_CODE_SUCCESS;
        } catch (Throwable throwable) {
            CLibError.fillStruct(throwable, _error, "bindings_tests");
            return ExceptionHandlers.returnErrorStatusCode(throwable);
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Errors_divideInt32"
    )
    public static int bindings_tests_Errors_divideInt32(IsolateThread _isolate, ErrorInfo _error,
            int a, int b) {
        CLibError.clearStruct(_error);
        try {
            return TestErrors.divideInt32(a, b);
        } catch (Throwable throwable) {
            CLibError.fillStruct(throwable, _error, "bindings_tests");
            return 0;
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Errors_failingFloat64"
    )
    public static double bindings_tests_Errors_failingFloat64(IsolateThread _isolate,
            ErrorInfo _error, boolean shouldFail) {
        CLibError.clearStruct(_error);
        try {
            return TestErrors.failingFloat64(shouldFail);
        } catch (Throwable throwable) {
            CLibError.fillStruct(throwable, _error, "bindings_tests");
            return 0;
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Errors_failingBool"
    )
    public static boolean bindings_tests_Errors_failingBool(IsolateThread _isolate,
            ErrorInfo _error, boolean shouldFail) {
        CLibError.clearStruct(_error);
        try {
            return TestErrors.failingBool(shouldFail);
        } catch (Throwable throwable) {
            CLibError.fillStruct(throwable, _error, "bindings_tests");
            return false;
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Errors_failingString"
    )
    public static CCharPointer bindings_tests_Errors_failingString(IsolateThread _isolate,
            ErrorInfo _error, boolean shouldFail) {
        CLibError.clearStruct(_error);
        try {
            return CTypes.toTempCharPointer(TestErrors.failingString(shouldFail));
        } catch (Throwable throwable) {
            CLibError.fillStruct(throwable, _error, "bindings_tests");
            return WordFactory.nullPointer();
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Errors_failWithoutMessage"
    )
    public static int bindings_tests_Errors_failWithoutMessage(IsolateThread _isolate,
            ErrorInfo _error) {
        CLibError.clearStruct(_error);
        try {
            return TestErrors.failWithoutMessage();
        } catch (Throwable throwable) {
            CLibError.fillStruct(throwable, _error, "bindings_tests");
            return 0;
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Errors_neverThrows"
    )
    public static void bindings_tests_Errors_neverThrows(IsolateThread _isolate) {
        try {
            TestErrors.neverThrows();
        } catch (Throwable throwable) {
            ExceptionHandlers.setLastError(throwable);
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Errors_clearLastError"
    )
    public static void bindings_tests_Errors_clearLastError(IsolateThread _isolate) {
        try {
            TestErrors.clearLastError();
        } catch (Throwable throwable) {
            ExceptionHandlers.setLastError(throwable);
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Errors_getLastError"
    )
    public static CCharPointer bindings_tests_Errors_getLastError(IsolateThread _isolate) {
        try {
            return CTypes.toTempCharPointer(TestErrors.getLastError());
        } catch (Throwable throwable) {
            ExceptionHandlers.setLastError(throwable);
            return WordFactory.nullPointer();
        }
    }
}
