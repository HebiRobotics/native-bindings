package us.hebi.graalvm.bindings.generator.generators;

import us.hebi.graalvm.bindings.model.*;
import us.hebi.quickbuf.RepeatedMessage;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * @author Florian Enner
 * @since 05 Jan 2024
 */
public class GeneratorUtil {

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

    /**
     * A face keeps the constructor overloads it can tell apart and turns the rest into static
     * factories. Two constructors collide when some argument list is accepted by both, so each
     * one contributes the lists it accepts as "key,key" strings (one per count its defaults allow,
     * times the key choices of parameters that accept several classes) and a later constructor
     * whose list is already taken is demoted. The wrap path's native handle is taken from the
     * start, a face that passes it through the constructor keys it the same way. Works on a copy,
     * the model keeps every constructor, and the reason goes into the method's documentation.
     */
    public static void filterOverloadedConstructors(LibraryDescription lib, String nativeHandleConstructor, Function<TypeDescription, List<String>> overloadKeys) {
        for (var apiClass : lib.getClasses()) {
            var taken = new HashMap<String, String>(); // argument list -> what accepts it
            if (nativeHandleConstructor != null) {
                taken.put(nativeHandleConstructor, "native handle");
            }
            for (var method : apiClass.getMethods()) {
                if (!isConstructor(method)) continue;
                var lists = getArgumentLists(method.getParameters(), overloadKeys);
                var clash = lists.stream().filter(taken::containsKey).findFirst();
                if (clash.isPresent()) {
                    method.setIsConstructor(false);
                    String reason = "Not a constructor here, its arguments cannot be told from " + taken.get(clash.get()) + ".";
                    method.setDocumentation(method.hasDocumentation() ? method.getDocumentation().trim() + "\n\n" + reason : reason);
                } else {
                    lists.forEach(list -> taken.put(list, method.getName()));
                }
            }
        }
    }

    private static List<String> getArgumentLists(RepeatedMessage<TypeDescription> params,
                                                 Function<TypeDescription, List<String>> overloadKeys) {
        int required = 0;
        for (var param : params) {
            if (!param.hasDefaultValue()) required++;
        }
        var lists = new ArrayList<String>();
        var prefixes = List.of("");
        if (required == 0) lists.add("");
        for (int i = 0; i < params.length(); i++) {
            var extended = new ArrayList<String>();
            for (var prefix : prefixes) {
                for (var key : overloadKeys.apply(params.get(i))) {
                    extended.add(prefix.isEmpty() ? key : prefix + "," + key);
                }
            }
            prefixes = extended;
            if (i + 1 >= required) lists.addAll(prefixes);
        }
        return lists;
    }

    public static List<MethodDescription> getConstructors(ClassDescription desc) {
        List<MethodDescription> constructors = new ArrayList<>();
        for (MethodDescription method : desc.getMethods()) {
            if (isConstructor(method)) constructors.add(method);
        }
        return constructors;
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
     * All C-symbols that are part of the public C ABI
     */
    public static List<String> getAllCMethodSymbols(LibraryDescription lib) {
        List<String> symbols = new ArrayList<>();
        for (var clazz : lib.getClasses()) {
            for (MethodDescription method : clazz.getMethods()) {
                symbols.add(method.getCName());
            }
            clazz.tryGetCDestructorName().ifPresent(symbols::add);
            clazz.tryGetToParentCastMethod().map(MethodDescription::getCName).ifPresent(symbols::add);
        }

        return symbols;
    }

}
