package us.hebi.graalvm.bindings.runtime.types;

/**
 * Convenience accessors for {@link DoubleSpan}. They cannot live on the interface
 * itself because native-image rejects any method on a {@code @CStruct} interface
 * that is not an annotated accessor.
 *
 * @author Florian Enner
 * @since 01 Aug 2026
 */
public final class Spans {

    private Spans() {
    }

    /**
     * @return the element count, capped to 31 bits like other size_t inputs
     */
    public static int size(DoubleSpan span) {
        return CTypes.capSizeToInt(span.length());
    }

    public static double read(DoubleSpan span, int index) {
        return span.data().read(index);
    }

    public static boolean isEmpty(DoubleSpan span) {
        return span.length() == 0;
    }

    public static double[] toArray(DoubleSpan span) {
        return CTypes.getDoubles(span.data(), size(span));
    }

}
