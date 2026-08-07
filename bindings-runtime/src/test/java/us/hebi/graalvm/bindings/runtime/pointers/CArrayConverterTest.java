package us.hebi.graalvm.bindings.runtime.pointers;

import org.graalvm.nativeimage.c.type.CDoublePointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.word.SignedWord;
import org.junit.Test;
import us.hebi.glue.DirectBuffer6;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.Assert.*;

/**
 * Covers both code paths: the direct-buffer pointers below expose a real address, so
 * they take the {@code Unsafe.copyMemory} fast path on HotSpot, while the array-backed
 * mocks throw from {@code rawValue()} and land in the element loop.
 *
 * @author Florian Enner
 * @since 02 Aug 2026
 */
public class CArrayConverterTest {

    @Test
    public void testCopyFromCArrayFastPath() {
        var src = DirectDoublePointer.of(1.5, 2.5, 3.5);
        var dst = new double[3];
        CArrayConverter.copyFromCArray(3, src, dst);
        assertArrayEquals(new double[]{1.5, 2.5, 3.5}, dst, delta);
    }

    @Test
    public void testCopyFromCArrayMockFallback() {
        var dst = new double[3];
        CArrayConverter.copyFromCArray(3, CDoublePointerMock.wrap(1.5, 2.5, 3.5), dst);
        assertArrayEquals(new double[]{1.5, 2.5, 3.5}, dst, delta);
    }

    @Test
    public void testCopyFromCArrayWithOffsets() {
        var src = DirectDoublePointer.of(1.0, 2.0, 3.0, 4.0);
        var dst = new double[]{-1, -1, -1, -1};
        CArrayConverter.copyFromCArray(2, src, 1, dst, 2);
        assertArrayEquals(new double[]{-1, -1, 2.0, 3.0}, dst, delta);

        var mockDst = new double[]{-1, -1, -1, -1};
        CArrayConverter.copyFromCArray(2, CDoublePointerMock.wrap(1.0, 2.0, 3.0, 4.0), 1, mockDst, 2);
        assertArrayEquals(new double[]{-1, -1, 2.0, 3.0}, mockDst, delta);
    }

    @Test
    public void testCopyFromCArrayRejectsBadArguments() {
        var src = DirectDoublePointer.of(1.0);
        assertThrows(IllegalArgumentException.class,
                () -> CArrayConverter.copyFromCArray(-1, src, 0, new double[1], 0));
    }

    @Test
    public void testShortDestinationThrowsInsteadOfCorrupting() {
        var src = DirectDoublePointer.of(1.0, 2.0, 3.0, 4.0, 5.0);
        assertThrows(ArrayIndexOutOfBoundsException.class,
                () -> CArrayConverter.copyFromCArray(5, src, new double[3]));
    }

    @Test
    public void testCopyToCArrayRoundTrip() {
        var values = new double[]{1.5, 2.5, 3.5};
        var pointer = DirectDoublePointer.allocate(3);
        CArrayConverter.copyToCArray(3, values, pointer);
        var result = new double[3];
        CArrayConverter.copyFromCArray(3, pointer, result);
        assertArrayEquals(values, result, delta);

        var mock = CDoublePointerMock.allocate(3);
        CArrayConverter.copyToCArray(3, values, mock);
        assertArrayEquals(values, mock.toArray(), delta);
    }

    @Test
    public void testIntCopiesRoundTrip() {
        var values = new int[]{1, -2, 3};
        var pointer = DirectIntPointer.allocate(3);
        CArrayConverter.copyToCArray(3, values, pointer);
        var result = new int[3];
        CArrayConverter.copyFromCArray(3, pointer, result);
        assertArrayEquals(values, result);
    }

    @Test
    public void testResizeJavaReusesExactMatchOnly() {
        var exact = new double[4];
        assertSame(exact, CArrayConverter.resizeJava(4, exact));
        assertEquals(2, CArrayConverter.resizeJava(2, exact).length);
        assertEquals(3, CArrayConverter.resizeJava(3, null).length);
    }

    @Test
    public void testFromCArrayExactResizesAndCopies() {
        var src = DirectDoublePointer.of(1.0, 2.0);
        var dst = CArrayConverter.fromCArrayExact(2, src, null);
        assertArrayEquals(new double[]{1.0, 2.0}, dst, delta);
        assertSame(dst, CArrayConverter.fromCArrayExact(2, src, dst));
    }

    private static final double delta = 1e-9;

    /**
     * A {@link CDoublePointer} over off-heap memory. Unlike the mocks it has a real
     * address, which is what routes the converter onto the fast path.
     */
    static class DirectDoublePointer extends MockPointerBase implements CDoublePointer {

        static DirectDoublePointer of(double... values) {
            DirectDoublePointer pointer = allocate(values.length);
            for (int i = 0; i < values.length; i++) {
                pointer.write(i, values[i]);
            }
            return pointer;
        }

        static DirectDoublePointer allocate(int length) {
            ByteBuffer buffer = ByteBuffer.allocateDirect(length * Double.BYTES);
            buffer.order(ByteOrder.nativeOrder());
            return new DirectDoublePointer(buffer);
        }

        DirectDoublePointer(ByteBuffer buffer) {
            this.buffer = buffer;
        }

        @Override
        public long rawValue() {
            return DirectBuffer6.getAddress(buffer);
        }

        @Override
        public double read() {
            return read(0);
        }

        @Override
        public double read(int index) {
            return buffer.getDouble(index * Double.BYTES);
        }

        @Override
        public double read(SignedWord index) {
            return read((int) index.rawValue());
        }

        @Override
        public void write(double value) {
            write(0, value);
        }

        @Override
        public void write(int index, double value) {
            buffer.putDouble(index * Double.BYTES, value);
        }

        @Override
        public void write(SignedWord index, double value) {
            write((int) index.rawValue(), value);
        }

        @Override
        public CDoublePointer addressOf(int index) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CDoublePointer addressOf(SignedWord index) {
            throw new UnsupportedOperationException();
        }

        final ByteBuffer buffer;

    }

    /**
     * The {@link CIntPointer} counterpart of {@link DirectDoublePointer}.
     */
    static class DirectIntPointer extends MockPointerBase implements CIntPointer {

        static DirectIntPointer allocate(int length) {
            ByteBuffer buffer = ByteBuffer.allocateDirect(length * Integer.BYTES);
            buffer.order(ByteOrder.nativeOrder());
            return new DirectIntPointer(buffer);
        }

        DirectIntPointer(ByteBuffer buffer) {
            this.buffer = buffer;
        }

        @Override
        public long rawValue() {
            return DirectBuffer6.getAddress(buffer);
        }

        @Override
        public int read() {
            return read(0);
        }

        @Override
        public int read(int index) {
            return buffer.getInt(index * Integer.BYTES);
        }

        @Override
        public int read(SignedWord index) {
            return read((int) index.rawValue());
        }

        @Override
        public void write(int value) {
            write(0, value);
        }

        @Override
        public void write(int index, int value) {
            buffer.putInt(index * Integer.BYTES, value);
        }

        @Override
        public void write(SignedWord index, int value) {
            write((int) index.rawValue(), value);
        }

        @Override
        public CIntPointer addressOf(int index) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CIntPointer addressOf(SignedWord index) {
            throw new UnsupportedOperationException();
        }

        final ByteBuffer buffer;

    }

}
