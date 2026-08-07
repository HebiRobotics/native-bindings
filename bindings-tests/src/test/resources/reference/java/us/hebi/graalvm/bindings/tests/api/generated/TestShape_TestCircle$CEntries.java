package us.hebi.graalvm.bindings.tests.api.generated;

import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.ObjectHandle;
import org.graalvm.nativeimage.ObjectHandles;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.word.WordFactory;
import us.hebi.graalvm.bindings.runtime.pointers.Handles;
import us.hebi.graalvm.bindings.runtime.structs.CLibErrors;
import us.hebi.graalvm.bindings.runtime.structs.ErrorInfo;
import us.hebi.graalvm.bindings.tests.api.TestShape;

public final class TestShape_TestCircle$CEntries {
    public static final ObjectHandles INSTANCES = TestShape$CEntries.INSTANCES;

    public static ObjectHandle _registerInstance(final TestShape.TestCircle instance) {
        return Handles.create(INSTANCES, instance);
    }

    public static TestShape.TestCircle _getInstance(final ObjectHandle handle) {
        return Handles.get(INSTANCES, handle);
    }

    @CEntryPoint(
            name = "__bindings_tests_Circle_create"
    )
    public static ObjectHandle bindings_tests_Circle_create(IsolateThread _isolate,
            ErrorInfo _error, double radius) {
        CLibErrors.clear(_error);
        try {
            return TestShape_TestCircle$CEntries._registerInstance(TestShape.TestCircle.create(radius));
        } catch (Throwable throwable) {
            CLibErrors.fill(throwable, _error, "bindings_tests");
            return WordFactory.nullPointer();
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Circle_getColor"
    )
    public static int bindings_tests_Circle_getColor(IsolateThread _isolate, ObjectHandle _obj) {
        try {
            return TestShape_TestCircle_Color$$CEnum.fromJava(_getInstance(_obj).getColor());
        } catch (Throwable throwable) {
            // Declared noexcept, so there is no error struct to report through
            return 0;
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Circle_getRadius"
    )
    public static double bindings_tests_Circle_getRadius(IsolateThread _isolate,
            ObjectHandle _obj) {
        try {
            return _getInstance(_obj).getRadius();
        } catch (Throwable throwable) {
            // Declared noexcept, so there is no error struct to report through
            return 0;
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Circle_area"
    )
    public static double bindings_tests_Circle_area(IsolateThread _isolate, ObjectHandle _obj) {
        try {
            return _getInstance(_obj).area();
        } catch (Throwable throwable) {
            // Declared noexcept, so there is no error struct to report through
            return 0;
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Circle_sideCount"
    )
    public static int bindings_tests_Circle_sideCount(IsolateThread _isolate, ObjectHandle _obj) {
        try {
            return _getInstance(_obj).sideCount();
        } catch (Throwable throwable) {
            // Declared noexcept, so there is no error struct to report through
            return 0;
        }
    }
}
