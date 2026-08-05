package us.hebi.graalvm.bindings.runtime.types;

import us.hebi.graalvm.bindings.runtime.Configuration;
import us.hebi.graalvm.bindings.runtime.views.Utf8StringView;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Opt-in for exceptions that carry custom identifiers across the ABI, so callers can dispatch
 * on the kind of failure rather than on the message text. An interface rather than a base
 * class, because the exceptions that want an id already extend something.
 *
 * @author Florian Enner
 * @since 05 Aug 2026
 */
public interface CLibError {

    int STATUS_CODE_SUCCESS = 0;
    int STATUS_CODE_GENERIC_ERROR = 1;

    /**
     * @return anything other than zero (SUCCESS)
     */
    default int getStatusCode() {
        return STATUS_CODE_GENERIC_ERROR;
    }

    /**
     * Colon separated identifier, as in {@code mylib:invalidArgument}. MATLAB requires at
     * least one colon and silently treats anything else as a format string, so a single word
     * is worse than none.
     *
     * @return colon separated identifier
     */
    String getErrorId();

    default String getErrorMessage() {
        return this instanceof Throwable t ? t.getMessage() : "Annotated non-Exception class: " + getClass().getName();
    }

    /**
     * Clears a caller-provided error struct before the guarded invocation. Writing on the
     * success path is what keeps a struct that is reused across a loop from reporting the
     * first failure forever.
     *
     * @param error input struct owned by the caller
     */
    static void clearStruct(ErrorInfo error) {
        if (error.isNull()) return; // shouldn't happen, but not safe to throw here
        error.setCode(STATUS_CODE_SUCCESS);
        error.setMessage(CCharPointerMock.nullPointer());
        error.setId(CCharPointerMock.nullPointer());
    }

    /**
     * Fills a caller-provided error struct from a failure. The message and id go into the
     * calling thread's scratch buffer and are valid only until its next call.
     *
     * @param error    input struct owned by the caller
     * @param idPrefix library name used for exceptions that carry no id of their own
     */
    static void fillStruct(Throwable throwable, ErrorInfo error, String idPrefix) {
        if (error.isNull()) return; // shouldn't happen, but not safe to throw here
        if (throwable instanceof CLibError clib) {
            error.setCode(clib.getStatusCode());
            error.setMessage(tmpErrorMessage.get().setContent(withDebugInfo(clib.getErrorMessage(), throwable)).getPointer());
            error.setId(tmpErrorId.get().setContent(clib.getErrorId()).getPointer());
        } else {
            error.setCode(STATUS_CODE_GENERIC_ERROR);
            error.setMessage(tmpErrorMessage.get().setContent(withDebugInfo(throwable.getMessage(), throwable)).getPointer());
            error.setId(tmpErrorId.get().setContent(idPrefix + ":" + throwable.getClass().getSimpleName()).getPointer());
        }

    }

    /**
     * Appends the stack trace and cause chain to the message when debugging is enabled.
     * Walking the stack is expensive, so it only happens when someone is listening.
     */
    private static String withDebugInfo(String message, Throwable throwable) {
        if (!Configuration.isDebugEnabled()) {
            return message;
        }
        StringWriter writer = new StringWriter();
        PrintWriter printer = new PrintWriter(writer);
        if (message != null) {
            printer.println(message);
        }
        throwable.printStackTrace(printer);
        printer.flush();
        return writer.toString();
    }

    ThreadLocal<Utf8StringView> tmpErrorMessage = ThreadLocal.withInitial(Utf8StringView::new);
    ThreadLocal<Utf8StringView> tmpErrorId = ThreadLocal.withInitial(Utf8StringView::new);

}
