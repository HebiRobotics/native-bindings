package us.hebi.graalvm.bindings.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;

/**
 * Optional metadata for a {@code CFunctionPointer} interface used as a parameter type.
 * Discovery is signature-driven, so an unannotated interface is still a valid callback
 * with the asynchronous default.
 *
 * @author Florian Enner
 * @since 07 Aug 2026
 */
@Retention(RetentionPolicy.CLASS)
@Target(TYPE)
public @interface CLibCallback {

    /**
     * @return true if the callback fires on the calling thread and completes before the
     * library method returns. That contract is what opts the callback into MATLAB support
     * and lets the wrappers scope its lifetime to the call.
     */
    boolean synchronous() default false;

    /**
     * @return typedef name, defaults to the interface's simple name
     */
    String name() default "";

}
