package us.hebi.graalvm.bindings.tests.api;

import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.function.InvokeCFunctionPointer;
import org.graalvm.nativeimage.c.type.CConst;
import org.graalvm.nativeimage.c.type.CDoublePointer;
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

    @CLibCallback("DoubleCallbackFunc")
    public interface DoubleCallbackFunction extends CFunctionPointer {
        @InvokeCFunctionPointer
        double invoke(double a, double b);
    }

    @CLibMethod(flags = Noexcept)
    public static double invokeDoubleCallback(DoubleCallbackFunction func, double a, double b) {
        return func.invoke(a, b);
    }

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

    // A raw pointer argument has no length travelling with it, so the MEX trampoline cannot
    // size the mxArray it would have to build. This is the shape that excludes the methods
    // taking it from the MATLAB face, and it is what a numerical solver's objective looks like.
    public interface TransformCallbackFunction extends CFunctionPointer {
        @InvokeCFunctionPointer(transition = CFunction.Transition.TO_NATIVE)
        void invoke(VoidPointer userData, @CConst CDoublePointer input, CDoublePointer output);
    }

    // The shape the MATLAB gateway supports, now that nothing may cross the ABI as context.
    // The registry thunk the library was handed is the channel, so its identity is what
    // replaces the void* every other callback here carries.
    public interface PlainIndexCallbackFunction extends CFunctionPointer {
        @InvokeCFunctionPointer(transition = CFunction.Transition.TO_NATIVE)
        void invoke(int index);
    }

    // No arguments at all. Together with a method that takes no void* either, this is the
    // pair the MATLAB face used to reject outright.
    public interface PlainNotifyCallbackFunction extends CFunctionPointer {
        @InvokeCFunctionPointer(transition = CFunction.Transition.TO_NATIVE)
        void invoke();
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

    // Pins a callback whose own arguments are raw pointers, the shape a solver objective has
    // when it reads a parameter vector and writes a residual vector.
    @CLibMethod(flags = Noexcept)
    @CLibComment("""
            Invokes the callback once with the two buffers and returns after it completes.
            Both buffers stay owned by the caller, and how many elements they hold is
            between the caller and the callback, since a raw pointer carries no length.
            The input is not verified! A null function pointer leaves output untouched.
            """)
    public static void transformOnce(TransformCallbackFunction func, VoidPointer userData,
                                     @CConst CDoublePointer input, CDoublePointer output) {
        if (func.isNull()) {
            return;
        }
        func.invoke(userData, input, output);
    }

    @CLibMethod(flags = Noexcept)
    @CLibComment("""
            Invokes the callback exactly 'times' times with indices 0 to times-1, then
            returns. A null function pointer is ignored, and a non-positive count does
            nothing.
            """)
    public static void invokePlainTimes(PlainIndexCallbackFunction func, @CSizeType int times) {
        if (func.isNull()) {
            return;
        }
        for (int i = 0; i < times; i++) {
            func.invoke(i);
        }
    }

    @CLibMethod
    @CLibComment(value = """
            Invokes the callback once with the given index, then throws if the index is
            negative. The invocation happens before the validation on purpose, so that a
            caller can observe both failures in one call.
            """,
            throwsOn = "index is negative")
    public static void invokePlainChecked(PlainIndexCallbackFunction func, int index) {
        if (!func.isNull()) {
            func.invoke(index);
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
    public static void invokePlainOnWorkerThread(PlainIndexCallbackFunction func, int index) {
        if (func.isNull()) {
            return;
        }
        // A lambda capture becomes a field of the lambda object, and a word type may not be
        // stored in one, so the pointer crosses to the worker as a raw address.
        final long function = func.rawValue();
        Thread worker = new Thread(() -> {
            PlainIndexCallbackFunction target = WordFactory.pointer(function);
            target.invoke(index);
        });
        worker.start();
        try {
            worker.join();
        } catch (InterruptedException e) {
            // Noexcept, so the flag is the only way to report it
            Thread.currentThread().interrupt();
        }
    }

    // Nothing crosses the ABI here but the function pointer itself, which is the narrowest
    // callback a library can declare and the one MATLAB needs no conversion at all for.
    @CLibMethod(flags = Noexcept)
    @CLibComment("Invokes the callback once. Returns after it completes.")
    public static void notifyPlainOnce(PlainNotifyCallbackFunction func) {
        if (func.isNull()) {
            return;
        }
        func.invoke();
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

    /**
     * The owned lifetime without a context pointer. The constructor both fires the callback
     * and keeps it, so one class covers construction-time invocation, invocation after the
     * call that registered it, and the release path a construction that failed has to take.
     * <p>
     * {@link #close()} follows {@link TestStored}, so the suites can see the release.
     */
    @CLibComment("A handle that invokes a callback during construction and stores it for later calls.")
    @CLibClass("PlainStored")
    public static class TestPlainStored implements AutoCloseable {

        @CLibMethod(constructor = true)
        @CLibComment("""
                Invokes the callback with indices 0 to times-1 before returning the handle, and
                keeps it for every later call to fire(), so it has to stay alive for as long as
                the handle does.
                """)
        public static TestPlainStored create(PlainIndexCallbackFunction func, @CSizeType int times) {
            return new TestPlainStored(func, times);
        }

        @CLibMethod(flags = Noexcept)
        @CLibComment("Invokes the stored callback once with the given index, on the calling thread.")
        public void fire(int index) {
            PlainIndexCallbackFunction func = WordFactory.pointer(function);
            if (func.isNull()) {
                return;
            }
            func.invoke(index);
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

        private TestPlainStored(PlainIndexCallbackFunction func, int times) {
            // A word type may not be stored in an object field, so the pointer is kept as a
            // raw address and rebuilt on every fire.
            this.function = func.rawValue();
            if (func.isNull()) {
                return;
            }
            for (int i = 0; i < times; i++) {
                func.invoke(i);
            }
        }

        private final long function;
        private boolean closed = false;

        // Single threaded by contract: the tests only ever touch this from the calling thread.
        private static int closedCount = 0;

    }

}
