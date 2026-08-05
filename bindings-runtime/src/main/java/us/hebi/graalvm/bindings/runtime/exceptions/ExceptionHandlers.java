package us.hebi.graalvm.bindings.runtime.exceptions;

import us.hebi.graalvm.bindings.runtime.Configuration;
import us.hebi.graalvm.bindings.runtime.types.CLibError;
import us.hebi.graalvm.bindings.runtime.types.CTypes;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Every generated entry point wraps its invocation in a try/catch that routes
 * failures through here, so that exceptions never cross the C ABI boundary.
 * <p>
 * The last error is stored per thread, which matches the C convention of
 * {@code errno} and lets a caller retrieve the message on the same thread
 * that made the failing call.
 * <p>
 * Note: custom exceptionHandler classes on the @CEntryPoint annotation do not work
 * because of a missing @Uninterruptible annotation that doesn't seem to be part of
 * the public API in newer versions.
 *
 * @author Florian Enner
 * @since 06 Jan 2024
 */
@Deprecated // TODO: no longer necessary after migration? keep for debugging?
public interface ExceptionHandlers {

    int STATUS_CODE_SUCCESS = 0;
    int STATUS_CODE_GENERIC_ERROR = 1;

    @Deprecated // TODO: should be deleted after error merge
    static int returnErrorStatusCode(Throwable throwable) {
        // TODO: add status codes that provide info about error messages?
        return CLibError.STATUS_CODE_GENERIC_ERROR;
    }

    @Deprecated // TODO: no longer necessary after migration? keep for debugging?
    static void setLastError(Throwable throwable) {
        // Walking the stack is expensive, so only do it when someone is listening
        if (Configuration.isDebugEnabled()) {
            Configuration.printDebug(getStackTrace(throwable));
        }
        lastException.set(throwable);
    }

    @Deprecated // TODO: no longer necessary after migration? keep for debugging?
    static void printLastStackTrace() {
        Throwable exception = lastException.get();
        if (exception == null) {
            System.err.println("So far no exceptions have occured on this thread");
        } else {
            exception.printStackTrace();
        }
    }

    @Deprecated // TODO: no longer necessary after migration? keep for debugging?
    static String getLastError(boolean withStackTrace) {
        Throwable ex = lastException.get();
        if (ex == null) return "";
        return withStackTrace ? "\n" + getStackTrace(ex) : ex.getMessage();
    }

    static String getStackTrace(Throwable throwable) {
        if (throwable == null) return "";
        StringWriter writer = new StringWriter();
        PrintWriter printer = new PrintWriter(writer);
        throwable.printStackTrace(printer);
        printer.flush();
        return writer.toString();
    }

    ThreadLocal<Throwable> lastException = new ThreadLocal<Throwable>();

}
