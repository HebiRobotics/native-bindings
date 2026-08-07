package us.hebi.graalvm.bindings.tests.fixtures;

import us.hebi.graalvm.bindings.generator.bindings.StructMarshaller;

import java.util.List;
import java.util.Optional;

/**
 * A user marshaller for {@link MarshalledView} that customizes only the C++ face, used
 * by {@code ProcessorValidationTest} to pin the annotation registration and the include
 * contribution. The strings are deliberately distinctive so the test can assert they
 * reached the generated output.
 *
 * @author Florian Enner
 * @since 7 Aug 2026
 */
public class MarshalledViewMarshaller implements StructMarshaller {

    @Override
    public Optional<AllocatingReturn> allocatingReturn() {
        return Optional.empty();
    }

    @Override
    public Optional<CppStructMarshaller> cpp() {
        return Optional.of(CPP);
    }

    @Override
    public Optional<PyStructMarshaller> python() {
        return Optional.empty();
    }

    @Override
    public Optional<MexStructMarshaller> matlab() {
        return Optional.empty();
    }

    private static final CppStructMarshaller CPP = new CppStructMarshaller() {

        @Override
        public String paramType() {
            return "const std::array<double, 1>&";
        }

        @Override
        public String pack(String paramName) {
            return "{" + paramName + ".data()}";
        }

        @Override
        public String returnType() {
            throw new UnsupportedOperationException("MarshalledView returns are not supported");
        }

        @Override
        public String unpack(String structVar) {
            throw new UnsupportedOperationException("MarshalledView returns are not supported");
        }

        @Override
        public List<String> systemIncludes() {
            return List.of("array");
        }

    };

}
