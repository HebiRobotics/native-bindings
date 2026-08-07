package us.hebi.graalvm.bindings.runtime.structs;

import us.hebi.graalvm.bindings.runtime.pointers.CPointers;

/**
 * Convenience accessors for {@code @CStruct} annotated interface. They cannot live
 * on the interface itself because native-image rejects any method on a {@code @CStruct}
 * interface that is not an annotated accessor.
 *
 * @author Florian Enner
 * @since 01 Aug 2026
 */
public final class CStructs {

    private CStructs() {
    }

    /**
     * @return the element count, capped to 31 bits like other size_t inputs
     */
    public static int size(DoubleSpan span) {
        return CPointers.capSizeToInt(span.length());
    }

    public static double read(DoubleSpan span, int index) {
        return span.data().read(index);
    }

    public static boolean isEmpty(DoubleSpan span) {
        return span.length() == 0;
    }

    public static double[] toArray(DoubleSpan span) {
        return CPointers.getDoubles(span.data(), size(span));
    }

}
