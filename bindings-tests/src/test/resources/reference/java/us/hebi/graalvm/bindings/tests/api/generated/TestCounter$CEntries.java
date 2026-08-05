package us.hebi.graalvm.bindings.tests.api.generated;

import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.ObjectHandle;
import org.graalvm.nativeimage.ObjectHandles;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.word.WordFactory;
import us.hebi.graalvm.bindings.runtime.handles.Handles;
import us.hebi.graalvm.bindings.runtime.types.CLibError;
import us.hebi.graalvm.bindings.runtime.types.ErrorInfo;
import us.hebi.graalvm.bindings.tests.api.TestCounter;

public final class TestCounter$CEntries {
    public static final ObjectHandles INSTANCES = ObjectHandles.create();

    public static ObjectHandle _registerInstance(final TestCounter instance) {
        return Handles.create(INSTANCES, instance);
    }

    public static TestCounter _getInstance(final ObjectHandle handle) {
        return Handles.get(INSTANCES, handle);
    }

    @CEntryPoint(
            name = "__bindings_tests_Counter_release"
    )
    public static void bindings_tests_Counter_release(IsolateThread _isolate,
            final ObjectHandle handle) {
        Handles.destroy(INSTANCES, handle);
    }

    @CEntryPoint(
            name = "__bindings_tests_Counter_create"
    )
    public static ObjectHandle bindings_tests_Counter_create(IsolateThread _isolate,
            ErrorInfo _error, int start) {
        CLibError.clearStruct(_error);
        try {
            return TestCounter$CEntries._registerInstance(TestCounter.create(start));
        } catch (Throwable throwable) {
            CLibError.fillStruct(throwable, _error, "bindings_tests");
            return WordFactory.nullPointer();
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Counter_increment"
    )
    public static void bindings_tests_Counter_increment(IsolateThread _isolate, ObjectHandle _obj) {
        try {
            _getInstance(_obj).increment();
        } catch (Throwable throwable) {
            // Declared noexcept, so there is no error struct to report through
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Counter_add"
    )
    public static void bindings_tests_Counter_add(IsolateThread _isolate, ObjectHandle _obj,
            int amount) {
        try {
            _getInstance(_obj).add(amount);
        } catch (Throwable throwable) {
            // Declared noexcept, so there is no error struct to report through
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Counter_getValue"
    )
    public static int bindings_tests_Counter_getValue(IsolateThread _isolate, ObjectHandle _obj) {
        try {
            return _getInstance(_obj).getValue();
        } catch (Throwable throwable) {
            // Declared noexcept, so there is no error struct to report through
            return 0;
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Counter_setValue"
    )
    public static void bindings_tests_Counter_setValue(IsolateThread _isolate, ObjectHandle _obj,
            int value) {
        try {
            _getInstance(_obj).setValue(value);
        } catch (Throwable throwable) {
            // Declared noexcept, so there is no error struct to report through
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Counter_isClosed"
    )
    public static boolean bindings_tests_Counter_isClosed(IsolateThread _isolate,
            ObjectHandle _obj) {
        try {
            return _getInstance(_obj).isClosed();
        } catch (Throwable throwable) {
            // Declared noexcept, so there is no error struct to report through
            return false;
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Counter_getClosedCount"
    )
    public static int bindings_tests_Counter_getClosedCount(IsolateThread _isolate) {
        try {
            return TestCounter.getClosedCount();
        } catch (Throwable throwable) {
            // Declared noexcept, so there is no error struct to report through
            return 0;
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Counter_resetClosedCount"
    )
    public static void bindings_tests_Counter_resetClosedCount(IsolateThread _isolate) {
        try {
            TestCounter.resetClosedCount();
        } catch (Throwable throwable) {
            // Declared noexcept, so there is no error struct to report through
        }
    }
}
