package us.hebi.graalvm.bindings.tests.api.generated;

import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.ObjectHandle;
import org.graalvm.nativeimage.ObjectHandles;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.word.WordFactory;
import us.hebi.graalvm.bindings.runtime.exceptions.ExceptionHandlers;
import us.hebi.graalvm.bindings.runtime.handles.Handles;
import us.hebi.graalvm.bindings.tests.api.NativeCounter;

public final class NativeCounter$CEntries {
    public static final ObjectHandles INSTANCES = ObjectHandles.create();

    public static ObjectHandle _registerInstance(final NativeCounter instance) {
        return Handles.create(INSTANCES, instance);
    }

    public static NativeCounter _getInstance(final ObjectHandle handle) {
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
    public static ObjectHandle bindings_tests_Counter_create(IsolateThread _isolate, int start) {
        try {
            return NativeCounter$CEntries._registerInstance(NativeCounter.create(start));
        } catch (Throwable throwable) {
            ExceptionHandlers.setLastError(throwable);
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
            ExceptionHandlers.setLastError(throwable);
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
            ExceptionHandlers.setLastError(throwable);
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Counter_getValue"
    )
    public static int bindings_tests_Counter_getValue(IsolateThread _isolate, ObjectHandle _obj) {
        try {
            return _getInstance(_obj).getValue();
        } catch (Throwable throwable) {
            ExceptionHandlers.setLastError(throwable);
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
            ExceptionHandlers.setLastError(throwable);
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
            ExceptionHandlers.setLastError(throwable);
            return false;
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Counter_getClosedCount"
    )
    public static int bindings_tests_Counter_getClosedCount(IsolateThread _isolate) {
        try {
            return NativeCounter.getClosedCount();
        } catch (Throwable throwable) {
            ExceptionHandlers.setLastError(throwable);
            return 0;
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Counter_resetClosedCount"
    )
    public static void bindings_tests_Counter_resetClosedCount(IsolateThread _isolate) {
        try {
            NativeCounter.resetClosedCount();
        } catch (Throwable throwable) {
            ExceptionHandlers.setLastError(throwable);
        }
    }
}
