package us.hebi.graalvm.bindings.runtime.views;

import org.graalvm.nativeimage.c.type.CDoublePointer;
import us.hebi.glue.Buffer6;

import java.nio.DoubleBuffer;

/**
 * caches the double view on top of the native buffer
 *
 * @author Florian Enner
 * @since 05 Aug 2026
 */
public final class DoubleBufferView extends BufferView.CDouble {

    public DoubleBufferView setContent(double[] values) {
        if (clearAndCheckNotNull(values)) {
            return setContent(values, 0, values.length);
        }
        return this;
    }

    public DoubleBufferView setContent(double[] values, int offset, int length) {
        if (clearAndCheckNotNull(values)) {
            ensureSpace(length * Double.BYTES);
            Buffer6.rewind(doubles);
            doubles.put(values, offset, length);
        }
        return this;
    }

}
