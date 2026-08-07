package us.hebi.graalvm.bindings.tests.api.generated;

import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.WordFactory;
import us.hebi.graalvm.bindings.runtime.pointers.CPointers;
import us.hebi.graalvm.bindings.runtime.structs.CLibErrors;
import us.hebi.graalvm.bindings.runtime.structs.ErrorInfo;
import us.hebi.graalvm.bindings.tests.api.TestErrors;

public final class TestErrors$CEntries {
    @CEntryPoint(
            name = "__bindings_tests_Errors_requireNonNegative"
    )
    public static void bindings_tests_Errors_requireNonNegative(IsolateThread _isolate,
            ErrorInfo _error, int value) {
        CLibErrors.clear(_error);
        try {
            TestErrors.requireNonNegative(value);
        } catch (Throwable throwable) {
            CLibErrors.fill(throwable, _error, "bindings_tests");
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Errors_divideInt32"
    )
    public static int bindings_tests_Errors_divideInt32(IsolateThread _isolate, ErrorInfo _error,
            int a, int b) {
        CLibErrors.clear(_error);
        try {
            return TestErrors.divideInt32(a, b);
        } catch (Throwable throwable) {
            CLibErrors.fill(throwable, _error, "bindings_tests");
            return 0;
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Errors_failingFloat64"
    )
    public static double bindings_tests_Errors_failingFloat64(IsolateThread _isolate,
            ErrorInfo _error, boolean shouldFail) {
        CLibErrors.clear(_error);
        try {
            return TestErrors.failingFloat64(shouldFail);
        } catch (Throwable throwable) {
            CLibErrors.fill(throwable, _error, "bindings_tests");
            return 0;
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Errors_failingBool"
    )
    public static boolean bindings_tests_Errors_failingBool(IsolateThread _isolate,
            ErrorInfo _error, boolean shouldFail) {
        CLibErrors.clear(_error);
        try {
            return TestErrors.failingBool(shouldFail);
        } catch (Throwable throwable) {
            CLibErrors.fill(throwable, _error, "bindings_tests");
            return false;
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Errors_failingString"
    )
    public static CCharPointer bindings_tests_Errors_failingString(IsolateThread _isolate,
            ErrorInfo _error, boolean shouldFail) {
        CLibErrors.clear(_error);
        try {
            return CPointers.toTempCharPointer(TestErrors.failingString(shouldFail));
        } catch (Throwable throwable) {
            CLibErrors.fill(throwable, _error, "bindings_tests");
            return WordFactory.nullPointer();
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Errors_failWithoutMessage"
    )
    public static int bindings_tests_Errors_failWithoutMessage(IsolateThread _isolate,
            ErrorInfo _error) {
        CLibErrors.clear(_error);
        try {
            return TestErrors.failWithoutMessage();
        } catch (Throwable throwable) {
            CLibErrors.fill(throwable, _error, "bindings_tests");
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
            // Declared noexcept, so there is no error struct to report through
        }
    }
}
