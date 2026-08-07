package us.hebi.graalvm.bindings.generator.annotations;

import org.graalvm.nativeimage.c.constant.CEnum;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.nativeimage.c.type.CConst;
import us.hebi.graalvm.bindings.annotations.CLibClass;
import us.hebi.graalvm.bindings.annotations.CLibEnum;
import us.hebi.graalvm.bindings.annotations.CLibTypeDef;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import java.lang.annotation.Annotation;
import java.util.Objects;

/**
 * @author Florian Enner
 * @since 09 Feb 2024
 */
public class AnnotatedCLibElement<T extends Element> implements EnvProcessor {

    protected boolean isNativeApiType(TypeMirror type) {
        return hasTypeAnnotation(type, CLibClass.class);
    }

    protected boolean isGeneratedCEnumType(TypeMirror type) {
        return hasTypeAnnotation(type, CLibEnum.class);
    }

    protected boolean isDefinedType(TypeMirror type) {
        return hasTypeAnnotation(type, CLibTypeDef.class);
    }

    protected boolean isCallbackType(TypeMirror type) {
        return AnnotatedCLibCallback.isCallbackType(toTypeElement(type));
    }

    protected boolean isCStructType(TypeMirror type) {
        return hasTypeAnnotation(type, CStruct.class);
    }

    protected boolean isCEnumType(TypeMirror type) {
        return hasTypeAnnotation(type, CEnum.class);
    }

    private boolean hasTypeAnnotation(TypeMirror mirror, Class<? extends Annotation> annotation) {
        return AnnotationUtil.hasTypeAnnotation(env, mirror, annotation);
    }

    protected <A extends Annotation> A getTypeAnnotation(TypeMirror mirror, Class<A> annotation) {
        return AnnotationUtil.getTypeAnnotation(env, mirror, annotation);
    }

    protected <A extends Annotation> boolean hasAnnotation(TypeElement type, Class<A> annotation) {
        return type != null && type.getAnnotationsByType(annotation) != null;
    }

    protected TypeElement toTypeElement(TypeMirror type) {
        return (TypeElement) env.getTypeUtils().asElement(type);
    }

    protected boolean isTypeOf(TypeElement type, Class<?> testedClass) {
        return type != null && Objects.equals(testedClass.getTypeName(), type.getQualifiedName().toString());
    }

    protected boolean isTypeOf(TypeMirror type, Class<?> testedClass) {
        return isTypeOf(toTypeElement(type), testedClass);
    }

    protected boolean isConst(TypeMirror mirror) {
        return mirror.toString().contains(CConst.class.getSimpleName());
    }

    protected AnnotatedCLibElement(T element, ProcessingEnvironment env) {
        this.element = element;
        this.env = env;
    }

    protected void debugPrint(String message) {
        // ERROR is displayed by default
        env.getMessager().printMessage(Diagnostic.Kind.ERROR, message);
    }

    @Override
    public ProcessingEnvironment getEnv() {
        return env;
    }

    final T element;

    final ProcessingEnvironment env;

}
