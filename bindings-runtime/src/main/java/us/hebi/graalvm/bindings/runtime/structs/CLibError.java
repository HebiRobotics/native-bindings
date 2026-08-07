package us.hebi.graalvm.bindings.runtime.structs;

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

}
