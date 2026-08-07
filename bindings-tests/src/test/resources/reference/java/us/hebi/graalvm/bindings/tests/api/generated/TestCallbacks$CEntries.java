package us.hebi.graalvm.bindings.tests.api.generated;

import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.VoidPointer;
import us.hebi.graalvm.bindings.runtime.pointers.CPointers;
import us.hebi.graalvm.bindings.tests.api.TestCallbacks;

public final class TestCallbacks$CEntries {
    @CEntryPoint(
            name = "__bindings_tests_Callbacks_invokeTimes"
    )
    public static void bindings_tests_Callbacks_invokeTimes(IsolateThread _isolate,
            TestCallbacks.IndexCallbackFunction func, VoidPointer userData, long times) {
        try {
            TestCallbacks.invokeTimes(func, userData, CPointers.capSizeToInt(times));
        } catch (Throwable throwable) {
            // Declared noexcept, so there is no error struct to report through
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Callbacks_invokeOnce"
    )
    public static void bindings_tests_Callbacks_invokeOnce(IsolateThread _isolate,
            TestCallbacks.IndexCallbackFunction func, VoidPointer userData, int index) {
        try {
            TestCallbacks.invokeOnce(func, userData, index);
        } catch (Throwable throwable) {
            // Declared noexcept, so there is no error struct to report through
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Callbacks_notifyOnce"
    )
    public static void bindings_tests_Callbacks_notifyOnce(IsolateThread _isolate,
            TestCallbacks.NotifyCallbackFunction func, VoidPointer userData) {
        try {
            TestCallbacks.notifyOnce(func, userData);
        } catch (Throwable throwable) {
            // Declared noexcept, so there is no error struct to report through
        }
    }
}
