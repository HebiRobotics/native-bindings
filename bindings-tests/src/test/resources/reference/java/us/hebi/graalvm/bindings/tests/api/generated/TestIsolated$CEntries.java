package us.hebi.graalvm.bindings.tests.api.generated;

import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import us.hebi.graalvm.bindings.runtime.structs.CLibErrors;
import us.hebi.graalvm.bindings.runtime.structs.ErrorInfo;
import us.hebi.graalvm.bindings.tests.api.TestIsolated;

public final class TestIsolated$CEntries {
    @CEntryPoint(
            name = "__bindings_tests_Isolated_increment"
    )
    public static void bindings_tests_Isolated_increment(IsolateThread _isolate) {
        try {
            TestIsolated.increment();
        } catch (Throwable throwable) {
            // Declared noexcept, so there is no error struct to report through
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Isolated_getCounter"
    )
    public static int bindings_tests_Isolated_getCounter(IsolateThread _isolate) {
        try {
            return TestIsolated.getCounter();
        } catch (Throwable throwable) {
            // Declared noexcept, so there is no error struct to report through
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
            // Declared noexcept, so there is no error struct to report through
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Isolated_alwaysFails"
    )
    public static void bindings_tests_Isolated_alwaysFails(IsolateThread _isolate,
            ErrorInfo _error) {
        CLibErrors.clear(_error);
        try {
            TestIsolated.alwaysFails();
        } catch (Throwable throwable) {
            CLibErrors.fill(throwable, _error, "bindings_tests");
        }
    }
}
