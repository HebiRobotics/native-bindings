package us.hebi.graalvm.bindings.runtime.pointers;

import org.graalvm.nativeimage.c.type.CCharPointer;

/**
 * A {@link CCharPointer} that carries binary data rather than a 0 terminated
 * string. The distinction only exists in the model: the string conventions
 * (UTF-8 decoding, {@code c_char_p}, {@code str} hints) stay on
 * {@code CCharPointer}, while this type maps to {@code uint8_t*} data with an
 * explicit length.
 *
 * @author Florian Enner
 * @since 07 Aug 2026
 */
public interface CBytePointer extends CCharPointer {
}
