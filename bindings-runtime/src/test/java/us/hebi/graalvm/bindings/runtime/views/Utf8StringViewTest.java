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
public class Utf8StringViewTest {

    @Test
    public void testContentRoundTrips() {
        assertEquals("abc", readBack(new Utf8StringView().setContent("abc")));
    }

    @Test
    public void testNonAsciiCrossesAsUtf8() {
        assertEquals("mañana", readBack(new Utf8StringView().setContent("mañana")));
    }

    @Test
    public void testNullContentReportsNullPointer() {
        assertTrue(new Utf8StringView().setContent(null).getPointer().isNull());
    }

    /** A reused view must not report the previous call's text. */
    @Test
    public void testReuseReplacesTheOldText() {
        var view = new Utf8StringView().setContent("first");
        assertTrue(view.setContent(null).getPointer().isNull());
        assertEquals("second", readBack(view.setContent("second")));
        assertEquals("third", readBack(view.setContent("third")));
    }

    @Test
    public void testGrowthKeepsTheContentReadable() {
        var view = new Utf8StringView().setContent("short");
        String longText = "x".repeat(4096);
        assertEquals(longText, readBack(view.setContent(longText)));
    }

    private static String readBack(Utf8StringView view) {
        return CPointers.utf8ToJavaString(view.getPointer());
    }

}
