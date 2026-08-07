package us.hebi.graalvm.bindings.runtime.views;

import us.hebi.glue.Buffer6;

/**
 * Byte content on top of the native buffer
 *
 * @author Florian Enner
 * @since 07 Aug 2026
 */
public final class ByteBufferView extends BufferView.CByte {

    public ByteBufferView setContent(byte[] values) {
        if (clearAndCheckNotNull(values)) {
            return setContent(values, 0, values.length);
        }
        return this;
    }

    public ByteBufferView setContent(byte[] values, int offset, int length) {
        if (clearAndCheckNotNull(values)) {
            ensureSpace(length);
            Buffer6.rewind(bytes);
            bytes.put(values, offset, length);
        }
        return this;
    }

}
