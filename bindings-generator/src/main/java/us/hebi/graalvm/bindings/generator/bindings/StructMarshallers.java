package us.hebi.graalvm.bindings.generator.bindings;

import us.hebi.graalvm.bindings.model.StructDescription;
import us.hebi.graalvm.bindings.model.TypeDescription;

import java.util.Collection;
import java.util.LinkedHashMap;
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

    /**
     * The descriptor carries the struct name rather than the Java view type, so a built-in view
     * has to declare a {@code @CStruct} name that matches its simple name.
     */
    public static Optional<StructMarshaller> resolve(TypeDescription type) {
        return Optional.ofNullable(BY_STRUCT_NAME.get(type.getTypeName()));
    }

    public static Optional<StructMarshaller> resolve(StructDescription struct) {
        return Optional.ofNullable(BY_STRUCT_NAME.get(struct.getName()));
    }

    /**
     * @return all registered marshallers, which is what the direction rules on the annotated
     * API are derived from
     */
    public static Collection<StructMarshaller> getAll() {
        return BUILT_INS.values();
    }

    static String toStructName(String viewType) {
        return viewType.substring(viewType.lastIndexOf('.') + 1);
    }

    private static Map<String, StructMarshaller> indexByStructName(Map<String, StructMarshaller> byViewType) {
        var result = new LinkedHashMap<String, StructMarshaller>();
        byViewType.forEach((viewType, marshaller) -> result.put(toStructName(viewType), marshaller));
        return Map.copyOf(result);
    }

    private static final Map<String, StructMarshaller> BUILT_INS = Map.of(
            DoubleSpanMarshaller.VIEW_TYPE, new DoubleSpanMarshaller()
    );

    private static final Map<String, StructMarshaller> BY_STRUCT_NAME = indexByStructName(BUILT_INS);

}
