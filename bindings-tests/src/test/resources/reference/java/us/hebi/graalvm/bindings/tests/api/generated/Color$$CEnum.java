package us.hebi.graalvm.bindings.tests.api.generated;

import us.hebi.graalvm.bindings.tests.api.NativePalette;

public class Color$$CEnum {
    public static int fromJava(NativePalette.Color value) {
        return switch (value) {
            case Red -> 0;
            case Green -> 1;
            case Blue -> 2;
        } ;
    }

    public static NativePalette.Color toJava(int value) {
        return switch (value) {
            case 0 -> NativePalette.Color.Red;
            case 1 -> NativePalette.Color.Green;
            case 2 -> NativePalette.Color.Blue;
            default -> throw new IllegalArgumentException("Unknown enum value: " + value);
        } ;
    }
}
