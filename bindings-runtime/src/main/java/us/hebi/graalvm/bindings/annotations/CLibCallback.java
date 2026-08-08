package us.hebi.graalvm.bindings.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;

/**
 * Optional metadata for a {@code CFunctionPointer} interface used as a parameter type.
 * Discovery is signature-driven, so an unannotated interface is a valid callback and this
 * annotation only overrides the derived typedef name and its C prefix.
 *
 * @author Florian Enner
 * @since 07 Aug 2026
 */
@Retention(RetentionPolicy.CLASS)
@Target(TYPE)
public @interface CLibCallback {

    /**
     * @return typedef name, defaults to the interface's simple name
     */
    String name() default "";

    /**
     * @return prefix for non-namespaced C names
     */
    String cPrefix() default "";

}
