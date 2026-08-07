package us.hebi.graalvm.bindings.runtime.pointers;

import org.graalvm.nativeimage.c.type.CFloatPointer;
import org.graalvm.word.SignedWord;

import java.util.Arrays;

/**
 * Array-backed {@link CFloatPointer} so that annotated classes can be exercised
 * on HotSpot without building a native image.
 *
 * @author Florian Enner
 * @since 07 Aug 2026
 */
public class CFloatPointerMock extends MockPointerBase implements CFloatPointer {

    public CFloatPointerMock(float[] array, int offset) {
        this.array = array;
        this.offset = offset;
    }

    public CFloatPointerMock(float... array) {
        this(array, 0);
    }

    public static CFloatPointerMock allocate(int length) {
        return new CFloatPointerMock(new float[length]);
    }

    public static CFloatPointerMock wrap(float... values) {
        return new CFloatPointerMock(values);
    }

    @Override
    public boolean isNull() {
        return array == null;
    }

    @Override
    public float read() {
        return array[offset];
    }

    @Override
    public float read(int index) {
        return array[offset + index];
    }

    @Override
    public float read(SignedWord index) {
        return read((int) index.rawValue());
    }

    @Override
    public void write(float value) {
        array[offset] = value;
    }

    @Override
    public void write(int index, float value) {
        array[offset + index] = value;
    }

    @Override
    public void write(SignedWord index, float value) {
        write((int) index.rawValue(), value);
    }

    @Override
    public CFloatPointer addressOf(int index) {
        return new CFloatPointerMock(array, this.offset + index);
    }

    @Override
    public CFloatPointer addressOf(SignedWord index) {
        return addressOf((int) index.rawValue());
    }

    @Override
    public String toString() {
        return Arrays.toString(toArray());
    }

    public float[] toArray() {
        return offset == 0 ? array : Arrays.copyOfRange(array, offset, array.length);
    }

    final float[] array;
    final int offset;

}
