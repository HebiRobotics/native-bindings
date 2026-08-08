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

public final class TestCallbacks_TestStored$CEntries {
    public static final ObjectHandles INSTANCES = ObjectHandles.create();

    public static ObjectHandle _registerInstance(final TestCallbacks.TestStored instance) {
        return Handles.create(INSTANCES, instance);
    }

    public static TestCallbacks.TestStored _getInstance(final ObjectHandle handle) {
        return Handles.get(INSTANCES, handle);
    }

    @CEntryPoint(
            name = "__bindings_tests_Stored_release"
    )
    public static void bindings_tests_Stored_release(IsolateThread _isolate,
            final ObjectHandle handle) {
        Handles.destroy(INSTANCES, handle);
    }

    @CEntryPoint(
            name = "__bindings_tests_Stored_create"
    )
    public static ObjectHandle bindings_tests_Stored_create(IsolateThread _isolate,
            ErrorInfo _error, TestCallbacks.StoredCallbackFunction func, VoidPointer userData) {
        CLibErrors.clear(_error);
        try {
            return TestCallbacks_TestStored$CEntries._registerInstance(TestCallbacks.TestStored.create(func, userData));
        } catch (Throwable throwable) {
            CLibErrors.fill(throwable, _error, "bindings_tests");
            return WordFactory.nullPointer();
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Stored_fire"
    )
    public static void bindings_tests_Stored_fire(IsolateThread _isolate, ObjectHandle _obj,
            int index) {
        try {
            _getInstance(_obj).fire(index);
        } catch (Throwable throwable) {
            // Declared noexcept, so there is no error struct to report through
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Stored_getClosedCount"
    )
    public static int bindings_tests_Stored_getClosedCount(IsolateThread _isolate) {
        try {
            return TestCallbacks.TestStored.getClosedCount();
        } catch (Throwable throwable) {
            // Declared noexcept, so there is no error struct to report through
            return 0;
        }
    }
}
