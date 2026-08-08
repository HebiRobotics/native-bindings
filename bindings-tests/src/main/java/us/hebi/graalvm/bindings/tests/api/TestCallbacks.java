package us.hebi.graalvm.bindings.tests.api;

import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.function.InvokeCFunctionPointer;
import org.graalvm.nativeimage.c.type.VoidPointer;
import org.graalvm.word.WordFactory;
import us.hebi.graalvm.bindings.annotations.CLibCallback;
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

    @CLibCallback(synchronous = true)
    public interface IndexCallbackFunction extends CFunctionPointer {
        @InvokeCFunctionPointer(transition = CFunction.Transition.TO_NATIVE)
        void invoke(VoidPointer userData, int index);
    }

    public interface NotifyCallbackFunction extends CFunctionPointer {
        @InvokeCFunctionPointer(transition = CFunction.Transition.TO_NATIVE)
        void invoke(VoidPointer userData);
    }

    @CLibCallback(synchronous = true)
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

    // NotifyCallbackFunction is deliberately left unannotated, so this method is what pins
    // the asynchronous-default wrapper shape. It still invokes immediately, because the
    // language suites assert the effect without sleeping.
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
         * Exists to pin the generation shape of a constructor taking an asynchronous
         * callback, which every face other than MATLAB generates like any other one.
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
         * Pins the shape of a constructor taking a synchronous callback, which is legal on
         * every face including MATLAB, where it is the only path that runs the callback
         * slot mechanics through a constructor.
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

}
