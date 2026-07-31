package us.hebi.graalvm.bindings.tests.api;

import us.hebi.graalvm.bindings.annotations.CLibClass;
import us.hebi.graalvm.bindings.annotations.CLibComment;
import us.hebi.graalvm.bindings.annotations.CLibLastErrorProvider;
import us.hebi.graalvm.bindings.annotations.CLibMethod;
import us.hebi.graalvm.bindings.runtime.exceptions.ExceptionHandlers;

import static us.hebi.graalvm.bindings.annotations.MethodFlag.*;

/**
 * Lives in its own isolate, so it gets a separate isolate thread accessor and does
 * not share static state or the handle registry with the default isolate.
 * <p>
 * {@link #getCounter()} is static, which is exactly the point: incrementing it here
 * must not be visible to anything in the default isolate, and each isolate needs its
 * own last error provider.
 *
 * @author Florian Enner
 */
@CLibComment("State confined to a second isolate.")
@CLibClass(value = "Isolated", isolateName = "secondary", enableInstancing = false)
public class TestIsolated {

    @CLibMethod(flags = Noexcept)
    public static void increment() {
        counter++;
    }

    @CLibMethod(flags = Noexcept)
    public static int getCounter() {
        return counter;
    }

    @CLibMethod(flags = Noexcept)
    public static void reset() {
        counter = 0;
    }

    @CLibMethod
    @CLibComment(value = "Always throws, so the secondary isolate's error path is exercised too.",
            throwsOn = "always")
    public static void alwaysFails() {
        throw new IllegalStateException("alwaysFails in the secondary isolate");
    }

    @CLibLastErrorProvider
    @CLibMethod(flags = Noexcept)
    @CLibComment("Last error for the calling thread within the secondary isolate.")
    public static String getLastError() {
        return ExceptionHandlers.getLastError(false);
    }

    private static int counter = 0;

}
