package us.hebi.graalvm.bindings.generator.generators;

import us.hebi.graalvm.bindings.generator.annotations.AnnotatedCLibClass;
import us.hebi.graalvm.bindings.model.*;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * @author Florian Enner
 * @since 05 Jan 2024
 */
public class GeneratorUtil {

    public static Function<String, String> createIndenter(String indent) {
        return input -> input.replaceAll("\n", "\n" + indent);
    }

    public static String indent(String input, String indent) {
        return indent + input.stripLeading().replaceAll("\n", "\n" + indent);
    }

    /**
     * Same as Guava's LOWER_UNDERSCORE.converterTo(UPPER_CAMEL).convert(input), but
     * it preserves any existing uppercase characters casing.
     * <p>
     * For example, hebi_charts_LineStyle becomes HebiChartsLineStyle
     */
    public static String convertUnderscoresToCamelCase(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        // Always start with uppercase
        StringBuilder result = new StringBuilder(input.length());
        result.append(Character.toUpperCase(input.charAt(0)));

        // For others, uppercase after
        boolean upperNext = false;
        for (int i = 1; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '_') {
                upperNext = true;
            } else {
                result.append(upperNext ? Character.toUpperCase(c) : c);
                upperNext = false;
            }
        }

        return result.toString();

    }

    public static Set<String> getPropertyNames(ClassDescription desc) {
        var properties = new TreeSet<String>();
        for (MethodDescription method : desc.getMethods()) {
            method.tryGetPropertyName().ifPresent(properties::add);
        }
        return properties;
    }

    public static boolean isAccessor(MethodDescription description) {
        return description.hasPropertyName();
    }

    public static boolean isGetter(MethodDescription description) {
        return description.hasPropertyName() && description.getParameters().length() == 0;
    }

    public static boolean isSetter(MethodDescription description) {
        // Note: setters can only have 1 parameter, but there may be custom overloads that can reduce multiple to one
        return description.hasPropertyName() && description.getParameters().length() >= 1;
    }

    public static boolean isConstructor(MethodDescription method) {
        return method.getIsStatic() && method.getIsConstructor();
    }

    public static Optional<MethodDescription> getConstructor(ClassDescription desc) {
        for (MethodDescription method : desc.getMethods()) {
            if (isConstructor(method)) return Optional.of(method);
        }
        return Optional.empty();
    }

    public static List<MethodDescription> getGetters(LibraryDescription lib, TypeDescription type) {
        if (type.getDataType() != DataType.OBJECT_HANDLE) return Collections.emptyList();
        List<MethodDescription> methods = new ArrayList<>();
        forEachDeclaredMethodOf(lib, type, m -> {
            if (GeneratorUtil.isGetter(m)) methods.add(m);
        });
        return methods.isEmpty() ? Collections.emptyList() : methods;
    }

    public static List<MethodDescription> getSetters(LibraryDescription lib, TypeDescription type) {
        if (type.getDataType() != DataType.OBJECT_HANDLE) return Collections.emptyList();
        List<MethodDescription> methods = new ArrayList<>();
        forEachDeclaredMethodOf(lib, type, m -> {
            if (GeneratorUtil.isSetter(m)) methods.add(m);
        });
        return methods.isEmpty() ? Collections.emptyList() : methods;
    }

    /**
     * iterates all declared and all inherited methods
     */
    public static void forEachDeclaredMethodOf(LibraryDescription lib, TypeDescription type, Consumer<MethodDescription> action) {
        for (var clazz : lib.getClasses()) {
            if (Objects.equals(clazz.getType().getTypeNameBytes(), type.getTypeNameBytes())) {
                for (var method : clazz.getMethods()) {
                    action.accept(method);
                }
                if (clazz.hasParent()) {
                    forEachDeclaredMethodOf(lib, clazz.getParent(), action);
                }
                return;
            }
        }
    }

    /**
     * The default isolate (empty name) plus all named per-class isolates,
     * sorted so that the generated output stays deterministic.
     */
    public static List<String> getIsolateNames(LibraryDescription lib) {
        var names = new TreeSet<String>();
        names.add("");
        for (ClassDescription clazz : lib.getClasses()) {
            clazz.tryGetIsolateName().ifPresent(names::add);
        }
        return new ArrayList<>(names);
    }

    /**
     * All C-symbols that are part of the public C ABI
     */
    public static List<String> getAllCMethodSymbols(LibraryDescription lib) {
        List<String> symbols = new ArrayList<>(getLastErrorSymbols(lib));
        for (var clazz : lib.getClasses()) {
            for (MethodDescription method : clazz.getMethods()) {
                symbols.add(method.getCName());
            }
            clazz.tryGetCDestructorName().ifPresent(symbols::add);
            clazz.tryGetToParentCastMethod().map(MethodDescription::getCName).ifPresent(symbols::add);
        }

        return symbols;
    }

    public static List<String> getLastErrorSymbols(LibraryDescription lib) {
        // One error accessor per isolate
        List<String> symbols = new ArrayList<>();
        for (String isolateName : getIsolateNames(lib)) {
            symbols.add(getLastErrorCName(lib, isolateName));
        }
        return symbols;
    }

    /**
     * The public C symbol of the last error accessor for the given isolate. The
     * isolate name rides in the class-name slot, so the default isolate yields
     * lib_getLastError and a named one lib_secondary_getLastError.
     */
    public static String getLastErrorCName(LibraryDescription lib, String isolateName) {
        return AnnotatedCLibClass.getCEntryName(true, lib.getCPrefix(), isolateName, "getLastError");
    }

    /**
     * True if any method passes or returns a span, so generators can emit the
     * struct definition only when it is needed.
     */
    public static boolean usesSpans(LibraryDescription lib) {
        for (ClassDescription apiClass : lib.getClasses()) {
            if (findSpanType(apiClass).isPresent()) return true;
        }
        return false;
    }

    /**
     * The first span used by the class, if any. Generators need the description
     * itself rather than a boolean when they emit the struct definition, because
     * the type name is derived from it.
     */
    public static Optional<TypeDescription> findSpanType(ClassDescription apiClass) {
        for (MethodDescription method : apiClass.getMethods()) {
            if (method.getReturnType().getDataType() == DataType.FLOAT64_SPAN) {
                return Optional.of(method.getReturnType());
            }
            for (TypeDescription param : method.getParameters()) {
                if (param.getDataType() == DataType.FLOAT64_SPAN) {
                    return Optional.of(param);
                }
            }
        }
        return Optional.empty();
    }

}
