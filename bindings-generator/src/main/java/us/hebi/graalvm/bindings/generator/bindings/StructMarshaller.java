package us.hebi.graalvm.bindings.generator.bindings;

import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.TypeName;

import java.util.Optional;

/**
 * Conversions for one {@code @CLibStruct} view. Names and layout are never a marshaller
 * concern, they derive from the annotation and the accessor list the same way for every
 * struct, and a marshaller contributes conversions only. Each language face is optional,
 * and an absent one falls back to the derived default emission.
 *
 * @author Florian Enner
 * @since 6 Aug 2026
 */
public interface StructMarshaller {

    /**
     * @return qualified name of the view interface this marshaller is registered for
     */
    String viewType();

    Optional<AllocatingReturn> allocatingReturn();

    Optional<CppStructMarshaller> cpp();

    Optional<PyStructMarshaller> python();

    Optional<MexStructMarshaller> matlab();

    /** Authoring form of a return that allocates in Java and fills a caller-provided out view. */
    interface AllocatingReturn {

        TypeName javaType();

        CodeBlock fillOut(String outName, String resultVar);

        CodeBlock zeroOut(String outName);

    }

    /** Conversions for the C++ face, replacing the by-value struct default. */
    interface CppStructMarshaller {

        String paramType();

        String pack(String paramName);

        String returnType();

        String unpack(String structVar);

    }

    /** Conversions for the Python face, replacing the dataclass default. */
    interface PyStructMarshaller {

        String pack(String paramName);

        String unpack(String structVar);

        String typeHint();

    }

    /** Conversions for the MATLAB face, replacing the native struct default. */
    interface MexStructMarshaller {

        String read(String prhsExpr, String structVar);

        String write(String structVar);

    }

}
