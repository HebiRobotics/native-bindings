package us.hebi.graalvm.bindings.tests.api.generated;

import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.WordFactory;
import us.hebi.graalvm.bindings.runtime.exceptions.ExceptionHandlers;
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
            ExceptionHandlers.setLastError(throwable);
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
            ExceptionHandlers.setLastError(throwable);
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
            ExceptionHandlers.setLastError(throwable);
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
            ExceptionHandlers.setLastError(throwable);
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
            ExceptionHandlers.setLastError(throwable);
            return 0;
        }
    }
}
