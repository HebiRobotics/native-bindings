package us.hebi.graalvm.bindings.runtime.types;

import org.graalvm.nativeimage.c.type.CDoublePointer;

/**
 * Array-backed {@link DoubleSpan} so that annotated classes can be exercised
 * on HotSpot without building a native image.
 *
 * @author Florian Enner
 * @since 01 Aug 2026
 */
public class DoubleSpanMock extends MockPointerBase implements DoubleSpan {

    public DoubleSpanMock(double... values) {
        this.data = new CDoublePointerMock(values);
        this.length = values.length;
    }

    @Override
    public CDoublePointer data() {
        return data;
    }

    @Override
    public void setData(CDoublePointer data) {
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

    private CDoublePointer data;
    private long length;

}
