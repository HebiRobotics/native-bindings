package us.hebi.graalvm.bindings.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;

/**
 * Marks an integral parameter or return value as an index into a collection, so that
 * generators for one-based languages can convert it.
 * <p>
 * Java and the C ABI stay zero based. Only the wrappers for languages that count from
 * one convert, which today means MATLAB. C, C++ and Python emit nothing.
 * <p>
 * A {@code @CDefault} on an index is written zero based, like the Java signature it sits
 * in. Each one-based generator shifts it into its own space, so a MATLAB user reading the
 * generated wrapper sees the default expressed as a valid index in their own language and
 * an omitted argument converts exactly like a supplied one. The default has to be an
 * integer literal, since the shift is arithmetic.
 * <p>
 * A {@code matlabOverload} that supplies its own {@code parameters} or {@code call} takes
 * full control of the signature, so no conversion is generated for that method.
 *
 * @author Florian Enner
 */
@Retention(RetentionPolicy.CLASS)
@Target({TYPE_USE, TYPE_PARAMETER})
public @interface CLibIndex {
}
