package us.hebi.graalvm.bindings.tests.api.generated;

import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.ObjectHandle;
import org.graalvm.nativeimage.ObjectHandles;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.WordFactory;
import us.hebi.graalvm.bindings.runtime.pointers.CPointers;
import us.hebi.graalvm.bindings.runtime.pointers.Handles;
import us.hebi.graalvm.bindings.runtime.structs.CLibErrors;
import us.hebi.graalvm.bindings.runtime.structs.ErrorInfo;
import us.hebi.graalvm.bindings.tests.api.TestShape;

public final class TestShape$CEntries {
    public static final ObjectHandles INSTANCES = ObjectHandles.create();

    public static ObjectHandle _registerInstance(final TestShape instance) {
        return Handles.create(INSTANCES, instance);
    }

    public static TestShape _getInstance(final ObjectHandle handle) {
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
            name = "__bindings_tests_Shape_create"
    )
    public static ObjectHandle bindings_tests_Shape_create(IsolateThread _isolate, ErrorInfo _error,
            CCharPointer name) {
        CLibErrors.clear(_error);
        try {
            return TestShape$CEntries._registerInstance(TestShape.create(CPointers.utf8ToJavaString(name)));
        } catch (Throwable throwable) {
            CLibErrors.fill(throwable, _error, "bindings_tests");
            return WordFactory.nullPointer();
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Shape_plainCopy"
    )
    public static ObjectHandle bindings_tests_Shape_plainCopy(IsolateThread _isolate,
            ObjectHandle _obj) {
        try {
            return TestShape$CEntries._registerInstance(_getInstance(_obj).plainCopy());
        } catch (Throwable throwable) {
            // Declared noexcept, so there is no error struct to report through
            return WordFactory.nullPointer();
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Shape_getName"
    )
    public static CCharPointer bindings_tests_Shape_getName(IsolateThread _isolate,
            ObjectHandle _obj) {
        try {
            return CPointers.toTempCharPointer(_getInstance(_obj).getName());
        } catch (Throwable throwable) {
            // Declared noexcept, so there is no error struct to report through
            return WordFactory.nullPointer();
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Shape_setName"
    )
    public static void bindings_tests_Shape_setName(IsolateThread _isolate, ObjectHandle _obj,
            CCharPointer name) {
        try {
            _getInstance(_obj).setName(CPointers.utf8ToJavaString(name));
        } catch (Throwable throwable) {
            // Declared noexcept, so there is no error struct to report through
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Shape_area"
    )
    public static double bindings_tests_Shape_area(IsolateThread _isolate, ObjectHandle _obj) {
        try {
            return _getInstance(_obj).area();
        } catch (Throwable throwable) {
            // Declared noexcept, so there is no error struct to report through
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
            // Declared noexcept, so there is no error struct to report through
            return 0;
        }
    }
}
