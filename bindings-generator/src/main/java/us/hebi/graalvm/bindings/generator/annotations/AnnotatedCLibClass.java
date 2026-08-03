package us.hebi.graalvm.bindings.generator.annotations;

import com.google.auto.common.MoreElements;
import com.google.common.base.CaseFormat;
import com.google.common.base.Strings;
import com.squareup.javapoet.ClassName;
import us.hebi.graalvm.bindings.annotations.CLibClass;
import us.hebi.graalvm.bindings.annotations.CLibMethod;
import us.hebi.graalvm.bindings.annotations.CLibTypeDef;
import us.hebi.graalvm.bindings.model.MethodDescription;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;
import java.util.*;

import static com.google.common.base.Preconditions.*;
import static java.util.function.Predicate.*;

/**
 * Wrapper for native classes
 *
 * @author Florian Enner
 * @since 09 Feb 2024
 */
public class AnnotatedCLibClass extends AnnotatedCLibElement<TypeElement> {

    public AnnotatedCLibClass(TypeElement typeElement, ProcessingEnvironment processingEnvironment) {
        super(typeElement, processingEnvironment);
        annotation = checkNotNull(typeElement.getAnnotation(CLibClass.class), "Class %s is not annotated with @CApiClass");
        publicApiMethods = getPublicMethodsUpToAnnotatedParent(typeElement);

        nestedEnums = typeElement.getEnclosedElements().stream()
                .filter(AnnotatedCLibEnum::isNativeEnum)
                .map(element -> new AnnotatedCLibEnum(element, processingEnvironment, this))
                .toList();

        typeDefs = typeElement.getEnclosedElements().stream()
                .map(element -> element.getAnnotation(CLibTypeDef.class))
                .filter(Objects::nonNull)
                .toList();

    }

    private List<AnnotatedCLibMethod> getPublicMethodsUpToAnnotatedParent(TypeElement startClass) {
        List<AnnotatedCLibMethod> methods = new ArrayList<>();
        TypeElement currentClass = startClass;

        while (currentClass != null && !Object.class.getName().equals(currentClass.getQualifiedName().toString())) {

            // If we find an annotated parent class (not our start class), stop here
            if (currentClass != startClass) {
                var annotation = currentClass.getAnnotation(CLibClass.class);
                if (annotation != null && !annotation.ignore()) {
                    break;
                }
            }

            // Add all valid methods from this class
            currentClass.getEnclosedElements().stream()
                    .filter(AnnotationUtil::isMethod)
                    .filter(AnnotationUtil::isPublic)
                    .filter(not(AnnotationUtil::isConstructor))
                    .filter(AnnotationUtil.hasAnnotation(CLibMethod.class))
                    .map(AnnotationUtil::castToExecutableElement)
                    .map(element -> new AnnotatedCLibMethod(element, env, this))
                    .filter(element -> !element.shouldIgnore())
                    .forEach(methods::add);

            // Move up to the parent class
            currentClass = (TypeElement) env.getTypeUtils().asElement(currentClass.getSuperclass());
        }

        // TODO: sort by name?
        // .sorted(Comparator.comparing(AnnotatedNativeMethod::getMethodName))

        return methods;
    }

    public boolean shouldIgnore() {
        return annotation.ignore();
    }

    public String getName() {
        return annotation.value();
    }

    public boolean enableInstancing() {
        return annotation.enableInstancing();
    }

    @Deprecated
    public boolean isAbstractClass() {
        return annotation.abstractClass();
    }

    public Optional<String> getIsolateName() {
        return Optional.ofNullable(annotation.isolateName())
                .filter(s -> !s.isEmpty());
    }

    /**
     * @return first parent class with annotation
     */
    public Optional<AnnotatedClibType> getParent() {
        return getParent(element);
    }

    public Optional<MethodDescription> getToParentCast() {
        return getParent().map(parent -> {
            var method = MethodDescription.newInstance();
            method.setName("%s_to_%s".formatted(getApiClassName(), parent.getClassName().get()));
            method.setLowerUnderscoreName(method.getName());
            method.setCName(AnnotationUtil.getCPrefix(getOptions(), annotation.cPrefix()) + "_" + method.getName());
            method.setIsStatic(true);
            method.setReturnType(parent.toDescription());
            var param = method.getMutableParameters().next();
            param.copyFrom(getNativeType().toDescription()).setParamName("ptr");
            return method;
        });
    }

    private Optional<AnnotatedClibType> getParent(TypeElement child) {
        TypeElement parent = (TypeElement) env.getTypeUtils().asElement(child.getSuperclass());
        while (parent != null && !Object.class.getName().equals(parent.getQualifiedName().toString())) {
            var annotation = parent.getAnnotation(CLibClass.class);
            if (annotation != null && !annotation.ignore()) {
                checkState(enableInstancing(), "Only instantiable classes can inherit");
                checkState(annotation.enableInstancing(), "Parent classes must be instantiable");
                checkState(annotation.abstractClass(), "Parent classes must be abstract");
                return Optional.of(AnnotatedClibType.fromType(parent.asType(), env));
            }
            parent = (TypeElement) env.getTypeUtils().asElement(parent.getSuperclass());
        }
        return Optional.empty();
    }

    /**
     * @return highest parent class with annotation
     */
    public Optional<AnnotatedClibType> getRoot() {
        Optional<AnnotatedClibType> root = Optional.empty();
        TypeElement parent = (TypeElement) env.getTypeUtils().asElement(element.getSuperclass());
        while (!Object.class.getName().equals(parent.getQualifiedName().toString())) {
            var annotation = parent.getAnnotation(CLibClass.class);
            if (annotation != null && !annotation.ignore()) {
                checkState(enableInstancing(), "Only instantiable classes can inherit");
                checkState(annotation.enableInstancing(), "Parent classes must be instantiable");
                checkState(annotation.abstractClass(), "Parent classes must be abstract");
                root = Optional.of(AnnotatedClibType.fromType(parent.asType(), env));
            }
            parent = (TypeElement) env.getTypeUtils().asElement(parent.getSuperclass());
        }
        return root;
    }

    public String getDestructorCEntryName() {
        return getCEntryName(annotation.destructorName());
    }

    public AnnotatedClibType getNativeType() {
        return AnnotatedClibType.fromType(element.asType(), env);
    }

    protected String getCEntryName(String methodName) {
        return getCEntryName(annotation.useCamelCase(), AnnotationUtil.getCPrefix(getOptions(), annotation.cPrefix()), getApiClassName(), methodName);
    }

    public static String getCEntryName(boolean camelCase, String prefix, String className, String methodName) {
        CaseFormat targetCase = camelCase ? CaseFormat.LOWER_CAMEL : CaseFormat.LOWER_UNDERSCORE;
        String conventionalName = CaseFormat.LOWER_CAMEL.to(targetCase, methodName);
        var builder = new StringBuilder();
        builder.append(prefix);
        if (!Strings.isNullOrEmpty(className)) {
            builder.append("_").append(className);
        }
        if (!Strings.isNullOrEmpty(conventionalName)) {
            builder.append("_").append(conventionalName);
        }
        return builder.toString();
    }

    protected String getApiClassName() {
        return annotation.value();
    }

    public ClassName getJavaType() {
        // Nesting aware, so a nested class is referenced as Outer.Inner
        return ClassName.get(element);
    }

    public ClassName getWrapperType() {
        return getWrapperType(getJavaType());
    }

    /**
     * The generated wrapper is always a top-level class, so a nested API class has its
     * enclosing types folded into the name. Joining the whole nesting path.
     */
    protected static ClassName getWrapperType(ClassName javaType) {
        return ClassName.get(javaType.packageName() + WRAPPER_PACKAGE_SUFFIX,
                String.join("_", javaType.simpleNames()) + WRAPPER_NAME_SUFFIX);
    }

    public String getNamespace() {
        return String.valueOf(MoreElements.getPackage(element).getQualifiedName());
    }

    public List<AnnotatedCLibMethod> getPublicApiMethods() {
        return publicApiMethods;
    }

    public List<AnnotatedCLibEnum> getNestedEnums() {
        return nestedEnums;
    }

    public List<CLibTypeDef> getTypeDefs() {
        return typeDefs;
    }

    final List<AnnotatedCLibMethod> publicApiMethods;

    final List<AnnotatedCLibEnum> nestedEnums;

    final List<CLibTypeDef> typeDefs;

    final CLibClass annotation;

    private static final String WRAPPER_PACKAGE_SUFFIX = ".generated";
    private static final String WRAPPER_NAME_SUFFIX = "$CEntries";

}
