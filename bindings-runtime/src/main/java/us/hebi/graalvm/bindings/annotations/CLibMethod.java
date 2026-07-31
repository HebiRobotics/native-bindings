package us.hebi.graalvm.bindings.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;

/**
 * Annotates a method that should be exposed via the C API. Only used
 * inside a @CLibClass annotated class and only used to overwrite the
 * default (derived) method name.
 *
 * @author Florian Enner
 * @since 15 Aug 2024
 */
@Retention(RetentionPolicy.CLASS)
@Target(METHOD)
public @interface CLibMethod {

    String name() default "";

    boolean constructor() default false;

    boolean ignore() default false;

    String property() default "";

    MethodFlag[] flags() default {};

    String cppBody() default "";

    MethodOverload[] cppOverloads() default {};

    MethodOverload pythonOverload() default @MethodOverload();

    MethodOverload matlabOverload() default @MethodOverload();

    public @interface MethodOverload {

        /**
         * @return the function prototype for the new method overload. Object references are added automatically.
         */
        String parameters() default "";

        /**
         * @return code that shall be placed at the beginning of the overloaded method
         */
        String body() default "";

        /**
         * @return code that shall be placed at the end of the overloaded method, right before the call
         */
        String postBody() default "";

        /**
         * @return how to call the existing method from the new overloaded method. The instance ptr is prepended automatically
         */
        String call() default "";

        /**
         * @return code that gets added below the call
         */
        String postCall() default "";

    }

}
