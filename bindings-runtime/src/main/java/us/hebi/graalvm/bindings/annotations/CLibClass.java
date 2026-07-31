package us.hebi.graalvm.bindings.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;

/**
 * Annotates a class that should be exposed via a C API. The generated API
 * adds appropriate wrappers that deal with converting java handles and add
 * exception handling.
 *
 * @author Florian Enner
 * @since 05 Jan 2024
 */
@Retention(RetentionPolicy.CLASS)
@Target(TYPE)
public @interface CLibClass {

    /**
     * @return prefix for all class related methods
     */
    String cPrefix() default "";

    /**
     * @return name for API class wrapper
     */
    String value();

    String isolateName() default "";

    String destructorName() default "release";

    boolean abstractClass() default false;

    /**
     * @return true if the class can have instances (e.g. generates a destructor)
     */
    boolean enableInstancing() default true;

    boolean useCamelCase() default true;

    boolean ignore() default false;

}
