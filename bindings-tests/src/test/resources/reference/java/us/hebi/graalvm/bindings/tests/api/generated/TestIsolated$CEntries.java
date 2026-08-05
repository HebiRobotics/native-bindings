package us.hebi.graalvm.bindings.tests.api.generated;

import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import us.hebi.graalvm.bindings.runtime.exceptions.ExceptionHandlers;
import us.hebi.graalvm.bindings.runtime.types.CLibError;
import us.hebi.graalvm.bindings.runtime.types.ErrorInfo;
import us.hebi.graalvm.bindings.tests.api.TestIsolated;

public final class TestIsolated$CEntries {
    @CEntryPoint(
            name = "__bindings_tests_Isolated_increment"
    )
    public static void bindings_tests_Isolated_increment(IsolateThread _isolate) {
        try {
            TestIsolated.increment();
        } catch (Throwable throwable) {
            ExceptionHandlers.setLastError(throwable);
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Isolated_getCounter"
    )
    public static int bindings_tests_Isolated_getCounter(IsolateThread _isolate) {
        try {
            return TestIsolated.getCounter();
        } catch (Throwable throwable) {
            ExceptionHandlers.setLastError(throwable);
            return 0;
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Isolated_reset"
    )
    public static void bindings_tests_Isolated_reset(IsolateThread _isolate) {
        try {
            TestIsolated.reset();
        } catch (Throwable throwable) {
            ExceptionHandlers.setLastError(throwable);
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Isolated_alwaysFails"
    )
    public static int bindings_tests_Isolated_alwaysFails(IsolateThread _isolate,
            ErrorInfo _error) {
        CLibError.clearStruct(_error);
        try {
            TestIsolated.alwaysFails();
            return ExceptionHandlers.STATUS_CODE_SUCCESS;
        } catch (Throwable throwable) {
            CLibError.fillStruct(throwable, _error, "bindings_tests");
            return ExceptionHandlers.returnErrorStatusCode(throwable);
        }
    }
}
