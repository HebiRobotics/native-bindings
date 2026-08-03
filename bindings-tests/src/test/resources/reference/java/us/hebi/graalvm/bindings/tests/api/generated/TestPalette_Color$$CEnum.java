package us.hebi.graalvm.bindings.tests.api.generated;

import us.hebi.graalvm.bindings.tests.api.TestPalette;

public class TestPalette_Color$$CEnum {
    public static int fromJava(TestPalette.Color value) {
        return switch (value) {
            case Red -> 0;
            case Green -> 1;
            case Blue -> 2;
        } ;
    }

    public static TestPalette.Color toJava(int value) {
        return switch (value) {
            case 0 -> TestPalette.Color.Red;
            case 1 -> TestPalette.Color.Green;
            case 2 -> TestPalette.Color.Blue;
            default -> throw new IllegalArgumentException("Unknown enum value: " + value);
        } ;
    }
}
