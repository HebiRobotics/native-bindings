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
     * A class may declare at most one constructor. The MATLAB generator asserts this,
     * because overloading on the first argument of a classdef constructor is not
     * something it can express. The default value covers the no-argument case instead.
     */
    @CLibMethod(constructor = true)
    @CLibComment("Creates a counter starting at the given value, or at zero by default.")
    public static TestCounter create(@CDefault("0") int start) {
        return new TestCounter(start);
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
