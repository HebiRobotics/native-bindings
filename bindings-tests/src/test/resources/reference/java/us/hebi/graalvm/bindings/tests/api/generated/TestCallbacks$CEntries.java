package us.hebi.graalvm.bindings.tests.api.generated;

import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.CDoublePointer;
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

    @CEntryPoint(
            name = "__bindings_tests_Callbacks_transformOnce"
    )
    public static void bindings_tests_Callbacks_transformOnce(IsolateThread _isolate,
            TestCallbacks.TransformCallbackFunction func, VoidPointer userData,
            CDoublePointer input, CDoublePointer output) {
        try {
            TestCallbacks.transformOnce(func, userData, input, output);
        } catch (Throwable throwable) {
            // Declared noexcept, so there is no error struct to report through
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Callbacks_invokePlainTimes"
    )
    public static void bindings_tests_Callbacks_invokePlainTimes(IsolateThread _isolate,
            TestCallbacks.PlainIndexCallbackFunction func, long times) {
        try {
            TestCallbacks.invokePlainTimes(func, CPointers.capSizeToInt(times));
        } catch (Throwable throwable) {
            // Declared noexcept, so there is no error struct to report through
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Callbacks_invokePlainChecked"
    )
    public static void bindings_tests_Callbacks_invokePlainChecked(IsolateThread _isolate,
            ErrorInfo _error, TestCallbacks.PlainIndexCallbackFunction func, int index) {
        CLibErrors.clear(_error);
        try {
            TestCallbacks.invokePlainChecked(func, index);
        } catch (Throwable throwable) {
            CLibErrors.fill(throwable, _error, "bindings_tests");
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Callbacks_invokePlainOnWorkerThread"
    )
    public static void bindings_tests_Callbacks_invokePlainOnWorkerThread(IsolateThread _isolate,
            TestCallbacks.PlainIndexCallbackFunction func, int index) {
        try {
            TestCallbacks.invokePlainOnWorkerThread(func, index);
        } catch (Throwable throwable) {
            // Declared noexcept, so there is no error struct to report through
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Callbacks_notifyPlainOnce"
    )
    public static void bindings_tests_Callbacks_notifyPlainOnce(IsolateThread _isolate,
            TestCallbacks.PlainNotifyCallbackFunction func) {
        try {
            TestCallbacks.notifyPlainOnce(func);
        } catch (Throwable throwable) {
            // Declared noexcept, so there is no error struct to report through
        }
    }
}
