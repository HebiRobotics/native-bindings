package us.hebi.graalvm.bindings.tests.api;

import us.hebi.graalvm.bindings.annotations.CLibClass;
import us.hebi.graalvm.bindings.annotations.CLibComment;
import us.hebi.graalvm.bindings.annotations.CLibEnum;
import us.hebi.graalvm.bindings.annotations.CLibMethod;

import static us.hebi.graalvm.bindings.annotations.MethodFlag.*;

/**
 * Abstract base of the inheritance test. Subclasses share this class's handle
 * registry, so a Circle handle can be passed to any method declared here after
 * an upcast.
 * <p>
 * {@link TestCircle} is declared here rather than in its own file to cover annotated
 * static nested classes. Nothing about the C ABI changes: every symbol comes from the
 * {@code @CLibClass} value, so Circle's generated C, C++, Python and MATLAB output is
 * identical to what a top-level declaration produces. Only the generated Java wrapper
 * differs, folding the enclosing type into its name.
 *
 * @author Florian Enner
 */
@CLibComment("Base class for the shape hierarchy.")
@CLibClass(value = "Shape", abstractClass = true)
public abstract class TestShape {

    @CLibComment("A circle. Inherits name and area from Shape.")
    @CLibClass("Circle")
    public static class TestCircle extends TestShape {

        /**
         * Deliberately named {@code Color}, the same as {@link TestPalette.Color}. The
         * generated enum wrapper is a top-level class, so both would land in the same file
         * if it were keyed on the innermost name.
         */
        @CLibEnum("CircleColor")
        @CLibComment("Fill color of a circle.")
        public enum Color {
            Red,
            Green,
            Blue
        }

        @CLibMethod(constructor = true)
        public static TestCircle create(double radius) {
            return new TestCircle(radius);
        }

        @CLibMethod(flags = {Noexcept, Const})
        @CLibComment("Returns the fill color, which is always Blue until setters exist.")
        public Color getColor() {
            return Color.Blue;
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

        private TestCircle(double radius) {
            super("circle");
            this.radius = radius;
        }

        private final double radius;

    }

    @CLibMethod(flags = {Noexcept, Const}, property = "name")
    public String getName() {
        return name;
    }

    @CLibMethod(flags = Noexcept, property = "name")
    public void setName(String name) {
        this.name = name;
    }

    @CLibMethod(flags = {Noexcept, Const})
    @CLibComment("Area of the shape. Dispatches to the concrete subclass.")
    public abstract double area();

    @CLibMethod(flags = {Noexcept, Const})
    @CLibComment("Number of sides. Zero for shapes without straight edges.")
    public abstract int sideCount();

    protected TestShape(String name) {
        this.name = name;
    }

    private String name;

}
