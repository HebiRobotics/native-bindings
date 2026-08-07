package us.hebi.graalvm.bindings.runtime.structs;

import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.word.WordFactory;
import us.hebi.graalvm.bindings.runtime.Configuration;
import us.hebi.graalvm.bindings.runtime.views.Utf8StringView;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * @author Florian Enner
 * @since 07 Aug 2026
 */
public class CLibErrors {

    /**
     * Clears a caller-provided error struct before the guarded invocation. Writing on the
     * success path is what keeps a struct that is reused across a loop from reporting the
     * first failure forever.
     *
     * @param error input struct owned by the caller
     */
    public static void clear(ErrorInfo error) {
        if (error.isNull()) return; // shouldn't happen, but not safe to throw here
        if (ImageInfo.inImageRuntimeCode()) {
            error.setMessage(WordFactory.nullPointer());
            error.setId(WordFactory.nullPointer());
        }
        error.setCode(CLibError.STATUS_CODE_SUCCESS);
    }

    /**
     * Fills a caller-provided error struct from a failure. The message and id go into the
     * calling thread's scratch buffer and are valid only until its next call.
     *
     * @param error    input struct owned by the caller
     * @param idPrefix library name used for exceptions that carry no id of their own
     */
    public static void fill(Throwable throwable, ErrorInfo error, String idPrefix) {
        if (error.isNull()) return; // shouldn't happen, but not safe to throw here
        if (throwable instanceof CLibError clib) {
            error.setCode(clib.getStatusCode());
            error.setMessage(tmpErrorMessage.get().setContent(withDebugInfo(clib.getErrorMessage(), throwable)).getPointer());
            error.setId(tmpErrorId.get().setContent(clib.getErrorId()).getPointer());
        } else {
            error.setCode(CLibError.STATUS_CODE_GENERIC_ERROR);
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

    private static final ThreadLocal<Utf8StringView> tmpErrorMessage = ThreadLocal.withInitial(Utf8StringView::new);
    private static final ThreadLocal<Utf8StringView> tmpErrorId = ThreadLocal.withInitial(Utf8StringView::new);

}
