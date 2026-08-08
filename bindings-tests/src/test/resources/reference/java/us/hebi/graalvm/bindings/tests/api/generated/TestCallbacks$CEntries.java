package us.hebi.graalvm.bindings.tests.api.generated;

import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.VoidPointer;
import us.hebi.graalvm.bindings.runtime.pointers.CPointers;
import us.hebi.graalvm.bindings.runtime.structs.CLibErrors;
import us.hebi.graalvm.bindings.runtime.structs.ErrorInfo;
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
            name = "__bindings_tests_Callbacks_invokeChecked"
    )
    public static void bindings_tests_Callbacks_invokeChecked(IsolateThread _isolate,
            ErrorInfo _error, TestCallbacks.IndexCallbackFunction func, VoidPointer userData,
            int index) {
        CLibErrors.clear(_error);
        try {
            TestCallbacks.invokeChecked(func, userData, index);
        } catch (Throwable throwable) {
            CLibErrors.fill(throwable, _error, "bindings_tests");
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Callbacks_invokeOnWorkerThread"
    )
    public static void bindings_tests_Callbacks_invokeOnWorkerThread(IsolateThread _isolate,
            TestCallbacks.IndexCallbackFunction func, VoidPointer userData, int index) {
        try {
            TestCallbacks.invokeOnWorkerThread(func, userData, index);
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

    @CEntryPoint(
            name = "__bindings_tests_Callbacks_applyScaled"
    )
    public static double bindings_tests_Callbacks_applyScaled(IsolateThread _isolate,
            TestCallbacks.ScaleCallbackFunction func, VoidPointer userData, double value) {
        try {
            return TestCallbacks.applyScaled(func, userData, value);
        } catch (Throwable throwable) {
            // Declared noexcept, so there is no error struct to report through
            return 0;
        }
    }
}
