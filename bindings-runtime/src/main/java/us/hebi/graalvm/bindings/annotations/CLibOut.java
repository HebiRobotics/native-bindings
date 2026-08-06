package us.hebi.graalvm.bindings.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;

/**
 * Marks a struct view parameter as the method's output. The method fills the
 * caller-provided struct through the view's setters, so the view must declare a
 * setter for every field. The public C signature returns the struct by value.
 *
 * @author Florian Enner
 * @since 6 Aug 2026
 */
@Retention(RetentionPolicy.CLASS)
@Target(PARAMETER)
public @interface CLibOut {
}
