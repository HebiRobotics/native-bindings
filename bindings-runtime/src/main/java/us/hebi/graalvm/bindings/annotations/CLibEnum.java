package us.hebi.graalvm.bindings.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;

/**
 * @author Florian Enner
 * @since 30 Jan 2025
 */
@Retention(RetentionPolicy.CLASS)
@Target(TYPE)
public @interface CLibEnum {

    /**
     * @return enum class name
     */
    String value();

    /**
     * @return prefix for non-namespaced C names
     */
    String cPrefix() default "";

}
