package us.hebi.graalvm.bindings.runtime.pointers;

import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.SignedWord;

import java.util.Arrays;

/**
 * Array-backed {@link CCharPointer} so that annotated classes can be exercised
 * on HotSpot without building a native image.
 *
 * @author Florian Enner
 * @since 05 Aug 2024
 */
public class CCharPointerMock extends MockPointerBase implements CCharPointer {

    public CCharPointerMock(byte[] array, int offset) {
        this.array = array;
        this.offset = offset;
    }

    @Override
    public boolean isNull() {
        return array == null;
    }

    @Override
    public byte read() {
        return array[offset];
    }

    @Override
    public byte read(int index) {
        return array[offset + index];
    }

    @Override
    public byte read(SignedWord index) {
        return read((int) index.rawValue());
    }

    @Override
    public void write(byte value) {
        array[offset] = value;
    }

    @Override
    public void write(int index, byte value) {
        array[offset + index] = value;
    }

    @Override
    public void write(SignedWord index, byte value) {
        write((int) index.rawValue(), value);
    }

    @Override
    public CCharPointer addressOf(int index) {
        return new CCharPointerMock(array, this.offset + index);
    }

    @Override
    public CCharPointer addressOf(SignedWord index) {
        return addressOf((int) index.rawValue());
    }

    @Override
    public String toString() {
        return Arrays.toString(toArray());
    }

    public byte[] toArray() {
        return offset == 0 ? array : Arrays.copyOfRange(array, offset, array.length);
    }

    final byte[] array;
    final int offset;

}
