package us.hebi.graalvm.bindings.runtime.views;

import org.graalvm.nativeimage.c.type.CCharPointer;
import us.hebi.glue.Buffer6;
import us.hebi.graalvm.bindings.runtime.types.CCharPointerMock;

import java.nio.charset.StandardCharsets;

/**
 * Holds utf8 C strings to return across a C ABI
 *
 * @author Florian Enner
 * @since 05 Aug 2026
 */
public final class Utf8StringView extends BufferView.CChar {

    public Utf8StringView setContent(String string) {
        if (clearAndCheckNotNull(string)) {
            byte[] utf8 = string.getBytes(StandardCharsets.UTF_8);
            return setContent(utf8, 0, utf8.length);
        }
        return this;
    }

    public Utf8StringView setContent(byte[] utf8, int offset, int length) {
        if (clearAndCheckNotNull(utf8)) {
            ensureSpace(length + 1);
            Buffer6.clear(bytes);
            bytes.put(utf8, offset, length);
            bytes.put((byte) '\0');
        }
        return this;
    }

    private byte[] toByteArray() {
        byte[] bytes = new byte[this.bytes.position()];
        Buffer6.rewind(this.bytes);
        this.bytes.get(bytes);
        return bytes;
    }

}
