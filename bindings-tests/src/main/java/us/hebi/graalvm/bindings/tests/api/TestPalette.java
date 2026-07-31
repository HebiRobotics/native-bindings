package us.hebi.graalvm.bindings.tests.api;

import us.hebi.graalvm.bindings.annotations.CLibClass;
import us.hebi.graalvm.bindings.annotations.CLibComment;
import us.hebi.graalvm.bindings.annotations.CLibEnum;
import us.hebi.graalvm.bindings.annotations.CLibMethod;

import static us.hebi.graalvm.bindings.annotations.MethodFlag.*;

/**
 * Covers enum mapping. The C representation is a 4 byte int keyed on ordinal,
 * so adding a constant in the middle of {@link Color} is a breaking change and
 * the reference files will show it.
 *
 * @author Florian Enner
 */
@CLibComment("Enum round trips.")
@CLibClass(value = "Palette", enableInstancing = false)
public class TestPalette {

    @CLibEnum("Color")
    @CLibComment("A named color. Values are ordinals, starting at zero.")
    public enum Color {
        Red,
        Green,
        Blue
    }

    @CLibMethod(flags = Noexcept)
    @CLibComment("Cycles Red to Green to Blue and back to Red.")
    public static Color next(Color color) {
        return switch (color) {
            case Red -> Color.Green;
            case Green -> Color.Blue;
            case Blue -> Color.Red;
        };
    }

    @CLibMethod(flags = Noexcept)
    public static String nameOf(Color color) {
        return color.name();
    }

    @CLibMethod(flags = Noexcept)
    public static int ordinalOf(Color color) {
        return color.ordinal();
    }

    @CLibMethod(flags = Noexcept)
    @CLibComment("Returns the enum with the given ordinal, or Red if it is out of range.")
    public static Color fromOrdinal(int ordinal) {
        Color[] values = Color.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : Color.Red;
    }

}
