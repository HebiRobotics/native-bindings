package us.hebi.graalvm.bindings.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;

/**
 * @author Florian Enner
 * @since 05 Aug 2025
 */
@Retention(RetentionPolicy.CLASS)
@Target({PARAMETER})
public @interface CDefault {
    String value();
}
