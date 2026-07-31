package us.hebi.graalvm.bindings.tests.api;

import us.hebi.graalvm.bindings.annotations.CLibClass;
import us.hebi.graalvm.bindings.annotations.CLibComment;
import us.hebi.graalvm.bindings.annotations.CLibLastErrorProvider;
import us.hebi.graalvm.bindings.annotations.CLibMethod;
import us.hebi.graalvm.bindings.runtime.exceptions.ExceptionHandlers;

import static us.hebi.graalvm.bindings.annotations.MethodFlag.*;

/**
 * Covers error propagation across the ABI.
 * <p>
 * A method is treated as throwing unless it is marked {@code Noexcept}. A throwing
 * method that returns void is promoted to an int status code; every other return
 * type gets a type specific default on failure (false, 0, or a null pointer). Each
 * case below exists so that the language wrappers can assert on exactly one of those
 * defaults.
 * <p>
 * The last error is stored per thread, so a caller must read it on the same thread
 * that made the failing call.
 *
 * @author Florian Enner
 */
@CLibComment("Deliberate failures, used to check error propagation.")
@CLibClass(value = "Errors", enableInstancing = false)
public class NativeErrors {

    @CLibMethod
    @CLibComment(value = "Throws when value is negative, otherwise does nothing.",
            throwsOn = "value is negative")
    public static void requireNonNegative(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("value must not be negative, but was " + value);
        }
    }

    @CLibMethod
    @CLibComment(value = "Integer division. Returns 0 through the ABI when it throws.",
            throwsOn = "divisor is zero")
    public static int divideInt32(int a, int b) {
        if (b == 0) {
            throw new ArithmeticException("division by zero");
        }
        return a / b;
    }

    @CLibMethod
    @CLibComment(value = "Returns 0.0 through the ABI when it throws.",
            throwsOn = "shouldFail is true")
    public static double failingFloat64(boolean shouldFail) {
        if (shouldFail) {
            throw new IllegalStateException("failingFloat64 was asked to fail");
        }
        return 1.5;
    }

    @CLibMethod
    @CLibComment(value = "Returns false through the ABI when it throws.",
            throwsOn = "shouldFail is true")
    public static boolean failingBool(boolean shouldFail) {
        if (shouldFail) {
            throw new IllegalStateException("failingBool was asked to fail");
        }
        return true;
    }

    @CLibMethod
    @CLibComment(value = "Returns a null pointer through the ABI when it throws.",
            throwsOn = "shouldFail is true")
    public static String failingString(boolean shouldFail) {
        if (shouldFail) {
            throw new IllegalStateException("failingString was asked to fail");
        }
        return "ok";
    }

    @CLibMethod(flags = Noexcept)
    @CLibComment("Marked noexcept, so no status code is generated for it.")
    public static void neverThrows() {
        // intentionally empty
    }

    @CLibMethod(flags = Noexcept)
    @CLibComment("Clears the last error for the calling thread.")
    public static void clearLastError() {
        ExceptionHandlers.lastException.remove();
    }

    @CLibLastErrorProvider
    @CLibMethod(flags = Noexcept)
    @CLibComment("""
            Message of the last exception thrown on the calling thread, or an empty
            string if there has not been one. The language wrappers call this to turn
            a failed call into a native exception.
            """)
    public static String getLastError() {
        return ExceptionHandlers.getLastError(false);
    }

}
