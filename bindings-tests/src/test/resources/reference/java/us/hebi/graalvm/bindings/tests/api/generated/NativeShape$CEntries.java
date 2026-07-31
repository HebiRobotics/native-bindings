package us.hebi.graalvm.bindings.tests.api.generated;

import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.ObjectHandle;
import org.graalvm.nativeimage.ObjectHandles;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.WordFactory;
import us.hebi.graalvm.bindings.runtime.exceptions.ExceptionHandlers;
import us.hebi.graalvm.bindings.runtime.handles.Handles;
import us.hebi.graalvm.bindings.runtime.types.CTypes;
import us.hebi.graalvm.bindings.tests.api.NativeShape;

public final class NativeShape$CEntries {
    public static final ObjectHandles INSTANCES = ObjectHandles.create();

    public static ObjectHandle _registerInstance(final NativeShape instance) {
        return Handles.create(INSTANCES, instance);
    }

    public static NativeShape _getInstance(final ObjectHandle handle) {
        return Handles.get(INSTANCES, handle);
    }

    @CEntryPoint(
            name = "__bindings_tests_Shape_release"
    )
    public static void bindings_tests_Shape_release(IsolateThread _isolate,
            final ObjectHandle handle) {
        Handles.destroy(INSTANCES, handle);
    }

    @CEntryPoint(
            name = "__bindings_tests_Shape_getName"
    )
    public static CCharPointer bindings_tests_Shape_getName(IsolateThread _isolate,
            ObjectHandle _obj) {
        try {
            return CTypes.toTempCharPointer(_getInstance(_obj).getName());
        } catch (Throwable throwable) {
            ExceptionHandlers.setLastError(throwable);
            return WordFactory.nullPointer();
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Shape_setName"
    )
    public static void bindings_tests_Shape_setName(IsolateThread _isolate, ObjectHandle _obj,
            CCharPointer name) {
        try {
            _getInstance(_obj).setName(CTypes.utf8ToJavaString(name));
        } catch (Throwable throwable) {
            ExceptionHandlers.setLastError(throwable);
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Shape_area"
    )
    public static double bindings_tests_Shape_area(IsolateThread _isolate, ObjectHandle _obj) {
        try {
            return _getInstance(_obj).area();
        } catch (Throwable throwable) {
            ExceptionHandlers.setLastError(throwable);
            return 0;
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Shape_sideCount"
    )
    public static int bindings_tests_Shape_sideCount(IsolateThread _isolate, ObjectHandle _obj) {
        try {
            return _getInstance(_obj).sideCount();
        } catch (Throwable throwable) {
            ExceptionHandlers.setLastError(throwable);
            return 0;
        }
    }
}
