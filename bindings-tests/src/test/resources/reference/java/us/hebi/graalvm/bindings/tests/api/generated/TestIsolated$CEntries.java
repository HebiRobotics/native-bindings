package us.hebi.graalvm.bindings.tests.api.generated;

import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.WordFactory;
import us.hebi.graalvm.bindings.runtime.exceptions.ExceptionHandlers;
import us.hebi.graalvm.bindings.runtime.types.CTypes;
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
    public static int bindings_tests_Isolated_alwaysFails(IsolateThread _isolate) {
        try {
            TestIsolated.alwaysFails();
            return ExceptionHandlers.STATUS_CODE_SUCCESS;
        } catch (Throwable throwable) {
            ExceptionHandlers.setLastError(throwable);
            return ExceptionHandlers.returnErrorStatusCode(throwable);
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Isolated_getLastError"
    )
    public static CCharPointer bindings_tests_Isolated_getLastError(IsolateThread _isolate) {
        try {
            return CTypes.toTempCharPointer(TestIsolated.getLastError());
        } catch (Throwable throwable) {
            ExceptionHandlers.setLastError(throwable);
            return WordFactory.nullPointer();
        }
    }
}
