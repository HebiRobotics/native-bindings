package us.hebi.graalvm.bindings.tests.api.generated;

import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.WordFactory;
import us.hebi.graalvm.bindings.runtime.exceptions.ExceptionHandlers;
import us.hebi.graalvm.bindings.runtime.types.CTypes;
import us.hebi.graalvm.bindings.tests.api.NativePalette;

public final class NativePalette$CEntries {
    @CEntryPoint(
            name = "__bindings_tests_Palette_next"
    )
    public static int bindings_tests_Palette_next(IsolateThread _isolate, int color) {
        try {
            return Color$$CEnum.fromJava(NativePalette.next(Color$$CEnum.toJava(color)));
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
            return CTypes.toTempCharPointer(NativePalette.nameOf(Color$$CEnum.toJava(color)));
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
            return NativePalette.ordinalOf(Color$$CEnum.toJava(color));
        } catch (Throwable throwable) {
            ExceptionHandlers.setLastError(throwable);
            return 0;
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Palette_fromOrdinal"
    )
    public static int bindings_tests_Palette_fromOrdinal(IsolateThread _isolate, int ordinal) {
        try {
            return Color$$CEnum.fromJava(NativePalette.fromOrdinal(ordinal));
        } catch (Throwable throwable) {
            ExceptionHandlers.setLastError(throwable);
            return 0;
        }
    }
}
