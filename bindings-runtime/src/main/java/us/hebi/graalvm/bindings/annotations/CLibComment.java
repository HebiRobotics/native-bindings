package us.hebi.graalvm.bindings.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author Florian Enner
 * @since 06 Aug 2025
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER, ElementType.METHOD, ElementType.TYPE})
public @interface CLibComment {

    String value();

    String throwsOn() default "";

}
