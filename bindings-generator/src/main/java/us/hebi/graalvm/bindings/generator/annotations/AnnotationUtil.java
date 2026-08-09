package us.hebi.graalvm.bindings.generator.annotations;

import com.google.common.base.Strings;
import com.squareup.javapoet.ClassName;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import us.hebi.graalvm.bindings.generator.generators.ProcessingException;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static java.util.function.Predicate.*;

/**
 * @author Florian Enner
 * @since 05 Jan 2024
 */
public class AnnotationUtil {

    /**
     * A user-chosen name goes into a C symbol or typedef unchanged, so anything javac accepts
     * as an annotation value would otherwise surface as a stage 2 C compile error in the
     * consuming build. The element carries the location, the message only needs the value.
     */
    public static void checkIsCIdentifier(Element element, String value) {
        if (!isCIdentifier(value)) {
            throw new ProcessingException(element, "'%s' is not a valid C identifier", value);
        }
    }

    public static boolean isCIdentifier(String value) {
        return C_IDENTIFIER.matcher(value).matches();
    }

    public static <A extends Annotation> Optional<String> tryGetAnnotationValue(Element element, Class<A> annotation, Function<A, String> getter) {
        return getAnnotation(element, annotation).map(getter).filter(not(Strings::isNullOrEmpty));
    }

    public static <A extends Annotation> Optional<A> getAnnotation(Element element, Class<A> annotation) {
        if (element == null) {
            return Optional.empty();
        }

        // Direct approach
        A value = element.getAnnotation(annotation);
        if (value != null) {
            return Optional.ofNullable(value);
        }

        // Mirror approach (necessary?)
        // This is useful when processing parameters or when annotations aren't directly accessible
        for (AnnotationMirror annotationMirror : element.getAnnotationMirrors()) {
            String annotationType = annotationMirror.getAnnotationType().toString();
            if (annotationType.endsWith(annotation.getSimpleName())) {
                return Optional.of(createAnnotationFromMirror(annotationMirror, annotation));
            }
        }

        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    private static <A extends Annotation> A createAnnotationFromMirror(
            AnnotationMirror annotationMirror,
            Class<A> annotation) {
        Map<? extends ExecutableElement, ? extends AnnotationValue> elementValues =
                annotationMirror.getElementValues();

        InvocationHandler handler = (proxy, method, args) -> {
            String methodName = method.getName();

            // Handle special annotation methods
            if (methodName.equals("annotationType")) {
                return annotation;
            }

            // Look for the corresponding element in the mirror
            for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry :
                    elementValues.entrySet()) {
                if (entry.getKey().getSimpleName().toString().equals(methodName)) {
                    return entry.getValue().getValue();
                }
            }

            // If no value is found, return the default value if it exists
            Method elementMethod = annotation.getDeclaredMethod(methodName);
            return elementMethod.getDefaultValue();
        };

        return (A) Proxy.newProxyInstance(
                annotation.getClassLoader(),
                new Class<?>[]{annotation},
                handler
        );
    }

    public static boolean isGraalCFunctionPointer(Element element) {
        return element.getKind() == ElementKind.INTERFACE && implementsInterface(element, CFunctionPointer.class);
    }

    public static boolean implementsInterface(Element element, Class<?> targetInterface) {
        if (element instanceof TypeElement type) {
            return implementsInterface(type, targetInterface);
        }
        return false;
    }

    public static boolean implementsInterface(TypeElement element, Class<?> targetInterface) {
        String qualifiedName = targetInterface.getCanonicalName();

        // Check direct interfaces
        for (TypeMirror anInterface : element.getInterfaces()) {
            if (qualifiedName.equals(anInterface.toString())) {
                return true;
            }
        }

        // Check interfaces of parent classes (transitive implementation)
        TypeMirror superclass = element.getSuperclass();
        if (superclass.getKind() != TypeKind.NONE && !superclass.toString().equals("java.lang.Object")) {
            Element superElement = ((javax.lang.model.type.DeclaredType) superclass).asElement();
            if (superElement instanceof TypeElement superType) {
                return implementsInterface(superType, targetInterface);
            }
        }

        return false;
    }

    @Deprecated // used to use it for enums before underscore naming
    public static String getUppercaseCPrefix(EnvProcessor.GeneratorOptions options, String prefix) {
        String result = getCPrefix(options, prefix);
        if (!result.isEmpty() && Character.isLowerCase(result.charAt(0))) {
            result = Character.toUpperCase(result.charAt(0)) + result.substring(1, result.length() - 1);
        }
        return result;
    }

    public static String getCPrefix(EnvProcessor.GeneratorOptions options, String prefix) {
        return !Strings.isNullOrEmpty(prefix) ? prefix : options.getCPrefix();
    }

    public static ExecutableElement castToExecutableElement(Element element) {
        if (element instanceof ExecutableElement) {
            return (ExecutableElement) element;
        }
        throw new ProcessingException(element, "Input must be an executable element");
    }

    public static TypeElement castToEnumElement(Element element) {
        if (element instanceof TypeElement && element.getKind() == ElementKind.ENUM) {
            return (TypeElement) element;
        }
        throw new ProcessingException(element, "Input must be an enum element");
    }

    public static <A extends Annotation> A extractAnnotation(Element element, Class<A> annotationClass) {
        A annotation = element.getAnnotation(annotationClass);
        if (annotation == null) {
            throw ProcessingException.processException(element, "Element requires %s annotation", annotationClass.getName());
        }
        return annotation;
    }

    public static boolean isMethod(Element element) {
        return element.getKind() == ElementKind.METHOD;
    }

    public static boolean isPublic(Element element) {
        return element.getModifiers().contains(Modifier.PUBLIC);
    }

    public static boolean isInheritedFromObject(Element element) {
        TypeElement containingType = (TypeElement) element.getEnclosingElement();
        return containingType.getQualifiedName().toString().equals("java.lang.Object");
    }

    public static boolean isConstructor(Element element) {
        return element.getKind() == ElementKind.CONSTRUCTOR;
    }

    public static boolean isStatic(Element element) {
        return element.getModifiers().contains(Modifier.STATIC);
    }

    public static boolean isEnum(Element element) {
        return element.getKind() == ElementKind.ENUM;
    }

    public static Predicate<Element> hasAnnotation(Class<? extends Annotation> annotation) {
        return element -> element.getAnnotation(annotation) != null;
    }

    private static <A extends Annotation> String getName(Class<A> clazz) {
        return "@" + clazz.getSimpleName();
    }

    public static boolean hasTypeAnnotation(ProcessingEnvironment env, TypeMirror mirror, Class<? extends Annotation> annotation) {
        return getTypeAnnotation(env, mirror, annotation) != null;
    }

    public static <A extends Annotation> A getTypeAnnotation(ProcessingEnvironment env, TypeMirror mirror, Class<A> annotation) {
        // TypeMirrors from e.g. return types may not show annotations, so we need to look up the real TypeMirror.
        // Note that this does not work if the mirror comes from an annotated place, e.g., @CConst
        var realType = env.getElementUtils().getTypeElement(mirror.toString());
        return realType == null ? null : realType.getAnnotation(annotation);
    }

    /**
     * Gets a corresponding type element from a className and handles all nesting
     */
    public static TypeElement getTypeElement(ProcessingEnvironment env, ClassName className) {
        List<String> simpleNames = className.simpleNames();
        var type = env.getElementUtils().getTypeElement(className.topLevelClassName().reflectionName());

        // Start from the root and work down nested elements
        for (int i = 1; i < simpleNames.size(); i++) {
            final var level = simpleNames.get(i);
            type = type.getEnclosedElements().stream()
                    .filter(e -> e instanceof TypeElement)
                    .map(e -> (TypeElement) e)
                    .filter(e -> level.equals(e.getSimpleName().toString()))
                    .findAny()
                    .get();
        }

        return type;
    }

    private static boolean isTypeKind(ElementKind kind) {
        return kind == ElementKind.CLASS ||
               kind == ElementKind.ENUM ||
               kind == ElementKind.INTERFACE;
    }

    private static final Pattern C_IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

}
