package us.hebi.graalvm.bindings.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;

/**
 * Marks a {@code @CStruct} view interface as a library struct that crosses the
 * C ABI by value. Version 1 supports primitive fields and typed GraalVM pointers.
 * The struct definition gets generated automatically, so the layout needs to be
 * fully defined. All fields need to be defined in the correct order, and changes
 * to the field layout may break ABI backwards compatibility.
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
     * @return marshaller that customizes the language faces of this view, an
     * implementation of the generator's {@code StructMarshaller} with a public
     * no-argument constructor. The class has to live in an already compiled
     * source set (i.e. a separate module), because the processor instantiates
     * it while this compilation is still running. {@code void.class} keeps the
     * default native-record faces.
     */
    Class<?> marshaller() default void.class;

    /**
     * @return prefix for non-namespaced C names
     */
    String prefix() default "";

}
