package us.hebi.graalvm.bindings.tests.api.generated;

import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.VoidPointer;
import us.hebi.graalvm.bindings.runtime.exceptions.ExceptionHandlers;
import us.hebi.graalvm.bindings.runtime.types.CTypes;
import us.hebi.graalvm.bindings.tests.api.NativeCallbacks;

public final class NativeCallbacks$CEntries {
    @CEntryPoint(
            name = "__bindings_tests_Callbacks_invokeTimes"
    )
    public static void bindings_tests_Callbacks_invokeTimes(IsolateThread _isolate,
            NativeCallbacks.IndexCallbackFunction func, VoidPointer userData, long times) {
        try {
            NativeCallbacks.invokeTimes(func, userData, CTypes.capSizeToInt(times));
        } catch (Throwable throwable) {
            ExceptionHandlers.setLastError(throwable);
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Callbacks_invokeOnce"
    )
    public static void bindings_tests_Callbacks_invokeOnce(IsolateThread _isolate,
            NativeCallbacks.IndexCallbackFunction func, VoidPointer userData, int index) {
        try {
            NativeCallbacks.invokeOnce(func, userData, index);
        } catch (Throwable throwable) {
            ExceptionHandlers.setLastError(throwable);
        }
    }
}
