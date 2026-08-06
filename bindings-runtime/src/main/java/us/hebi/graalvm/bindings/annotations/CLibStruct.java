package us.hebi.graalvm.bindings.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;

/**
 * Marks a {@code @CStruct} view interface as a library struct that crosses the
 * C ABI by value. Version 1 supports primitive fields and typed GraalVM pointers.
 *
 * @author Florian Enner
 * @since 6 Aug 2026
 */
@Retention(RetentionPolicy.CLASS)
@Target(TYPE)
public @interface CLibStruct {

    /**
     * @return struct name, defaults to the {@code @CStruct} value
     */
    String value() default "";

    /**
     * @return prefix for non-namespaced C names
     */
    String prefix() default "";

}
