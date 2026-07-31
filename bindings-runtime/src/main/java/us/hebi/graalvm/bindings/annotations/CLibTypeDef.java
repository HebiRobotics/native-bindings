package us.hebi.graalvm.bindings.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;

/**
 * @author Florian Enner
 * @since 20 Jun 2025
 */
@Retention(RetentionPolicy.CLASS)
@Target(TYPE)
public @interface CLibTypeDef {

    String name();

    String cDefinition();

    String cppDefinition();

    String pythonDefinition();

}
