package us.hebi.graalvm.bindings.tests.api.generated;

import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.ObjectHandle;
import org.graalvm.nativeimage.ObjectHandles;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.word.WordFactory;
import us.hebi.graalvm.bindings.runtime.pointers.Handles;
import us.hebi.graalvm.bindings.runtime.structs.CLibError;
import us.hebi.graalvm.bindings.runtime.structs.ErrorInfo;
import us.hebi.graalvm.bindings.tests.api.TestNaming;

public final class TestNaming$CEntries {
    public static final ObjectHandles INSTANCES = ObjectHandles.create();

    public static ObjectHandle _registerInstance(final TestNaming instance) {
        return Handles.create(INSTANCES, instance);
    }

    public static TestNaming _getInstance(final ObjectHandle handle) {
        return Handles.get(INSTANCES, handle);
    }

    @CEntryPoint(
            name = "__custom_prefix_Naming_release"
    )
    public static void custom_prefix_Naming_release(IsolateThread _isolate,
            final ObjectHandle handle) {
        Handles.destroy(INSTANCES, handle);
    }

    @CEntryPoint(
            name = "__custom_prefix_Naming_create"
    )
    public static ObjectHandle custom_prefix_Naming_create(IsolateThread _isolate, ErrorInfo _error,
            int startValue) {
        CLibError.clearStruct(_error);
        try {
            return TestNaming$CEntries._registerInstance(TestNaming.create(startValue));
        } catch (Throwable throwable) {
            CLibError.fillStruct(throwable, _error, "bindings_tests");
            return WordFactory.nullPointer();
        }
    }

    @CEntryPoint(
            name = "__custom_prefix_Naming_get_start_value"
    )
    public static int custom_prefix_Naming_get_start_value(IsolateThread _isolate,
            ObjectHandle _obj) {
        try {
            return _getInstance(_obj).getStartValue();
        } catch (Throwable throwable) {
            // Declared noexcept, so there is no error struct to report through
            return 0;
        }
    }

    @CEntryPoint(
            name = "__custom_prefix_Naming_add_two_numbers"
    )
    public static int custom_prefix_Naming_add_two_numbers(IsolateThread _isolate, int first,
            int second) {
        try {
            return TestNaming.addTwoNumbers(first, second);
        } catch (Throwable throwable) {
            // Declared noexcept, so there is no error struct to report through
            return 0;
        }
    }
}
