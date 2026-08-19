package us.hebi.graalvm.bindings.tests.api;

import us.hebi.graalvm.bindings.annotations.CDefault;
import us.hebi.graalvm.bindings.annotations.CLibClass;
import us.hebi.graalvm.bindings.annotations.CLibComment;
import us.hebi.graalvm.bindings.annotations.CLibMethod;

import static us.hebi.graalvm.bindings.annotations.MethodFlag.*;

/**
 * Covers object handle lifecycle: constructor, instance methods, properties,
 * and the generated destructor.
 * <p>
 * {@link #close()} is deliberately not annotated. The generated destructor calls
 * {@code Handles.destroy}, which invokes {@link AutoCloseable#close()} when the
 * instance implements it. {@link #getClosedCount()} makes that observable from
 * every language without needing a debugger.
 *
 * @author Florian Enner
 */
@CLibComment("A mutable counter, used to check handle creation and release.")
@CLibClass("Counter")
public class TestCounter implements AutoCloseable {

    /**
     * Two constructors, distinguishable in every language: C gets one symbol each, C++
     * overloads on the type, and Python and MATLAB dispatch on it inside their single
     * constructor body. The default value covers the no-argument case.
     */
    @CLibMethod(constructor = true)
    @CLibComment("Creates a counter starting at the given value, or at zero by default.")
    public static TestCounter create(@CDefault("0") int start) {
        return new TestCounter(start);
    }

    @CLibMethod(constructor = true)
    @CLibComment("Creates a counter starting at the value written in text.")
    public static TestCounter parse(String text) {
        return new TestCounter(Integer.parseInt(text.trim()));
    }

    @CLibMethod(flags = Noexcept)
    public void increment() {
        value++;
    }

    @CLibMethod(flags = Noexcept)
    public void add(int amount) {
        value += amount;
    }

    @CLibMethod(flags = {Noexcept, Const}, property = "value")
    public int getValue() {
        return value;
    }

    @CLibMethod(flags = Noexcept, property = "value")
    public void setValue(int value) {
        this.value = value;
    }

    @CLibMethod(flags = {Noexcept, Const})
    @CLibComment("Returns true once close() has run. Only observable before the handle is released.")
    public boolean isClosed() {
        return closed;
    }

    /**
     * A handle returned by a class that also declares a constructor. The wrappers used to
     * replace their pointer constructor with the value one, so this did not wrap in any of them.
     */
    @CLibMethod(flags = {Noexcept, Const})
    @CLibComment("Creates an independent counter with the same value.")
    public TestCounter copy() {
        return new TestCounter(value);
    }

    @CLibMethod(flags = Noexcept)
    @CLibComment("""
            Total number of counters that have been released so far in this isolate.
            Increments exactly once per released handle, so tests can verify that the
            generated destructor ran.
            """)
    public static int getClosedCount() {
        return closedCount;
    }

    @CLibMethod(flags = Noexcept)
    @CLibComment("Resets the released counter, so tests do not depend on execution order.")
    public static void resetClosedCount() {
        closedCount = 0;
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            closedCount++;
        }
    }

    private TestCounter(int start) {
        this.value = start;
    }

    private int value;
    private boolean closed = false;

    // Single threaded by contract: the tests only ever touch this from the calling thread.
    private static int closedCount = 0;

}
