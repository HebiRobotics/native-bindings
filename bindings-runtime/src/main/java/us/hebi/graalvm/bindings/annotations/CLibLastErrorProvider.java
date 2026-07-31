package us.hebi.graalvm.bindings.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;

/**
 * A marker interface for a method that returns the last error
 * as a (temporary) utf8 char* string. Only accessible within
 * the class isolate.
 *
 * @author Florian Enner
 * @since 18 Jul 2024
 */
@Retention(RetentionPolicy.CLASS)
@Target({METHOD})
public @interface CLibLastErrorProvider {
}
