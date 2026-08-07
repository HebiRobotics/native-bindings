package us.hebi.graalvm.bindings.tests.fixtures;

import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.struct.CField;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.word.PointerBase;
import us.hebi.graalvm.bindings.annotations.CLibStruct;
import us.hebi.graalvm.bindings.runtime.structs.include.StructDirectives;

/**
 * A precompiled struct view that ships its own {@code @CContext}, used by
 * {@code ProcessorValidationTest} to pin that such a classpath view is accepted.
 *
 * @author Florian Enner
 * @since 6 Aug 2026
 */
@CLibStruct
@CContext(StructDirectives.class)
@CStruct("WithContextPoint")
public interface WithContextView extends PointerBase {

    @CField("x")
    double x();

    @CField("x")
    void setX(double x);

}
