package us.hebi.graalvm.bindings.tests.fixtures;

import org.graalvm.nativeimage.c.struct.CField;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.word.PointerBase;
import us.hebi.graalvm.bindings.annotations.CLibStruct;

/**
 * A precompiled struct view with no {@code @CContext}, used by {@code ProcessorValidationTest}
 * to pin that a classpath view without its own probe directives is rejected.
 *
 * @author Florian Enner
 * @since 6 Aug 2026
 */
@CLibStruct
@CStruct("NoContextPoint")
public interface NoContextView extends PointerBase {

    @CField("x")
    double x();

    @CField("x")
    void setX(double x);

}
