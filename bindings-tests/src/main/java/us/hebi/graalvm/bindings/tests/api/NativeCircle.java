package us.hebi.graalvm.bindings.tests.api;

import us.hebi.graalvm.bindings.annotations.CLibClass;
import us.hebi.graalvm.bindings.annotations.CLibComment;
import us.hebi.graalvm.bindings.annotations.CLibMethod;

import static us.hebi.graalvm.bindings.annotations.MethodFlag.*;

/**
 * @author Florian Enner
 */
@CLibComment("A circle. Inherits name and area from Shape.")
@CLibClass("Circle")
public class NativeCircle extends NativeShape {

    @CLibMethod(constructor = true)
    public static NativeCircle create(double radius) {
        return new NativeCircle(radius);
    }

    @CLibMethod(flags = {Noexcept, Const}, property = "radius")
    public double getRadius() {
        return radius;
    }

    @Override
    @CLibMethod(flags = {Noexcept, Const})
    public double area() {
        return Math.PI * radius * radius;
    }

    @Override
    @CLibMethod(flags = {Noexcept, Const})
    public int sideCount() {
        return 0;
    }

    private NativeCircle(double radius) {
        super("circle");
        this.radius = radius;
    }

    private final double radius;

}
