package us.hebi.graalvm.bindings.generator.bindings;

import java.util.Map;
import java.util.Optional;

/**
 * Registry of the built-in {@link StructMarshaller} implementations, keyed on the qualified
 * name of the view interface. Explicitly listed rather than discovered, so the set is visible
 * at build time.
 *
 * @author Florian Enner
 * @since 6 Aug 2026
 */
public final class StructMarshallers {

    private StructMarshallers() {
    }

    public static Optional<StructMarshaller> resolve(String viewType) {
        return Optional.ofNullable(BUILT_INS.get(viewType));
    }

    private static final Map<String, StructMarshaller> BUILT_INS = Map.of();

}
