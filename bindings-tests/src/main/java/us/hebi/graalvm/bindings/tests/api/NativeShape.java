package us.hebi.graalvm.bindings.tests.api;

import us.hebi.graalvm.bindings.annotations.CLibClass;
import us.hebi.graalvm.bindings.annotations.CLibComment;
import us.hebi.graalvm.bindings.annotations.CLibMethod;

import static us.hebi.graalvm.bindings.annotations.MethodFlag.*;

/**
 * Abstract base of the inheritance test. Subclasses share this class's handle
 * registry, so a Circle handle can be passed to any method declared here after
 * an upcast.
 *
 * @author Florian Enner
 */
@CLibComment("Base class for the shape hierarchy.")
@CLibClass(value = "Shape", abstractClass = true)
public abstract class NativeShape {

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

    protected NativeShape(String name) {
        this.name = name;
    }

    private String name;

}
