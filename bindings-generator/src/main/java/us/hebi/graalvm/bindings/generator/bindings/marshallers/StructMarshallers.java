package us.hebi.graalvm.bindings.generator.bindings.marshallers;

import us.hebi.graalvm.bindings.generator.bindings.StructMarshaller;
import us.hebi.graalvm.bindings.model.StructDescription;
import us.hebi.graalvm.bindings.model.TypeDescription;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Registry of the {@link StructMarshaller} implementations, keyed on the qualified name
 * of the view interface. Built-ins are explicitly listed rather than discovered, so the
 * set is visible at build time. Consuming projects register their own implementations
 * on the view via {@code @CLibStruct(marshaller = ...)}, naming a precompiled class on
 * the processor classpath.
 *
 * @author Florian Enner
 * @since 6 Aug 2026
 */
public final class StructMarshallers {

    private StructMarshallers() {
    }

    public static Optional<StructMarshaller> resolve(String viewType) {
        var marshaller = BUILT_INS.get(viewType);
        return marshaller != null ? Optional.of(marshaller) : Optional.ofNullable(userByViewType.get(viewType));
    }

    /**
     * The descriptor carries the struct name rather than the Java view type, so a
     * marshalled view has to declare a {@code @CStruct} name that matches its simple name.
     */
    public static Optional<StructMarshaller> resolve(TypeDescription type) {
        return Optional.ofNullable(byStructName.get(type.getTypeName()));
    }

    public static Optional<StructMarshaller> resolve(StructDescription struct) {
        return Optional.ofNullable(byStructName.get(struct.getName()));
    }

    /**
     * @return all registered marshallers keyed by view type, which is what the direction
     * rules on the annotated API are derived from
     */
    public static Map<String, StructMarshaller> getAll() {
        var result = new LinkedHashMap<>(BUILT_INS);
        result.putAll(userByViewType);
        return result;
    }

    /**
     * Registers the marshaller a {@code @CLibStruct(marshaller = ...)} annotation names
     * for its view. The annotated view is the only naming direction, so one marshaller
     * class may serve several views. A view gets modeled once per use, so registering
     * the same class again is a no-op rather than a duplicate.
     *
     * @throws IllegalArgumentException naming the offending class on any lookup,
     *                                  instantiation, or conflict problem
     */
    public static void register(String viewType, String marshallerClassName) {
        var existing = userByViewType.get(viewType);
        if (existing != null) {
            if (existing.getClass().getName().equals(marshallerClassName)) {
                return;
            }
            throw new IllegalArgumentException("View '" + viewType + "' already registered marshaller "
                    + existing.getClass().getName() + ", cannot also register " + marshallerClassName);
        }
        if (BUILT_INS.containsKey(viewType)) {
            throw new IllegalArgumentException(
                    "View '" + viewType + "' already has a built-in marshaller: " + marshallerClassName);
        }
        var byViewType = new LinkedHashMap<>(userByViewType);
        byViewType.put(viewType, instantiate(marshallerClassName));
        userByViewType = Map.copyOf(byViewType);
        byStructName = indexByStructName();
    }

    /**
     * Drops every annotation-registered marshaller. Called when a new compilation
     * starts so runs in a shared JVM (IDE daemons, compile-testing) do not leak
     * into each other.
     */
    public static void clearRegistrations() {
        userByViewType = Map.of();
        byStructName = indexByStructName();
    }

    private static StructMarshaller instantiate(String className) {
        Class<?> clazz;
        try {
            clazz = Class.forName(className, true, StructMarshallers.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException(
                    "Struct marshaller class not found on the processor classpath: " + className);
        }
        if (!StructMarshaller.class.isAssignableFrom(clazz)) {
            throw new IllegalArgumentException(
                    "Struct marshaller does not implement " + StructMarshaller.class.getName() + ": " + className);
        }
        try {
            return (StructMarshaller) clazz.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException(
                    "Struct marshaller could not be instantiated (public no-argument constructor required): "
                            + className, e);
        }
    }

    public static String toStructName(String viewType) {
        return viewType.substring(viewType.lastIndexOf('.') + 1);
    }

    private static Map<String, StructMarshaller> indexByStructName() {
        var result = new LinkedHashMap<String, StructMarshaller>();
        getAll().forEach((viewType, marshaller) -> result.put(toStructName(viewType), marshaller));
        return Map.copyOf(result);
    }

    // Initialization order matters: indexByStructName() reads the two maps above it
    private static final Map<String, StructMarshaller> BUILT_INS = Map.of(
            DoubleSpanMarshaller.VIEW_TYPE, new DoubleSpanMarshaller()
    );

    private static volatile Map<String, StructMarshaller> userByViewType = Map.of();

    private static volatile Map<String, StructMarshaller> byStructName = indexByStructName();

}
