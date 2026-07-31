package us.hebi.graalvm.bindings.tests.api;

import org.graalvm.nativeimage.c.type.CDoublePointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import us.hebi.graalvm.bindings.annotations.CLibClass;
import us.hebi.graalvm.bindings.annotations.CLibComment;
import us.hebi.graalvm.bindings.annotations.CLibMethod;
import us.hebi.graalvm.bindings.annotations.CSizeType;
import us.hebi.graalvm.bindings.runtime.types.DoubleSpan;

import static us.hebi.graalvm.bindings.annotations.MethodFlag.*;

/**
 * Covers the (pointer, length) parameter pattern for both input and output buffers.
 * <p>
 * None of these methods allocate. The caller owns every buffer, which is what makes
 * the MATLAB and Python wrappers able to pass their native array types straight through.
 *
 * @author Florian Enner
 */
@CLibComment("Bulk array operations over caller-owned buffers.")
@CLibClass(value = "Arrays", enableInstancing = false)
public class NativeArrays {

    @CLibMethod(flags = Noexcept)
    public static double sumFloat64(CDoublePointer values, @CSizeType int count) {
        double sum = 0;
        for (int i = 0; i < count; i++) {
            sum += values.read(i);
        }
        return sum;
    }

    @CLibMethod(flags = Noexcept)
    public static int sumInt32(CIntPointer values, @CSizeType int count) {
        int sum = 0;
        for (int i = 0; i < count; i++) {
            sum += values.read(i);
        }
        return sum;
    }

    @CLibMethod(flags = Noexcept)
    @CLibComment("Writes value into every element of target. Checks output buffer handling.")
    public static void fillFloat64(CDoublePointer target, @CSizeType int count, double value) {
        for (int i = 0; i < count; i++) {
            target.write(i, value);
        }
    }

    @CLibMethod(flags = Noexcept)
    @CLibComment("""
            Writes the running total of source into target. Both buffers must hold at
            least count elements. Passing the same buffer for both is supported.
            """)
    public static void cumulativeSum(CDoublePointer source, CDoublePointer target, @CSizeType int count) {
        double sum = 0;
        for (int i = 0; i < count; i++) {
            sum += source.read(i);
            target.write(i, sum);
        }
    }

    @CLibMethod(flags = Noexcept)
    @CLibComment("Reverses the buffer in place.")
    public static void reverseFloat64(CDoublePointer values, @CSizeType int count) {
        for (int i = 0, j = count - 1; i < j; i++, j--) {
            double tmp = values.read(i);
            values.write(i, values.read(j));
            values.write(j, tmp);
        }
    }

    // ---- Span variants. Each buffer carries its own length, so wrappers pass
    // their native array types without a separate count argument. ----

    @CLibMethod
    @CLibComment(value = "Dot product of two spans. The lengths ride along in the spans, so a " +
            "mismatch is checked once here rather than in every language wrapper.",
            throwsOn = "mismatched lengths")
    public static double dotProduct(DoubleSpan x, DoubleSpan y) {
        if (x.length() != y.length()) {
            throw new IllegalArgumentException("x has " + x.length() + " elements, y has " + y.length());
        }
        double sum = 0;
        for (int i = 0; i < x.length(); i++) {
            sum += x.read(i) * y.read(i);
        }
        return sum;
    }

    @CLibMethod(flags = Noexcept)
    @CLibComment("Returns a new array with the offset added to every element. Checks a span in and an array out.")
    public static double[] addOffset(DoubleSpan values, double offset) {
        double[] result = new double[values.length()];
        for (int i = 0; i < result.length; i++) {
            result[i] = values.read(i) + offset;
        }
        return result;
    }

    @CLibMethod(flags = Noexcept)
    @CLibComment("""
            Returns 0, 1, .. count-1. A count of zero returns an empty array, and a count
            over 128 forces the per-thread scratch buffer to grow, mirroring Strings.repeat.
            """)
    public static double[] getSequence(@CSizeType int count) {
        double[] result = new double[Math.max(count, 0)];
        for (int i = 0; i < result.length; i++) {
            result[i] = i;
        }
        return result;
    }

}
