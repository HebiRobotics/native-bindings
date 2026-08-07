package us.hebi.graalvm.bindings.runtime.structs;

import org.graalvm.nativeimage.c.type.CFloatPointer;
import us.hebi.graalvm.bindings.runtime.pointers.CFloatPointerMock;
import us.hebi.graalvm.bindings.runtime.pointers.MockPointerBase;

/**
 * Array-backed {@link FloatSpan} so that annotated classes can be exercised
 * on HotSpot without building a native image.
 *
 * @author Florian Enner
 * @since 07 Aug 2026
 */
public class FloatSpanMock extends MockPointerBase implements FloatSpan {

    public FloatSpanMock(float... values) {
        this.data = new CFloatPointerMock(values);
        this.length = values.length;
    }

    @Override
    public CFloatPointer data() {
        return data;
    }

    @Override
    public void setData(CFloatPointer data) {
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

    private CFloatPointer data;
    private long length;

}
