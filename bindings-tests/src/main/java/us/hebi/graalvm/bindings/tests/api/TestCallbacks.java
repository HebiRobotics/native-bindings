package us.hebi.graalvm.bindings.tests.api;

import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.function.InvokeCFunctionPointer;
import org.graalvm.nativeimage.c.type.VoidPointer;
import org.graalvm.word.WordFactory;
import us.hebi.graalvm.bindings.annotations.CLibClass;
import us.hebi.graalvm.bindings.annotations.CLibComment;
import us.hebi.graalvm.bindings.annotations.CLibMethod;
import us.hebi.graalvm.bindings.annotations.CSizeType;

import static us.hebi.graalvm.bindings.annotations.MethodFlag.*;

/**
 * Covers function pointer parameters.
 * <p>
 * Every callback here completes before the method returns, so the number of invocations
 * is fully determined by the arguments. That keeps the language tests free of sleeps and
 * polling. {@link #invokeOnWorkerThread} is the one that does not run on the calling
 * thread, which is what the MATLAB face has to detect.
 *
 * @author Florian Enner
 */
@CLibComment("Synchronous callbacks into caller-provided function pointers.")
@CLibClass(value = "Callbacks", enableInstancing = false)
public class TestCallbacks {

    public interface IndexCallbackFunction extends CFunctionPointer {
        @InvokeCFunctionPointer(transition = CFunction.Transition.TO_NATIVE)
        void invoke(VoidPointer userData, int index);
    }

    // The plain unannotated shape, taken by a method and by a constructor, so it is the one
    // that pins a callback needing both a stack slot and a per-object record in MATLAB.
    public interface NotifyCallbackFunction extends CFunctionPointer {
        @InvokeCFunctionPointer(transition = CFunction.Transition.TO_NATIVE)
        void invoke(VoidPointer userData);
    }

    // Same shape as IndexCallbackFunction, kept separate so that a callback only a
    // constructor takes has its own typedef. TestStored is the only user.
    public interface StoredCallbackFunction extends CFunctionPointer {
        @InvokeCFunctionPointer(transition = CFunction.Transition.TO_NATIVE)
        void invoke(VoidPointer userData, int index);
    }

    public interface ScaleCallbackFunction extends CFunctionPointer {
        @InvokeCFunctionPointer(transition = CFunction.Transition.TO_NATIVE)
        double invoke(VoidPointer userData, double value);
    }

    @CLibMethod(flags = Noexcept)
    @CLibComment("""
            Invokes the callback exactly 'times' times with indices 0 to times-1, then
            returns. A null function pointer is ignored, and a non-positive count does
            nothing.
            """)
    public static void invokeTimes(IndexCallbackFunction func, VoidPointer userData, @CSizeType int times) {
        if (func.isNull()) {
            return;
        }
        for (int i = 0; i < times; i++) {
            func.invoke(userData, i);
        }
    }

    @CLibMethod(flags = Noexcept)
    @CLibComment("Invokes the callback once with the given index. Returns after it completes.")
    public static void invokeOnce(IndexCallbackFunction func, VoidPointer userData, int index) {
        if (func.isNull()) {
            return;
        }
        func.invoke(userData, index);
    }

    @CLibMethod
    @CLibComment(value = """
            Invokes the callback once with the given index, then throws if the index is
            negative. The invocation happens before the validation on purpose, so that a
            caller can observe both failures in one call.
            """,
            throwsOn = "index is negative")
    public static void invokeChecked(IndexCallbackFunction func, VoidPointer userData, int index) {
        if (!func.isNull()) {
            func.invoke(userData, index);
        }
        if (index < 0) {
            throw new IllegalArgumentException("index must not be negative, but was " + index);
        }
    }

    @CLibMethod(flags = Noexcept)
    @CLibComment("""
            Invokes the callback once with the given index on a worker thread, and joins that
            thread before returning. The invocation therefore completes within the call but not
            on the calling thread, which is the shape a solver has when it fans an objective
            out to workers. The callback must be thread safe.
            """)
    public static void invokeOnWorkerThread(IndexCallbackFunction func, VoidPointer userData, int index) {
        if (func.isNull()) {
            return;
        }
        // A lambda capture becomes a field of the lambda object, and a word type may not be
        // stored in one, so the pointers cross to the worker as raw addresses.
        final long function = func.rawValue();
        final long context = userData.rawValue();
        Thread worker = new Thread(() -> {
            IndexCallbackFunction target = WordFactory.pointer(function);
            target.invoke(WordFactory.pointer(context), index);
        });
        worker.start();
        try {
            worker.join();
        } catch (InterruptedException e) {
            // Noexcept, so the flag is the only way to report it
            Thread.currentThread().interrupt();
        }
    }

    // Pins the shape of a callback that carries nothing but its userData, which is the one
    // whose MATLAB function handle takes no arguments at all.
    @CLibMethod(flags = Noexcept)
    @CLibComment("Invokes the callback once. Returns after it completes.")
    public static void notifyOnce(NotifyCallbackFunction func, VoidPointer userData) {
        if (func.isNull()) {
            return;
        }
        func.invoke(userData);
    }

    // Pins the shape a solver objective needs, where the library calls back into user code
    // and consumes the value it returns.
    @CLibMethod(flags = Noexcept)
    @CLibComment("""
            Passes the value to the callback and returns what the callback produced. A null
            function pointer leaves the value unchanged.
            """)
    public static double applyScaled(ScaleCallbackFunction func, VoidPointer userData, double value) {
        if (func.isNull()) {
            return value;
        }
        return func.invoke(userData, value);
    }

    @CLibComment("A handle whose construction invokes a callback.")
    @CLibClass("Notified")
    public static class TestNotified {

        /**
         * Pins a constructor whose callback interface a method also takes, so the two
         * lifetimes coexist and MATLAB needs both trampoline forms for one typedef.
         */
        @CLibMethod(constructor = true)
        @CLibComment("Creates the handle and invokes the callback once before returning it.")
        public static TestNotified create(NotifyCallbackFunction func, VoidPointer userData) {
            return new TestNotified(func, userData);
        }

        private TestNotified(NotifyCallbackFunction func, VoidPointer userData) {
            if (!func.isNull()) {
                func.invoke(userData);
            }
        }

    }

    /**
     * {@link #close()} follows {@link TestCounter}: it is not annotated, and
     * {@code Handles.destroy} calls it. The count is what lets the MATLAB suite see that a
     * constructor whose callback failed still released the handle it had already created.
     */
    @CLibComment("A handle whose construction invokes a callback once per index.")
    @CLibClass("Counted")
    public static class TestCounted implements AutoCloseable {

        /**
         * Pins a constructor whose callback fires during construction and never again, so
         * the per-object record it needs is released without the handle ever being reused.
         */
        @CLibMethod(constructor = true)
        @CLibComment("Creates the handle, invoking the callback with indices 0 to times-1 before returning it.")
        public static TestCounted create(IndexCallbackFunction func, VoidPointer userData, int times) {
            return new TestCounted(func, userData, times);
        }

        @CLibMethod(flags = Noexcept)
        @CLibComment("""
                Total number of handles of this type that have been released so far in this
                isolate. Increments exactly once per released handle.
                """)
        public static int getClosedCount() {
            return closedCount;
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                closedCount++;
            }
        }

        private TestCounted(IndexCallbackFunction func, VoidPointer userData, int times) {
            if (func.isNull()) {
                return;
            }
            for (int i = 0; i < times; i++) {
                func.invoke(userData, i);
            }
        }

        private boolean closed = false;

        // Single threaded by contract: the tests only ever touch this from the calling thread.
        private static int closedCount = 0;

    }

    /**
     * Pins the registered shape: the constructor keeps the pair and every later {@link #fire}
     * runs it, on the calling thread. That is what the wrappers need owning constructor sugar
     * for, since a call-scoped closure would be gone by the time the first fire arrives.
     * <p>
     * {@link #close()} follows {@link TestCounted}, so the suites can see the release.
     */
    @CLibComment("A handle that stores a callback and invokes it during later calls.")
    @CLibClass("Stored")
    public static class TestStored implements AutoCloseable {

        @CLibMethod(constructor = true)
        @CLibComment("""
                Stores the callback and returns without invoking it. Every later call to
                fire() runs it on the calling thread, so it has to stay alive for as long
                as the handle does.
                """)
        public static TestStored create(StoredCallbackFunction func, VoidPointer userData) {
            return new TestStored(func, userData);
        }

        @CLibMethod(flags = Noexcept)
        @CLibComment("Invokes the stored callback once with the given index, on the calling thread.")
        public void fire(int index) {
            StoredCallbackFunction func = WordFactory.pointer(function);
            if (func.isNull()) {
                return;
            }
            func.invoke(WordFactory.pointer(context), index);
        }

        @CLibMethod(flags = Noexcept)
        @CLibComment("""
                Total number of handles of this type that have been released so far in this
                isolate. Increments exactly once per released handle.
                """)
        public static int getClosedCount() {
            return closedCount;
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                closedCount++;
            }
        }

        private TestStored(StoredCallbackFunction func, VoidPointer userData) {
            // A word type may not be stored in an object field, so the pair is kept as raw
            // addresses and rebuilt on every fire.
            this.function = func.rawValue();
            this.context = userData.rawValue();
        }

        private final long function;
        private final long context;
        private boolean closed = false;

        // Single threaded by contract: the tests only ever touch this from the calling thread.
        private static int closedCount = 0;

    }

}
