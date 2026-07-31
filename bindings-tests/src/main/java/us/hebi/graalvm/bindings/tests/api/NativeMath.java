package us.hebi.graalvm.bindings.tests.api;

import us.hebi.graalvm.bindings.annotations.CLibClass;
import us.hebi.graalvm.bindings.annotations.CLibComment;
import us.hebi.graalvm.bindings.annotations.CLibMethod;
import us.hebi.graalvm.bindings.annotations.CSizeType;

import static us.hebi.graalvm.bindings.annotations.MethodFlag.*;

/**
 * Covers every primitive width that can cross the ABI, plus size_t.
 * <p>
 * Static only, so nothing here allocates a handle. Every result is a pure
 * function of its inputs so the same assertions hold in every language.
 *
 * @author Florian Enner
 */
@CLibComment("Stateless arithmetic used to check primitive type mapping.")
@CLibClass(value = "Math", enableInstancing = false)
public class NativeMath {

    @CLibMethod(flags = Noexcept)
    public static boolean negate(boolean value) {
        return !value;
    }

    /*
     * TODO: re-enable once CHAR16 is supported as an output type.
     *
     * A Java char maps to DataType.CHAR16 (an unsigned 2 byte UTF-16 code unit). It is
     * accepted as a parameter, but CEntryPointGenerator throws
     * "Unsupported output type: CHAR16" when it appears as a return type. Disabled here
     * so the rest of the matrix can be established first.
     *
     * @CLibMethod(flags = Noexcept)
     * @CLibComment("Returns the next UTF-16 code unit. 'A' becomes 'B'.")
     * public static char nextChar(char value) {
     *     return (char) (value + 1);
     * }
     */

    @CLibMethod(flags = Noexcept)
    public static byte addInt8(byte a, byte b) {
        return (byte) (a + b);
    }

    @CLibMethod(flags = Noexcept)
    public static short addInt16(short a, short b) {
        return (short) (a + b);
    }

    @CLibMethod(flags = Noexcept)
    public static int addInt32(int a, int b) {
        return a + b;
    }

    @CLibMethod(flags = Noexcept)
    @CLibComment("Uses values above 2^32 to catch platforms that truncate long to 32 bit.")
    public static long addInt64(long a, long b) {
        return a + b;
    }

    @CLibMethod(flags = Noexcept)
    public static float addFloat32(float a, float b) {
        return a + b;
    }

    @CLibMethod(flags = Noexcept)
    public static double addFloat64(double a, double b) {
        return a + b;
    }

    @CLibMethod(flags = Noexcept)
    @CLibComment("size_t in and size_t out. Bridged through int64 on the Java side.")
    public static @CSizeType long addSize(@CSizeType long a, @CSizeType long b) {
        return a + b;
    }

    @CLibMethod(flags = Noexcept)
    @CLibComment("Takes a size_t that is capped to a 32 bit Java int on the way in.")
    public static int sizeToInt32(@CSizeType int value) {
        return value;
    }

    @CLibMethod(flags = Noexcept)
    @CLibComment("Returns Integer.MAX_VALUE, so callers can check the 31 bit cap.")
    public static int maxInt32() {
        return Integer.MAX_VALUE;
    }

    @CLibMethod(flags = Noexcept)
    @CLibComment("Returns Long.MAX_VALUE as a size_t, so callers can check the 63 bit cap.")
    public static @CSizeType long maxSize() {
        return Long.MAX_VALUE;
    }

}
