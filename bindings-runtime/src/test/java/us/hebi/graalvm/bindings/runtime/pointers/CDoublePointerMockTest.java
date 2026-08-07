package us.hebi.graalvm.bindings.runtime.pointers;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Pins the offset semantics, because an earlier version of this class read and wrote
 * index zero regardless of the offset it was created with.
 *
 * @author Florian Enner
 * @since 02 Aug 2026
 */
public class CDoublePointerMockTest {

    @Test
    public void testToArrayReturnsContents() {
        assertArrayEquals(new double[]{1.0, 2.0, 3.0},
                CDoublePointerMock.wrap(1.0, 2.0, 3.0).toArray(), delta);
    }

    @Test
    public void testToArrayStartsAtOffset() {
        var view = (CDoublePointerMock) CDoublePointerMock.wrap(1.0, 2.0, 3.0).addressOf(1);
        assertArrayEquals(new double[]{2.0, 3.0}, view.toArray(), delta);
    }

    @Test
    public void testReadsAreRelativeToOffset() {
        var view = CDoublePointerMock.wrap(1.0, 2.0, 3.0).addressOf(1);
        assertEquals(2.0, view.read(), delta);
        assertEquals(3.0, view.read(1), delta);
    }

    @Test
    public void testWritesAreRelativeToOffset() {
        var mock = CDoublePointerMock.allocate(3);
        mock.addressOf(1).write(7.0);
        assertArrayEquals(new double[]{0.0, 7.0, 0.0}, mock.toArray(), delta);
    }

    private static final double delta = 1e-9;

}
