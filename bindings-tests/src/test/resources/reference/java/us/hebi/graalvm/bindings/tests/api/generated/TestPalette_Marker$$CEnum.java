package us.hebi.graalvm.bindings.tests.api.generated;

import us.hebi.graalvm.bindings.tests.api.TestPalette;

public class TestPalette_Marker$$CEnum {
    public static int fromJava(TestPalette.Marker value) {
        return switch (value) {
            case Dot -> 0;
            case Cross -> 1;
        } ;
    }

    public static TestPalette.Marker toJava(int value) {
        return switch (value) {
            case 0 -> TestPalette.Marker.Dot;
            case 1 -> TestPalette.Marker.Cross;
            default -> throw new IllegalArgumentException("Unknown enum value: " + value);
        } ;
    }
}
