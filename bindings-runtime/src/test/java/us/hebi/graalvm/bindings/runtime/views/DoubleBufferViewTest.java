package us.hebi.graalvm.bindings.runtime.views;

import org.junit.Test;
import us.hebi.graalvm.bindings.runtime.pointers.CPointers;

import static org.junit.Assert.*;

/**
 * Goes through {@code getPointer} rather than the buffer, so the JVM branch is covered too.
 *
 * @author Florian Enner
 * @since 05 Aug 2026
 */
public class DoubleBufferViewTest {

    @Test
    public void testContentRoundTrips() {
        var view = new DoubleBufferView().setContent(new double[]{1, 2, 3});
        assertArrayEquals(new double[]{1, 2, 3}, readBack(view, 3), delta);
    }

    @Test
    public void testRangeCopiesTheSelectedElements() {
        var view = new DoubleBufferView().setContent(new double[]{1, 2, 3, 4}, 1, 2);
        assertArrayEquals(new double[]{2, 3}, readBack(view, 2), delta);
    }

    @Test
    public void testNullContentReportsNullPointer() {
        assertTrue(new DoubleBufferView().setContent(null).getPointer().isNull());
    }

    /** An empty array is a valid pointer with nothing behind it, unlike a null one. */
    @Test
    public void testEmptyContentIsNotNull() {
        assertFalse(new DoubleBufferView().setContent(new double[0]).getPointer().isNull());
    }

    /**
     * The initial buffer holds eight doubles, so this is the growth path. A view that sized
     * itself in elements rather than bytes would overflow here.
     */
    @Test
    public void testGrowthKeepsTheContentReadable() {
        double[] values = new double[512];
        for (int i = 0; i < values.length; i++) {
            values[i] = i;
        }
        var view = new DoubleBufferView().setContent(new double[]{1, 2, 3});
        assertArrayEquals(values, readBack(view.setContent(values), values.length), delta);
    }

    private static double[] readBack(DoubleBufferView view, int length) {
        return CPointers.getDoubles(view.getPointer(), length);
    }

    private static final double delta = 1e-9;

}
