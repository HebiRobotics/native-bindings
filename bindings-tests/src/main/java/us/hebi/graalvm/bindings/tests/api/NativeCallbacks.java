package us.hebi.graalvm.bindings.tests.api;

import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.function.InvokeCFunctionPointer;
import org.graalvm.nativeimage.c.type.VoidPointer;
import us.hebi.graalvm.bindings.annotations.CLibClass;
import us.hebi.graalvm.bindings.annotations.CLibComment;
import us.hebi.graalvm.bindings.annotations.CLibMethod;
import us.hebi.graalvm.bindings.annotations.CLibTypeDef;
import us.hebi.graalvm.bindings.annotations.CSizeType;

import static us.hebi.graalvm.bindings.annotations.MethodFlag.*;

/**
 * Covers function pointer parameters.
 * <p>
 * Every callback here fires synchronously on the calling thread before the method
 * returns, so the number of invocations is fully determined by the arguments. That
 * keeps the language tests free of sleeps and polling.
 * <p>
 * MATLAB cannot take a C function pointer, so the typedef degrades to void* there
 * and these methods are not callable from MATLAB.
 *
 * @author Florian Enner
 */
@CLibComment("Synchronous callbacks into caller-provided function pointers.")
@CLibClass(value = "Callbacks", enableInstancing = false)
public class NativeCallbacks {

    @CLibTypeDef(name = "IndexCallbackFunction",
            cDefinition = """
                    #ifndef MATLAB
                    typedef void (*IndexCallbackFunction)(void* userData, int index);
                    #else
                    typedef void* IndexCallbackFunction; // Callbacks are not supported
                    #endif
                    """,
            cppDefinition = "using IndexCallbackFunction = void (*)(void* userData, int index);",
            pythonDefinition = "IndexCallbackFunction = CFUNCTYPE(None, c_void_p, c_int)"
    )
    public interface IndexCallbackFunction extends CFunctionPointer {
        @InvokeCFunctionPointer(transition = CFunction.Transition.TO_NATIVE)
        void invoke(VoidPointer userData, int index);
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

}
