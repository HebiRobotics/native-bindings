package us.hebi.graalvm.bindings.runtime.views;

import us.hebi.glue.Buffer6;

/**
 * caches the int view on top of the native buffer
 *
 * @author Florian Enner
 * @since 07 Aug 2026
 */
public final class IntBufferView extends BufferView.CInt {

    public IntBufferView setContent(int[] values) {
        if (clearAndCheckNotNull(values)) {
            return setContent(values, 0, values.length);
        }
        return this;
    }

    public IntBufferView setContent(int[] values, int offset, int length) {
        if (clearAndCheckNotNull(values)) {
            ensureSpace(length * Integer.BYTES);
            Buffer6.rewind(ints);
            ints.put(values, offset, length);
        }
        return this;
    }

}
