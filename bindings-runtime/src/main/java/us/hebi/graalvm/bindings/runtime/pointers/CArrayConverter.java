package us.hebi.graalvm.bindings.runtime.pointers;

import org.graalvm.nativeimage.c.type.CDoublePointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import us.hebi.glue.UnsafeAccess;

import java.util.Arrays;

/**
 * Bulk copies between C arrays and Java arrays, for entry points that move whole
 * buffers rather than single elements. All lengths and offsets are in elements, not
 * bytes. The fast path is a single {@code Unsafe.copyMemory}, and the element loop
 * doubles as the HotSpot path: the array-backed mocks throw from {@code rawValue()},
 * which is what routes them past the fast path, so the catch below is load-bearing
 * rather than defensive.
 *
 * @author Florian Enner
 * @since 30 Nov 2023
 */
public class CArrayConverter {

    public static double[] fromCArrayExact(int length, CDoublePointer src, double[] dst) {
        dst = resizeJava(length, dst);
        copyFromCArray(length, src, dst);
        return dst;
    }

    public static void copyFromCArray(int length, CDoublePointer src, double[] dst) {
        // The length guard keeps Unsafe from ever writing past the array. A short
        // dst falls through and fails in the loop with an honest index.
        if (useCopyMemory && length > 0 && dst.length >= length) {
            try {
                UnsafeAccess.UNSAFE.copyMemory(
                        null, src.rawValue(),
                        dst, UnsafeAccess.DOUBLE_ARRAY_BASE_OFFSET,
                        (long) length * Double.BYTES);
                return;
            } catch (UnsupportedOperationException mockPointer) {
                // fall back to the element loop
            }
        }
        for (int i = 0; i < length; i++) {
            dst[i] = src.read(i);
        }
    }

    public static void copyFromCArray(int length, CDoublePointer src, int srcOffset, double[] dst, int dstOffset) {
        if (length < 0) {
            throw new IllegalArgumentException("negative length: " + length);
        }
        if (src.isNull()) {
            throw new IllegalArgumentException("nullptr source");
        }
        if (length == 0) return;
        if (useCopyMemory && dst.length - dstOffset >= length && dstOffset >= 0) {
            try {
                UnsafeAccess.UNSAFE.copyMemory(
                        null, src.rawValue() + (long) srcOffset * Double.BYTES,
                        dst, UnsafeAccess.DOUBLE_ARRAY_BASE_OFFSET + (long) dstOffset * Double.BYTES,
                        (long) length * Double.BYTES);
                return;
            } catch (UnsupportedOperationException mockPointer) {
                // fall back to the element loop
            }
        }
        for (int i = 0; i < length; i++) {
            dst[dstOffset + i] = src.read(srcOffset + i);
        }
    }

    public static void copyToCArray(int length, double[] src, CDoublePointer dst) {
        if (useCopyMemory && length > 0 && src.length >= length) {
            try {
                UnsafeAccess.UNSAFE.copyMemory(
                        src, UnsafeAccess.DOUBLE_ARRAY_BASE_OFFSET,
                        null, dst.rawValue(),
                        (long) length * Double.BYTES);
                return;
            } catch (UnsupportedOperationException mockPointer) {
                // fall back to the element loop
            }
        }
        for (int i = 0; i < length; i++) {
            dst.write(i, src[i]);
        }
    }

    public static void copyFromCArray(int length, CIntPointer src, int[] dst) {
        if (useCopyMemory && length > 0 && dst.length >= length) {
            try {
                UnsafeAccess.UNSAFE.copyMemory(
                        null, src.rawValue(),
                        dst, UnsafeAccess.INT_ARRAY_BASE_OFFSET,
                        (long) length * Integer.BYTES);
                return;
            } catch (UnsupportedOperationException mockPointer) {
                // fall back to the element loop
            }
        }
        for (int i = 0; i < length; i++) {
            dst[i] = src.read(i);
        }
    }

    public static void copyToCArray(int length, int[] src, CIntPointer dst) {
        if (useCopyMemory && length > 0 && src.length >= length) {
            try {
                UnsafeAccess.UNSAFE.copyMemory(
                        src, UnsafeAccess.INT_ARRAY_BASE_OFFSET,
                        null, dst.rawValue(),
                        (long) length * Integer.BYTES);
                return;
            } catch (UnsupportedOperationException mockPointer) {
                // fall back to the element loop
            }
        }
        for (int i = 0; i < length; i++) {
            dst.write(i, src[i]);
        }
    }

    /**
     * Returns the array if it already has exactly the requested length, otherwise a new
     * one. The contents are not preserved on a resize, callers overwrite the result.
     */
    public static double[] resizeJava(int length, double[] array) {
        if (array != null && array.length == length) {
            return array;
        }
        return new double[length];
    }

    private static final boolean useCopyMemory = isCopyMemoryAvailable();

    private static boolean isCopyMemoryAvailable() {
        if (!UnsafeAccess.isAvailable()) return false;
        try {

            // Some platforms may throw a "copy memory not available" error
            byte[] a = new byte[]{1, 2, 3};
            byte[] b = new byte[a.length];
            UnsafeAccess.UNSAFE.copyMemory(
                    a, UnsafeAccess.BYTE_ARRAY_BASE_OFFSET,
                    b, UnsafeAccess.BYTE_ARRAY_BASE_OFFSET,
                    a.length);

            // Make sure the results are actually the same
            return Arrays.equals(a, b);

        } catch (Throwable t) {
            return false;
        }
    }

}
