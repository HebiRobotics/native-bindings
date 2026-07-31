package us.hebi.graalvm.bindings.runtime.types;

import org.graalvm.nativeimage.c.type.CDoublePointer;

/**
 * Borrowed view of a caller-owned double buffer, i.e. a C {@code (const double*, size_t)}
 * pair passed as one value. Valid only for the duration of the call it was passed to.
 * The buffer is never owned or freed through this class.
 *
 * @author Florian Enner
 * @since 01 Aug 2026
 */
public final class DoubleSpan {

    public static DoubleSpan of(CDoublePointer pointer, long length) {
        return new DoubleSpan(pointer, CTypes.capSizeToLong(length));
    }

    /**
     * Array-backed span so that annotated classes can be exercised on HotSpot
     * without building a native image.
     */
    public static DoubleSpan of(double... values) {
        return new DoubleSpan(new CDoublePointerMock(values), values.length);
    }

    private DoubleSpan(CDoublePointer pointer, long length) {
        this.pointer = pointer;
        // No reference comparison: CDoublePointer is a GraalVM word type inside the
        // image, where comparing against a Java null is a build-time error
        this.length = pointer.isNull() ? 0 : length;
    }

    public double read(int index) {
        return pointer.read(index);
    }

    /**
     * @return the element count, capped to 31 bits like other size_t inputs
     */
    public int length() {
        return CTypes.capSizeToInt(length);
    }

    public boolean isEmpty() {
        return length == 0;
    }

    public double[] toArray() {
        return CTypes.getDoubles(pointer, length());
    }

    public CDoublePointer getPointer() {
        return pointer;
    }

    private final CDoublePointer pointer;
    private final long length;

}
