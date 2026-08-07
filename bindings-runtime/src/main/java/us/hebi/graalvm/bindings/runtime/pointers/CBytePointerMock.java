package us.hebi.graalvm.bindings.runtime.pointers;

/**
 * Array-backed {@link CBytePointer} so that annotated classes can be exercised
 * on HotSpot without building a native image.
 *
 * @author Florian Enner
 * @since 07 Aug 2026
 */
public class CBytePointerMock extends CCharPointerMock implements CBytePointer {

    public CBytePointerMock(byte[] array, int offset) {
        super(array, offset);
    }

    public CBytePointerMock(byte... array) {
        this(array, 0);
    }

    @Override
    public CBytePointer addressOf(int index) {
        return new CBytePointerMock(array, this.offset + index);
    }

}
