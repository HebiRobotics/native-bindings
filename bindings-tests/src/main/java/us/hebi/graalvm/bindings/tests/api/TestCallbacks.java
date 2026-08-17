package us.hebi.graalvm.bindings.tests.api;

import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.function.InvokeCFunctionPointer;
import org.graalvm.nativeimage.c.type.CConst;
import org.graalvm.nativeimage.c.type.CDoublePointer;
import org.graalvm.nativeimage.c.type.VoidPointer;
import org.graalvm.word.WordFactory;
import us.hebi.graalvm.bindings.annotations.*;
import us.hebi.graalvm.bindings.runtime.structs.DoubleSpan;

import static us.hebi.graalvm.bindings.annotations.MethodFlag.*;

/**
 * Covers function pointer parameters.
 * <p>
 * Every callback here completes before the method returns, so the number of invocations
 * is fully determined by the arguments. That keeps the language tests free of sleeps and
 * polling. {@link #invokePlainOnWorkerThread} is the one that does not run on the calling
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

    // A span argument carries its length, so unlike TransformCallbackFunction the MEX trampoline
    // can size the mxArray. Every face gets the span by pointer, which is how a struct view crosses.
    @CLibCallback("DoubleSpanToScalarCallbackFunc")
    public interface DoubleSpanToScalarCallbackFunction extends CFunctionPointer {
        @InvokeCFunctionPointer
        double invoke(DoubleSpan a, DoubleSpan b);
    }

    @CLibMethod(flags = Noexcept)
    public static double invokeDoubleSpanToScalarCallback(DoubleSpanToScalarCallbackFunction func, DoubleSpan a, DoubleSpan b) {
        return func.invoke(a, b);
    }

    // The only callback here that still carries a void*. Nothing may read it, so C, C++
    // and Python pass the address through untouched and MATLAB stubs every method taking one.
    public interface IndexCallbackFunction extends CFunctionPointer {
        @InvokeCFunctionPointer(transition = CFunction.Transition.TO_NATIVE)
        void invoke(VoidPointer userData, int index);
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
    // replaces a void* argument.
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

    // The single void* method, pinning both behaviours: C, C++ and Python see the address
    // arrive unread and unchanged, MATLAB stubs the method with the 'userData' reason.
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

}
