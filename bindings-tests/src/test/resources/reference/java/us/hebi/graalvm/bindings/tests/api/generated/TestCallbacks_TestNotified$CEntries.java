package us.hebi.graalvm.bindings.tests.api.generated;

import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.ObjectHandle;
import org.graalvm.nativeimage.ObjectHandles;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.VoidPointer;
import org.graalvm.word.WordFactory;
import us.hebi.graalvm.bindings.runtime.pointers.Handles;
import us.hebi.graalvm.bindings.runtime.structs.CLibErrors;
import us.hebi.graalvm.bindings.runtime.structs.ErrorInfo;
import us.hebi.graalvm.bindings.tests.api.TestCallbacks;

public final class TestCallbacks_TestNotified$CEntries {
    public static final ObjectHandles INSTANCES = ObjectHandles.create();

    public static ObjectHandle _registerInstance(final TestCallbacks.TestNotified instance) {
        return Handles.create(INSTANCES, instance);
    }

    public static TestCallbacks.TestNotified _getInstance(final ObjectHandle handle) {
        return Handles.get(INSTANCES, handle);
    }

    @CEntryPoint(
            name = "__bindings_tests_Notified_release"
    )
    public static void bindings_tests_Notified_release(IsolateThread _isolate,
            final ObjectHandle handle) {
        Handles.destroy(INSTANCES, handle);
    }

    @CEntryPoint(
            name = "__bindings_tests_Notified_create"
    )
    public static ObjectHandle bindings_tests_Notified_create(IsolateThread _isolate,
            ErrorInfo _error, TestCallbacks.NotifyCallbackFunction func, VoidPointer userData) {
        CLibErrors.clear(_error);
        try {
            return TestCallbacks_TestNotified$CEntries._registerInstance(TestCallbacks.TestNotified.create(func, userData));
        } catch (Throwable throwable) {
            CLibErrors.fill(throwable, _error, "bindings_tests");
            return WordFactory.nullPointer();
        }
    }
}
