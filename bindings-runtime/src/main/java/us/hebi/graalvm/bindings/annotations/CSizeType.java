package us.hebi.graalvm.bindings.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;

/**
 * A marker interface for types that should be size_t in the API,
 * but don't have a matching type in GraalVM. This maps to int or
 * long in Java, and uses size_t in the corresponding C wrapper methods.
 *
 * @author Florian Enner
 * @since 18 Jul 2024
 */
@Retention(RetentionPolicy.RUNTIME) // needed for pre-compiled views in jdk21; jdk25 works without
@Target({TYPE_USE, TYPE_PARAMETER})
public @interface CSizeType {
}
