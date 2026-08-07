package us.hebi.graalvm.bindings.runtime.views;

import us.hebi.glue.Buffer6;

/**
 * caches the float view on top of the native buffer
 *
 * @author Florian Enner
 * @since 07 Aug 2026
 */
public final class FloatBufferView extends BufferView.CFloat {

    public FloatBufferView setContent(float[] values) {
        if (clearAndCheckNotNull(values)) {
            return setContent(values, 0, values.length);
        }
        return this;
    }

    public FloatBufferView setContent(float[] values, int offset, int length) {
        if (clearAndCheckNotNull(values)) {
            ensureSpace(length * Float.BYTES);
            Buffer6.rewind(floats);
            floats.put(values, offset, length);
        }
        return this;
    }

}
