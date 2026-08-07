package us.hebi.graalvm.bindings.runtime.structs;

import us.hebi.graalvm.bindings.runtime.pointers.CPointers;

/**
 * Convenience accessors for the span structs. They cannot live on the interfaces
 * themselves because native-image rejects any method on a {@code @CStruct} interface
 * that is not an annotated accessor. Future element types add overloads here.
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

    /**
     * Array-backed mock for exercising annotated classes on HotSpot,
     * where no native struct exists.
     */
    public static DoubleSpanMock toDoubleSpan(double... values) {
        return new DoubleSpanMock(values);
    }

}
