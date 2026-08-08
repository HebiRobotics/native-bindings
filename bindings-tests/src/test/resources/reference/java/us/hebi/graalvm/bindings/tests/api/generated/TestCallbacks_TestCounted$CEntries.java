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

public final class TestCallbacks_TestCounted$CEntries {
    public static final ObjectHandles INSTANCES = ObjectHandles.create();

    public static ObjectHandle _registerInstance(final TestCallbacks.TestCounted instance) {
        return Handles.create(INSTANCES, instance);
    }

    public static TestCallbacks.TestCounted _getInstance(final ObjectHandle handle) {
        return Handles.get(INSTANCES, handle);
    }

    @CEntryPoint(
            name = "__bindings_tests_Counted_release"
    )
    public static void bindings_tests_Counted_release(IsolateThread _isolate,
            final ObjectHandle handle) {
        Handles.destroy(INSTANCES, handle);
    }

    @CEntryPoint(
            name = "__bindings_tests_Counted_create"
    )
    public static ObjectHandle bindings_tests_Counted_create(IsolateThread _isolate,
            ErrorInfo _error, TestCallbacks.IndexCallbackFunction func, VoidPointer userData,
            int times) {
        CLibErrors.clear(_error);
        try {
            return TestCallbacks_TestCounted$CEntries._registerInstance(TestCallbacks.TestCounted.create(func, userData, times));
        } catch (Throwable throwable) {
            CLibErrors.fill(throwable, _error, "bindings_tests");
            return WordFactory.nullPointer();
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Counted_getClosedCount"
    )
    public static int bindings_tests_Counted_getClosedCount(IsolateThread _isolate) {
        try {
            return TestCallbacks.TestCounted.getClosedCount();
        } catch (Throwable throwable) {
            // Declared noexcept, so there is no error struct to report through
            return 0;
        }
    }
}
