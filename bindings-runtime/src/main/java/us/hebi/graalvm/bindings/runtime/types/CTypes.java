package us.hebi.graalvm.bindings.runtime.types;

import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CDoublePointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.word.WordFactory;
import us.hebi.glue.Buffer6;
import us.hebi.glue.DirectBuffer6;
import us.hebi.graalvm.bindings.runtime.Configuration;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Marshalling helpers referenced by generated entry points.
 *
 * @author Florian Enner
 * @since 02 Aug 2024
 */
public class CTypes {

    public static CCharPointer toTempCharPointer(String string) {
        return string == null ? WordFactory.<CCharPointer>nullPointer() : WordFactory.<CCharPointer>pointer(toTempCString(string));
    }

    public static CCharPointer toTempCharPointer(byte[] utf8, int offset, int length) {
        return utf8 == null ? WordFactory.<CCharPointer>nullPointer() : WordFactory.<CCharPointer>pointer(toTempCString(utf8, offset, length));
    }

    /**
     * @param string input
     * @return an address to a matching c string that is only valid for this call. Needs to be copied immediately. null maps to nullptr.
     */
    public static long toTempCString(String string) {
        if (string == null) return 0;
        byte[] utf8 = string.getBytes(StandardCharsets.UTF_8);
        return toTempCString(utf8, 0, utf8.length);
    }

    /**
     * One direct buffer per thread, grown on demand and never released. Reusing it is what
     * makes the returned address valid only until this thread's next call. Threads that
     * never marshal a string never allocate.
     */
    public static long toTempCString(byte[] utf8, int offset, int length) {
        if (utf8 == null) return 0;
        int strlen = length + 1;
        ByteBuffer buffer = utf8StringBuffer.get();
        if (buffer == null || buffer.capacity() < strlen) {
            buffer = ByteBuffer.allocateDirect(Math.max(strlen, 128));
            buffer.order(ByteOrder.nativeOrder());
            utf8StringBuffer.set(buffer);
        }
        Buffer6.rewind(buffer);
        buffer.put(utf8, offset, length);
        buffer.put((byte) '\0');
        return DirectBuffer6.getAddress(buffer);
    }

    private static final ThreadLocal<ByteBuffer> utf8StringBuffer = new ThreadLocal<ByteBuffer>();

    public static int capSizeToInt(long size_t) {
        // Anything over 31 bits gets capped to the max
        if (size_t >>> 31 == 0) {
            return (int) size_t;
        } else {
            Configuration.printDebug("Capped large size_t value to 31 bits: " + size_t);
            return Integer.MAX_VALUE;
        }
    }

    public static long capSizeToLong(long size_t) {
        // Anything over 63 bits gets capped to the max
        if (size_t >= 0) {
            return size_t;
        } else {
            Configuration.printDebug("Capped large size_t value to 63 bits: " + size_t);
            return Long.MAX_VALUE;
        }
    }

    public static int silentCapSize32(int size32) {
        return size32 < 0 ? Integer.MAX_VALUE : size32;
    }

    public static CCharPointerMock toUtf8Chars(String values) {
        byte[] bytes = values.getBytes(StandardCharsets.UTF_8);
        byte[] full = Arrays.copyOfRange(bytes, 0, bytes.length + 1);
        full[full.length - 1] = '\0';
        return new CCharPointerMock(full, 0);
    }

    public static CDoublePointerMock toDoublePointer(double... values) {
        return new CDoublePointerMock(values, 0);
    }

    public static CDoublePointerMock allocateCDoublePointer(int size) {
        return new CDoublePointerMock(new double[size], 0);
    }

    /**
     * Decodes a UTF-8 encoded, 0 terminated C {@code char*} to a Java string.
     * <p>
     * The native image check is intentionally a call rather than a cached constant. A
     * {@code static final} would be folded at build time, where the builder JVM is not
     * a native image, and the fast path would never be taken inside the image.
     *
     * @param utf8String the pointer to a UTF-8 encoded, 0 terminated C string
     * @return a Java string
     */
    public static String utf8ToJavaString(CCharPointer utf8String) {
        if (ImageInfo.inImageRuntimeCode()) {
            return CTypeConversion.utf8ToJavaString(utf8String);
        }
        byte[] bytes = getBytes(utf8String, strLength(utf8String));
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public static boolean isEqual(CCharPointer a, byte[] b, int length) {
        if (a.isNull() || b == null) {
            throw new IllegalArgumentException("input can't be null");
        }
        for (int i = 0; i < length; i++) {
            if (a.read(i) != b[i]) {
                return false;
            }
        }
        return true;
    }

    public static int strLength(CCharPointer chars) {
        if (chars.isNull()) return 0;
        for (int i = 0; ; i++) {
            if (chars.read(i) == '\0') {
                return i;
            }
        }
    }

    public static byte[] getBytes(CCharPointer input, int len) {
        byte[] bytes = new byte[len];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = input.read(i);
        }
        return bytes;
    }

    public static double[] getDoubles(CDoublePointer src, int size) {
        double[] result = new double[size];
        for (int i = 0; i < size; i++) {
            result[i] = src.read(i);
        }
        return result;
    }

}
