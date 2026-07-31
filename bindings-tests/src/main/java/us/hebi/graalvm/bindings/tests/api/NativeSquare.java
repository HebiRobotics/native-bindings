package us.hebi.graalvm.bindings.tests.api;

import us.hebi.graalvm.bindings.annotations.CLibClass;
import us.hebi.graalvm.bindings.annotations.CLibComment;
import us.hebi.graalvm.bindings.annotations.CLibMethod;

import static us.hebi.graalvm.bindings.annotations.MethodFlag.*;

/**
 * Second subclass, so the generated upcast is exercised by more than one type.
 *
 * @author Florian Enner
 */
@CLibComment("A square. Inherits name and area from Shape.")
@CLibClass("Square")
public class NativeSquare extends NativeShape {

    @CLibMethod(constructor = true)
    public static NativeSquare create(double side) {
        return new NativeSquare(side);
    }

    @CLibMethod(flags = {Noexcept, Const}, property = "side")
    public double getSide() {
        return side;
    }

    @Override
    @CLibMethod(flags = {Noexcept, Const})
    public double area() {
        return side * side;
    }

    @Override
    @CLibMethod(flags = {Noexcept, Const})
    public int sideCount() {
        return 4;
    }

    private NativeSquare(double side) {
        super("square");
        this.side = side;
    }

    private final double side;

}
