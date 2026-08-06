package us.hebi.graalvm.bindings.generator.annotations;

import com.google.common.base.Strings;
import com.squareup.javapoet.ArrayTypeName;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeName;
import us.hebi.graalvm.bindings.annotations.CLibComment;
import us.hebi.graalvm.bindings.annotations.CLibMethod;
import us.hebi.graalvm.bindings.model.DataType;
import us.hebi.graalvm.bindings.model.MethodFlag;
import us.hebi.graalvm.bindings.model.MethodOverload;
import us.hebi.graalvm.bindings.model.TypeDescription;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import java.util.*;

import static com.google.common.base.Preconditions.*;
import static java.util.function.Predicate.*;

/**
 * Wrapper for methods contained inside native classes
 *
 * @author Florian Enner
 * @since 09 Feb 2024
 */
public class AnnotatedCLibMethod extends AnnotatedCLibElement<ExecutableElement> {

    public AnnotatedCLibMethod(ExecutableElement executableElement, ProcessingEnvironment processingEnvironment, AnnotatedCLibClass parent) {
        super(executableElement, processingEnvironment);
        this.parent = parent;
        this.returnType = AnnotatedClibType.fromType(executableElement.getReturnType(), processingEnvironment);
        this.annotation = element.getAnnotation(CLibMethod.class);

        // Flag validity checks
        for (us.hebi.graalvm.bindings.annotations.MethodFlag flag : AnnotationUtil.getAnnotation(element, CLibMethod.class).map(CLibMethod::flags).orElseGet(() -> new us.hebi.graalvm.bindings.annotations.MethodFlag[0])) {
            switch (flag) {
                case Const -> {
                    checkArgument(!isStatic() && parent.enableInstancing(), "only instance methods may be marked const: %s", executableElement.getSimpleName());
                }
                case Noexcept -> {
                    checkArgument(element.getThrownTypes().isEmpty(), "throwing methods may not be marked noexcept: %s", executableElement.getSimpleName());
                }
                default -> throw new IllegalArgumentException("Unknown flag " + flag);
            }
            flags.add(toModelFlag(flag));
        }

        // Spans have one Java form per direction: parameters borrow a caller-owned buffer
        // through the DoubleSpan view, returns hand back a copied double[]
        checkArgument(!DOUBLE_SPAN_TYPE.equals(returnType.getJavaType()),
                "Span returns must be declared as double[], not DoubleSpan: %s", executableElement.getSimpleName());
        for (var parameter : getParameters()) {
            checkArgument(!DOUBLE_ARRAY_TYPE.equals(parameter.getJavaType()),
                    "Span parameters must be declared as DoubleSpan, not double[]: %s", executableElement.getSimpleName());
        }

        // A struct view leaves the ABI through a caller-provided @CLibOut parameter, which
        // every public face presents as a by-value return
        checkArgument(!returnType.isStructView(),
                "@CLibStruct views may not be returned, declare void with a @CLibOut parameter instead: %s",
                executableElement.getSimpleName());
        AnnotatedClibType outParam = null;
        for (var parameter : getParameters()) {
            if (!parameter.isOutParameter()) continue;
            checkArgument(parameter.isStructView(),
                    "@CLibOut may only be used with a @CLibStruct annotated type, got '%s': %s",
                    parameter.getName(), executableElement.getSimpleName());
            checkArgument(outParam == null,
                    "at most one @CLibOut parameter is allowed, found a second one on '%s': %s",
                    parameter.getName(), executableElement.getSimpleName());
            outParam = parameter;
        }
        if (outParam != null) {
            checkArgument(returnType.isVoid(),
                    "a method with a @CLibOut parameter must be declared void, the out struct is the return value: %s",
                    executableElement.getSimpleName());
        }
        outParameter = outParam;
        outStruct = outParam == null ? null : new AnnotatedCLibStruct(outParam.element, processingEnvironment);
        if (outStruct != null) {
            outStruct.checkEveryFieldHasSetter();
        }

        // One-based languages convert indices by arithmetic, so the type has to be integral
        checkArgument(!returnType.isIndex() || isIndexType(returnType),
                "@CLibIndex may only be used on integral types: %s", executableElement.getSimpleName());
        for (var parameter : getParameters()) {
            if (!parameter.isIndex()) continue;
            checkArgument(isIndexType(parameter),
                    "@CLibIndex may only be used on integral types: %s", executableElement.getSimpleName());
            // A one-based language shifts the default into its own space, which needs a number
            parameter.getDefaultValue().ifPresent(value -> checkArgument(isInteger(value),
                    "@CLibIndex requires an integer @CDefault, got '%s': %s", value, executableElement.getSimpleName()));
        }
    }

    private static boolean isIndexType(AnnotatedClibType type) {
        return switch (type.getDataType()) {
            case INT8, INT16, INT32, INT64, SIZE_T -> true;
            default -> false;
        };
    }

    private static boolean isInteger(String value) {
        try {
            Long.parseLong(value.trim());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static final TypeName DOUBLE_SPAN_TYPE =
            ClassName.get("us.hebi.graalvm.bindings.runtime.types", "DoubleSpan");
    private static final TypeName DOUBLE_ARRAY_TYPE = ArrayTypeName.of(TypeName.DOUBLE);

    /**
     * @return the name this method is exported under, which the annotation may override
     */
    public String getMethodName() {
        return getAnnotation()
                .map(CLibMethod::name)
                .filter(not(Strings::isNullOrEmpty))
                .orElse(getJavaMethodName());
    }

    /**
     * @return the name of the Java method itself, which the annotation cannot change
     * <p>
     * Generated entry points have to call this rather than {@link #getMethodName()},
     * otherwise an exported rename would emit a call to a method that does not exist.
     */
    public String getJavaMethodName() {
        return String.valueOf(element.getSimpleName());
    }

    public boolean shouldIgnore() {
        return getAnnotation()
                .map(CLibMethod::ignore)
                .orElse(false);
    }

    public Optional<String> getDocumentation() {
        return AnnotationUtil.tryGetAnnotationValue(element, CLibComment.class, CLibComment::value);
    }

    public Optional<String> getThrowsOnComment() {
        return AnnotationUtil.tryGetAnnotationValue(element, CLibComment.class, CLibComment::throwsOn);
    }

    public boolean isThrowing() {
        return !flags.contains(MethodFlag.Noexcept);
    }

    public Optional<String> getCppBody() {
        return AnnotationUtil.tryGetAnnotationValue(element, CLibMethod.class, CLibMethod::cppBody);
    }

    public List<MethodOverload> getCppOverloads() {
        return getAnnotation()
                .map(CLibMethod::cppOverloads)
                .filter(array -> array.length > 0)
                .map(array -> Arrays.stream(array)
                        .map(AnnotatedCLibMethod::toMessage)
                        .filter(not(MethodOverload::isEmpty))
                        .toList()
                ).orElseGet(Collections::emptyList);
    }

    public Optional<MethodOverload> getPythonOverload() {
        return getAnnotation()
                .map(CLibMethod::pythonOverload)
                .map(AnnotatedCLibMethod::toMessage)
                .filter(not(MethodOverload::isEmpty));
    }

    public Optional<MethodOverload> getMatlabOverload() {
        return getAnnotation()
                .map(CLibMethod::matlabOverload)
                .map(AnnotatedCLibMethod::toMessage)
                .filter(not(MethodOverload::isEmpty));
    }

    public Optional<String> getPropertyName() {
        // TODO: sanity check that it's actually a getter or setter?
        return getAnnotation()
                .map(CLibMethod::property)
                .filter(not(Objects::isNull))
                .filter(not(String::isEmpty));
    }

    private static MethodOverload toMessage(CLibMethod.MethodOverload annotation) {
        var instance = MethodOverload.newInstance();
        if (!annotation.parameters().isEmpty()) instance.setParameters(annotation.parameters());
        if (!annotation.body().isEmpty()) instance.setBody(annotation.body());
        if (!annotation.postBody().isEmpty()) instance.setPostBody(annotation.postBody());
        if (!annotation.call().isEmpty()) instance.setCall(annotation.call());
        if (!annotation.postCall().isEmpty()) instance.setPostCall(annotation.postCall());
        return instance;
    }

    /**
     * The annotation API and the descriptor model each define their own MethodFlag
     * enum, so we map between them by name.
     */
    private static MethodFlag toModelFlag(us.hebi.graalvm.bindings.annotations.MethodFlag flag) {
        return MethodFlag.valueOf(flag.name());
    }

    public String getCEntryPointName() {
        return parent.getCEntryName(getMethodName());
    }

    public boolean isConstructor() {
        return getAnnotation().map(CLibMethod::constructor).orElse(false);
    }

    public boolean isStatic() {
        return AnnotationUtil.isStatic(element);
    }

    public List<AnnotatedClibType> getParameters() {
        return element.getParameters().stream()
                .map(variable -> AnnotatedClibType.fromVariable(variable, env))
                .toList();
    }

    private Optional<CLibMethod> getAnnotation() {
        return Optional.ofNullable(annotation);
    }

    /**
     * The Java return type, which stays void for a method that fills a {@code @CLibOut} struct.
     */
    public AnnotatedClibType getReturnType() {
        return returnType;
    }

    /**
     * The return type the public faces see, e.g., a {@code @CLibOut} parameter as a return value
     */
    public TypeDescription getReturnTypeDescription() {
        return outParameter == null ? returnType.toDescription() : outParameter.toDescription();
    }

    /**
     * @return the {@code @CLibOut} parameter, which is described as the return value and
     * therefore omitted from the descriptor's parameter list
     */
    public Optional<AnnotatedClibType> getOutParameter() {
        return Optional.ofNullable(outParameter);
    }

    /**
     * @return the struct view behind {@link #getOutParameter()}, whose setters fill it
     */
    public Optional<AnnotatedCLibStruct> getOutStruct() {
        return Optional.ofNullable(outStruct);
    }

    public List<MethodFlag> getFlags() {
        return flags;
    }

    final CLibMethod annotation;
    final AnnotatedCLibClass parent;

    final AnnotatedClibType returnType;

    final AnnotatedClibType outParameter;
    final AnnotatedCLibStruct outStruct;

    final List<MethodFlag> flags = new ArrayList<>(2);

}
