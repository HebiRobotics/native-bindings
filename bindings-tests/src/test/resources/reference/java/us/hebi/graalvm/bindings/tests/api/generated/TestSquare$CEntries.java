package us.hebi.graalvm.bindings.tests.api.generated;

import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.ObjectHandle;
import org.graalvm.nativeimage.ObjectHandles;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.word.WordFactory;
import us.hebi.graalvm.bindings.runtime.handles.Handles;
import us.hebi.graalvm.bindings.runtime.types.CLibError;
import us.hebi.graalvm.bindings.runtime.types.ErrorInfo;
import us.hebi.graalvm.bindings.tests.api.TestSquare;

public final class TestSquare$CEntries {
    public static final ObjectHandles INSTANCES = TestShape$CEntries.INSTANCES;

    public static ObjectHandle _registerInstance(final TestSquare instance) {
        return Handles.create(INSTANCES, instance);
    }

    public static TestSquare _getInstance(final ObjectHandle handle) {
        return Handles.get(INSTANCES, handle);
    }

    @CEntryPoint(
            name = "__bindings_tests_Square_create"
    )
    public static ObjectHandle bindings_tests_Square_create(IsolateThread _isolate,
            ErrorInfo _error, double side) {
        CLibError.clearStruct(_error);
        try {
            return TestSquare$CEntries._registerInstance(TestSquare.create(side));
        } catch (Throwable throwable) {
            CLibError.fillStruct(throwable, _error, "bindings_tests");
            return WordFactory.nullPointer();
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Square_getSide"
    )
    public static double bindings_tests_Square_getSide(IsolateThread _isolate, ObjectHandle _obj) {
        try {
            return _getInstance(_obj).getSide();
        } catch (Throwable throwable) {
            // Declared noexcept, so there is no error struct to report through
            return 0;
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Square_area"
    )
    public static double bindings_tests_Square_area(IsolateThread _isolate, ObjectHandle _obj) {
        try {
            return _getInstance(_obj).area();
        } catch (Throwable throwable) {
            // Declared noexcept, so there is no error struct to report through
            return 0;
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Square_sideCount"
    )
    public static int bindings_tests_Square_sideCount(IsolateThread _isolate, ObjectHandle _obj) {
        try {
            return _getInstance(_obj).sideCount();
        } catch (Throwable throwable) {
            // Declared noexcept, so there is no error struct to report through
            return 0;
        }
    }
}
