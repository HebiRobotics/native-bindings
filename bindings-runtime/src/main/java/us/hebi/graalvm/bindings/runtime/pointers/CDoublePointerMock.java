package us.hebi.graalvm.bindings.runtime.pointers;

import org.graalvm.nativeimage.c.type.CDoublePointer;
import org.graalvm.word.SignedWord;

import java.util.Arrays;

/**
 * Array-backed {@link CDoublePointer} so that annotated classes can be exercised
 * on HotSpot without building a native image.
 *
 * @author Florian Enner
 * @since 02 Aug 2024
 */
public class CDoublePointerMock extends MockPointerBase implements CDoublePointer {

    public CDoublePointerMock(double[] array, int offset) {
        this.array = array;
        this.offset = offset;
    }

    public CDoublePointerMock(double... array) {
        this(array, 0);
    }

    public static CDoublePointerMock allocate(int length) {
        return new CDoublePointerMock(new double[length]);
    }

    public static CDoublePointerMock wrap(double... values) {
        return new CDoublePointerMock(values);
    }

    @Override
    public boolean isNull() {
        return array == null;
    }

    @Override
    public double read() {
        return array[offset];
    }

    @Override
    public double read(int index) {
        return array[offset + index];
    }

    @Override
    public double read(SignedWord index) {
        return read((int) index.rawValue());
    }

    @Override
    public void write(double value) {
        array[offset] = value;
    }

    @Override
    public void write(int index, double value) {
        array[offset + index] = value;
    }

    @Override
    public void write(SignedWord index, double value) {
        write((int) index.rawValue(), value);
    }

    @Override
    public CDoublePointer addressOf(int index) {
        return new CDoublePointerMock(array, this.offset + index);
    }

    @Override
    public CDoublePointer addressOf(SignedWord index) {
        return addressOf((int) index.rawValue());
    }

    @Override
    public String toString() {
        return Arrays.toString(toArray());
    }

    public double[] toArray() {
        return offset == 0 ? array : Arrays.copyOfRange(array, offset, array.length);
    }

    final double[] array;
    final int offset;

}
