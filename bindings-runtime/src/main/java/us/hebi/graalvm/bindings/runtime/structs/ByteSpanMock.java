package us.hebi.graalvm.bindings.runtime.structs;

import us.hebi.graalvm.bindings.runtime.pointers.CBytePointer;
import us.hebi.graalvm.bindings.runtime.pointers.CBytePointerMock;
import us.hebi.graalvm.bindings.runtime.pointers.MockPointerBase;

/**
 * Array-backed {@link ByteSpan} so that annotated classes can be exercised
 * on HotSpot without building a native image.
 *
 * @author Florian Enner
 * @since 07 Aug 2026
 */
public class ByteSpanMock extends MockPointerBase implements ByteSpan {

    public ByteSpanMock(byte... values) {
        this.data = new CBytePointerMock(values);
        this.length = values.length;
    }

    @Override
    public CBytePointer data() {
        return data;
    }

    @Override
    public void setData(CBytePointer data) {
        this.data = data;
    }

    @Override
    public long length() {
        return length;
    }

    @Override
    public void setLength(long length) {
        this.length = length;
    }

    private CBytePointer data;
    private long length;

}
