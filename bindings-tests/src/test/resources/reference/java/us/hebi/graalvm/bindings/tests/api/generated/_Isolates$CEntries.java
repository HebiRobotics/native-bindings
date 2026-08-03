package us.hebi.graalvm.bindings.tests.api.generated;

import org.graalvm.nativeimage.Isolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.WordFactory;
import us.hebi.graalvm.bindings.runtime.exceptions.ExceptionHandlers;
import us.hebi.graalvm.bindings.runtime.types.CTypes;

public final class _Isolates$CEntries {
    @CEntryPoint(
            name = "bindings_tests_createIsolate",
            builtin = CEntryPoint.Builtin.CREATE_ISOLATE
    )
    public static native IsolateThread createIsolate();

    @CEntryPoint(
            name = "bindings_tests_getIsolate",
            builtin = CEntryPoint.Builtin.GET_ISOLATE
    )
    public static native Isolate getIsolate(IsolateThread thread);

    @CEntryPoint(
            name = "bindings_tests_attachThread",
            builtin = CEntryPoint.Builtin.ATTACH_THREAD
    )
    public static native IsolateThread attachThread(Isolate isolate);

    @CEntryPoint(
            name = "bindings_tests_getCurrentThread",
            builtin = CEntryPoint.Builtin.GET_CURRENT_THREAD
    )
    public static native IsolateThread getCurrentThread(Isolate isolate);

    @CEntryPoint(
            name = "bindings_tests_detachThread",
            builtin = CEntryPoint.Builtin.DETACH_THREAD
    )
    public static native int detachThread(IsolateThread thread);

    @CEntryPoint(
            name = "bindings_tests_tearDownIsolate",
            builtin = CEntryPoint.Builtin.TEAR_DOWN_ISOLATE
    )
    public static native int tearDownIsolate(IsolateThread thread);

    @CEntryPoint(
            name = "__bindings_tests_getLastError"
    )
    public static CCharPointer bindings_tests_getLastError(IsolateThread _isolate) {
        try {
            return CTypes.toTempCharPointer(ExceptionHandlers.getLastError(false));
        } catch (Throwable throwable) {
            ExceptionHandlers.setLastError(throwable);
            return WordFactory.nullPointer();
        }
    }
}
