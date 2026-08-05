package us.hebi.graalvm.bindings.tests.api.generated;

import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.WordFactory;
import us.hebi.graalvm.bindings.runtime.types.CTypes;
import us.hebi.graalvm.bindings.tests.api.TestOverloads;

public final class TestOverloads$CEntries {
    @CEntryPoint(
            name = "__bindings_tests_Overloads_scale"
    )
    public static int bindings_tests_Overloads_scale(IsolateThread _isolate, int value,
            int factor) {
        try {
            return TestOverloads.scale(value, factor);
        } catch (Throwable throwable) {
            // Declared noexcept, so there is no error struct to report through
            return 0;
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Overloads_greet"
    )
    public static CCharPointer bindings_tests_Overloads_greet(IsolateThread _isolate,
            CCharPointer name, CCharPointer greeting) {
        try {
            return CTypes.toTempCharPointer(TestOverloads.greet(CTypes.utf8ToJavaString(name), CTypes.utf8ToJavaString(greeting)));
        } catch (Throwable throwable) {
            // Declared noexcept, so there is no error struct to report through
            return WordFactory.nullPointer();
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Overloads_total"
    )
    public static int bindings_tests_Overloads_total(IsolateThread _isolate, int sum, int factor) {
        try {
            return TestOverloads.total(sum, factor);
        } catch (Throwable throwable) {
            // Declared noexcept, so there is no error struct to report through
            return 0;
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Overloads_gain"
    )
    public static double bindings_tests_Overloads_gain(IsolateThread _isolate, boolean invert,
            double scale) {
        try {
            return TestOverloads.gain(invert, scale);
        } catch (Throwable throwable) {
            // Declared noexcept, so there is no error struct to report through
            return 0;
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Overloads_echoIndex"
    )
    public static int bindings_tests_Overloads_echoIndex(IsolateThread _isolate, int index) {
        try {
            return TestOverloads.echoIndex(index);
        } catch (Throwable throwable) {
            // Declared noexcept, so there is no error struct to report through
            return 0;
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Overloads_answer"
    )
    public static int bindings_tests_Overloads_answer(IsolateThread _isolate, int multiplier) {
        try {
            return TestOverloads.answer(multiplier);
        } catch (Throwable throwable) {
            // Declared noexcept, so there is no error struct to report through
            return 0;
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Overloads_renamed"
    )
    public static int bindings_tests_Overloads_renamed(IsolateThread _isolate) {
        try {
            return TestOverloads.originalName();
        } catch (Throwable throwable) {
            // Declared noexcept, so there is no error struct to report through
            return 0;
        }
    }
}
