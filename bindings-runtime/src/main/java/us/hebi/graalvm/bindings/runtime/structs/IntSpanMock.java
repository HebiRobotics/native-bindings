package us.hebi.graalvm.bindings.runtime.structs;

import org.graalvm.nativeimage.c.type.CIntPointer;
import us.hebi.graalvm.bindings.runtime.pointers.CIntPointerMock;
import us.hebi.graalvm.bindings.runtime.pointers.MockPointerBase;

/**
 * Array-backed {@link IntSpan} so that annotated classes can be exercised
 * on HotSpot without building a native image.
 *
 * @author Florian Enner
 * @since 07 Aug 2026
 */
public class IntSpanMock extends MockPointerBase implements IntSpan {

    public IntSpanMock(int... values) {
        this.data = new CIntPointerMock(values);
        this.length = values.length;
    }

    @Override
    public CIntPointer data() {
        return data;
    }

    @Override
    public void setData(CIntPointer data) {
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

    private CIntPointer data;
    private long length;

}
