package us.hebi.graalvm.bindings.runtime.pointers;

import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.word.SignedWord;

import java.util.Arrays;

/**
 * Array-backed {@link CIntPointer} so that annotated classes can be exercised
 * on HotSpot without building a native image.
 *
 * @author Florian Enner
 * @since 07 Aug 2026
 */
public class CIntPointerMock extends MockPointerBase implements CIntPointer {

    public CIntPointerMock(int[] array, int offset) {
        this.array = array;
        this.offset = offset;
    }

    public CIntPointerMock(int... array) {
        this(array, 0);
    }

    public static CIntPointerMock allocate(int length) {
        return new CIntPointerMock(new int[length]);
    }

    public static CIntPointerMock wrap(int... values) {
        return new CIntPointerMock(values);
    }

    @Override
    public boolean isNull() {
        return array == null;
    }

    @Override
    public int read() {
        return array[offset];
    }

    @Override
    public int read(int index) {
        return array[offset + index];
    }

    @Override
    public int read(SignedWord index) {
        return read((int) index.rawValue());
    }

    @Override
    public void write(int value) {
        array[offset] = value;
    }

    @Override
    public void write(int index, int value) {
        array[offset + index] = value;
    }

    @Override
    public void write(SignedWord index, int value) {
        write((int) index.rawValue(), value);
    }

    @Override
    public CIntPointer addressOf(int index) {
        return new CIntPointerMock(array, this.offset + index);
    }

    @Override
    public CIntPointer addressOf(SignedWord index) {
        return addressOf((int) index.rawValue());
    }

    @Override
    public String toString() {
        return Arrays.toString(toArray());
    }

    public int[] toArray() {
        return offset == 0 ? array : Arrays.copyOfRange(array, offset, array.length);
    }

    final int[] array;
    final int offset;

}
