package us.hebi.graalvm.bindings.tests.fixtures;

import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.struct.CField;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.word.PointerBase;
import us.hebi.graalvm.bindings.annotations.CLibStruct;
import us.hebi.graalvm.bindings.runtime.types.StructDirectives;

/**
 * A precompiled struct view carrying {@link MarshalledViewMarshaller} in its
 * annotation, used by {@code ProcessorValidationTest} to pin the marshaller
 * registration. The {@code @CStruct} name matches the simple name because that is
 * how the generators resolve a marshaller from the descriptor.
 *
 * @author Florian Enner
 * @since 7 Aug 2026
 */
@CLibStruct(marshaller = MarshalledViewMarshaller.class)
@CContext(StructDirectives.class)
@CStruct("MarshalledView")
public interface MarshalledView extends PointerBase {

    @CField("x")
    double x();

    @CField("x")
    void setX(double x);

}
