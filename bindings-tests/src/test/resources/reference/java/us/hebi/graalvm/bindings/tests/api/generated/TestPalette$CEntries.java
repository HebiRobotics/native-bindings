package us.hebi.graalvm.bindings.tests.api.generated;

import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.WordFactory;
import us.hebi.graalvm.bindings.runtime.exceptions.ExceptionHandlers;
import us.hebi.graalvm.bindings.runtime.types.CTypes;
import us.hebi.graalvm.bindings.tests.api.TestPalette;

public final class TestPalette$CEntries {
    @CEntryPoint(
            name = "__bindings_tests_Palette_next"
    )
    public static int bindings_tests_Palette_next(IsolateThread _isolate, int color) {
        try {
            return TestPalette_Color$$CEnum.fromJava(TestPalette.next(TestPalette_Color$$CEnum.toJava(color)));
        } catch (Throwable throwable) {
            ExceptionHandlers.setLastError(throwable);
            return 0;
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Palette_nameOf"
    )
    public static CCharPointer bindings_tests_Palette_nameOf(IsolateThread _isolate, int color) {
        try {
            return CTypes.toTempCharPointer(TestPalette.nameOf(TestPalette_Color$$CEnum.toJava(color)));
        } catch (Throwable throwable) {
            ExceptionHandlers.setLastError(throwable);
            return WordFactory.nullPointer();
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Palette_ordinalOf"
    )
    public static int bindings_tests_Palette_ordinalOf(IsolateThread _isolate, int color) {
        try {
            return TestPalette.ordinalOf(TestPalette_Color$$CEnum.toJava(color));
        } catch (Throwable throwable) {
            ExceptionHandlers.setLastError(throwable);
            return 0;
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Palette_describe"
    )
    public static CCharPointer bindings_tests_Palette_describe(IsolateThread _isolate, int color,
            int marker) {
        try {
            return CTypes.toTempCharPointer(TestPalette.describe(TestPalette_Color$$CEnum.toJava(color), TestPalette_Marker$$CEnum.toJava(marker)));
        } catch (Throwable throwable) {
            ExceptionHandlers.setLastError(throwable);
            return WordFactory.nullPointer();
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Palette_fromOrdinal"
    )
    public static int bindings_tests_Palette_fromOrdinal(IsolateThread _isolate, int ordinal) {
        try {
            return TestPalette_Color$$CEnum.fromJava(TestPalette.fromOrdinal(ordinal));
        } catch (Throwable throwable) {
            ExceptionHandlers.setLastError(throwable);
            return 0;
        }
    }
}
