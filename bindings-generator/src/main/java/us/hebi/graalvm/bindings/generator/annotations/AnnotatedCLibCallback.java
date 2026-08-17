package us.hebi.graalvm.bindings.generator.annotations;

import org.graalvm.nativeimage.c.function.InvokeCFunctionPointer;
import us.hebi.graalvm.bindings.annotations.CLibCallback;
import us.hebi.graalvm.bindings.generator.generators.ProcessingException;
import us.hebi.graalvm.bindings.model.CallbackDescription;
import us.hebi.graalvm.bindings.model.DataType;
import us.hebi.graalvm.bindings.model.TypeDescription;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import java.util.ArrayList;
import java.util.List;

/**
 * A {@code CFunctionPointer} interface used as a parameter type. The typedef every language
 * face declares is derived from the interface's single {@code @InvokeCFunctionPointer}
 * method, so the signature is stated once in the annotated API.
 *
 * @author Florian Enner
 * @since 07 Aug 2026
 */
public class AnnotatedCLibCallback extends AnnotatedCLibElement<TypeElement> {

    public static boolean isCallbackType(TypeElement element) {
        return element != null && AnnotationUtil.isGraalCFunctionPointer(element);
    }

    public AnnotatedCLibCallback(TypeElement element, ProcessingEnvironment env) {
        super(element, env);
        AnnotationUtil.checkIsCIdentifier(element, getName());
        var annotation = element.getAnnotation(CLibCallback.class);
        if (annotation != null) {
            AnnotationUtil.checkIsCIdentifier(element, annotation.cPrefix(), annotation.value());
        }

        invokeMethod = findInvokeMethod();

        description = CallbackDescription.newInstance()
                .setName(getName())
                .setCPrefix(getCPrefix())
                .setReturnType(toCallbackType(AnnotatedClibType.fromType(invokeMethod.getReturnType(), env), true));
        for (var parameter : invokeMethod.getParameters()) {
            var type = AnnotatedClibType.fromVariable(parameter, env);
            description.getMutableParameters().next()
                    .copyFrom(toCallbackType(type, false))
                    .setParamName(type.getName());
        }
    }

    private ExecutableElement findInvokeMethod() {
        List<ExecutableElement> found = new ArrayList<>();
        for (var enclosed : element.getEnclosedElements()) {
            if (enclosed.getKind() == ElementKind.METHOD && enclosed.getAnnotation(InvokeCFunctionPointer.class) != null) {
                found.add((ExecutableElement) enclosed);
            }
        }
        if (found.size() != 1) {
            throw new ProcessingException(element,
                    "a CFunctionPointer parameter must declare exactly one @InvokeCFunctionPointer method, found %s: %s",
                    found.isEmpty() ? "none" : found.stream().map(m -> String.valueOf(m.getSimpleName())).toList(),
                    getQualifiedName());
        }
        return found.get(0);
    }

    /**
     * The allow-list is GraalVM's, not ours. {@code @InvokeCFunctionPointer} accepts only
     * primitive types, word types and {@code @CEnum} types, so a handle, a by-value struct
     * or a plain Java enum fails the image build rather than this processor. Rejecting them
     * here turns a stage 1 failure into a diagnostic on the offending declaration.
     */
    private TypeDescription toCallbackType(AnnotatedClibType type, boolean isReturn) {
        DataType dataType;
        try {
            dataType = type.getDataType();
        } catch (IllegalArgumentException iae) {
            throw unsupported(type, isReturn);
        }
        if (dataType == DataType.ENUM) {
            throw javaEnum(type, isReturn);
        }
        if (!(isReturn && dataType == DataType.VOID) && !isSupportedType(dataType)) {
            throw unsupported(type, isReturn);
        }
        return type.toDescription();
    }

    private static boolean isSupportedType(DataType type) {
        return switch (type) {
            case BOOL, CHAR16, INT8, INT16, INT32, INT64, FLOAT32, FLOAT64, SIZE_T -> true;
            case VOID_POINTER, CHAR_POINTER, CHAR_POINTER_POINTER, UINT8_POINTER, INT16_POINTER,
                 INT32_POINTER, INT64_POINTER, FLOAT32_POINTER, FLOAT64_POINTER -> true;
            default -> false;
        };
    }

    private ProcessingException unsupported(AnnotatedClibType type, boolean isReturn) {
        return new ProcessingException(invokeMethod,
                "@InvokeCFunctionPointer %s type '%s' is not supported in a callback signature: %s.%s",
                isReturn ? "return" : "parameter", type.getJavaType(),
                element.getSimpleName(), invokeMethod.getSimpleName());
    }

    /**
     * The annotated API declares plain Java enums and the {@code @CEnum} mirror is generated
     * from them, so the mirror is not what the interface names. Admitting the plain enum here
     * only moves the failure to the image build.
     */
    private ProcessingException javaEnum(AnnotatedClibType type, boolean isReturn) {
        return new ProcessingException(invokeMethod,
                "@InvokeCFunctionPointer %s type '%s' is a Java enum, which a callback signature cannot use"
                + " because GraalVM only accepts @CEnum types there: %s.%s",
                isReturn ? "return" : "parameter", type.getJavaType(),
                element.getSimpleName(), invokeMethod.getSimpleName());
    }

    /**
     * @return the typedef name, which is the interface's simple name unless
     * {@code @CLibCallback} overrides it
     */
    public static String getName(TypeElement element) {
        var annotation = element.getAnnotation(CLibCallback.class);
        if (annotation != null && !annotation.value().isEmpty()) {
            return annotation.value();
        }
        return String.valueOf(element.getSimpleName());
    }

    public String getName() {
        return getName(element);
    }

    /**
     * An unannotated interface is a valid callback, so the annotation is optional here the
     * way it is in {@link #getName(TypeElement)}.
     */
    public static String getCPrefix(EnvProcessor.GeneratorOptions options, TypeElement element) {
        var annotation = element.getAnnotation(CLibCallback.class);
        return AnnotationUtil.getCPrefix(options, annotation == null ? "" : annotation.cPrefix()) + "_";
    }

    public String getCPrefix() {
        return getCPrefix(getOptions(), element);
    }

    public String getQualifiedName() {
        return String.valueOf(element.getQualifiedName());
    }

    public TypeElement getElement() {
        return element;
    }

    public CallbackDescription toDescription() {
        return description;
    }

    private final ExecutableElement invokeMethod;

    private final CallbackDescription description;

}
