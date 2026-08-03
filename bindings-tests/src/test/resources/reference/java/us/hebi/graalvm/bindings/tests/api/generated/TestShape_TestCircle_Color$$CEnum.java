package us.hebi.graalvm.bindings.tests.api.generated;

import us.hebi.graalvm.bindings.tests.api.TestShape;

public class TestShape_TestCircle_Color$$CEnum {
    public static int fromJava(TestShape.TestCircle.Color value) {
        return switch (value) {
            case Red -> 0;
            case Green -> 1;
            case Blue -> 2;
        } ;
    }

    public static TestShape.TestCircle.Color toJava(int value) {
        return switch (value) {
            case 0 -> TestShape.TestCircle.Color.Red;
            case 1 -> TestShape.TestCircle.Color.Green;
            case 2 -> TestShape.TestCircle.Color.Blue;
            default -> throw new IllegalArgumentException("Unknown enum value: " + value);
        } ;
    }
}
