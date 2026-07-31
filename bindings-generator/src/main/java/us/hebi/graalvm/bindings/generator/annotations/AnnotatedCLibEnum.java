package us.hebi.graalvm.bindings.generator.annotations;

import com.squareup.javapoet.ClassName;
import us.hebi.graalvm.bindings.annotations.CLibEnum;
import us.hebi.graalvm.bindings.model.EnumDescription;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import java.util.List;

import static com.google.common.base.Preconditions.*;

/**
 * Annotated enum that needs to be generated as a C header and add matching directives.
 *
 * @author Florian Enner
 * @since 30 Jän 2025
 */
public class AnnotatedCLibEnum extends AnnotatedCLibElement<TypeElement> {

    public static boolean isNativeEnum(Element element) {
        return element.getKind() == ElementKind.ENUM && element.getAnnotation(CLibEnum.class) != null;
    }

    public AnnotatedCLibEnum(Element element, ProcessingEnvironment processingEnvironment, AnnotatedCLibClass parent) {
        super(AnnotationUtil.castToEnumElement(element), processingEnvironment);
        annotation = checkNotNull(element.getAnnotation(CLibEnum.class), "Enum must contain a @CGeneratedEnum annotation");
    }

    public EnumDescription toDescription() {
        var desc = EnumDescription.newInstance()
                .setName(getName())
                .setCPrefix(getCPrefix())
                .setCName(getCName(getOptions(), annotation));

        int i = 0;
        for (EnumValue value : getValues()) {
            desc.getMutableValues().next()
                    .setName(value.getName())
                    .setCName(desc.getCName() + "_" + value.getName())
                    .setCValue(i++);
        }
        return desc;
    }

    public static String getCName(EnvProcessor.GeneratorOptions options, CLibEnum annotation) {
        return AnnotationUtil.getCPrefix(options, annotation.prefix()) + "_" + annotation.value();
    }

    public static String getCPrefix(EnvProcessor.GeneratorOptions options, CLibEnum annotation) {
        return AnnotationUtil.getCPrefix(options, annotation.prefix()) + "_";
    }

    public String getCPrefix() {
        return AnnotationUtil.getCPrefix(getOptions(), annotation.prefix());
    }

    private String getName() {
        return annotation.value();
    }

    public ClassName getClassName() {
        return ClassName.get(element);
    }

    public List<EnumValue> getValues() {
        return getValues(element);
    }

    public static List<EnumValue> getValues(ClassName sourceEnum, ProcessingEnvironment env) {
        return getValues(AnnotationUtil.getTypeElement(env, sourceEnum));
    }

    public static List<EnumValue> getValues(Element element) {
        return element.getEnclosedElements().stream()
                .filter(e -> e.getKind() == ElementKind.ENUM_CONSTANT)
                .map(e -> (VariableElement) e)
                .map(EnumValue::new)
                .toList();
    }

    public static class EnumValue {

        public EnumValue(VariableElement element) {
            this.element = element;
        }

        public String getName() {
            return element.getSimpleName().toString();
        }

        final VariableElement element;
    }

    final CLibEnum annotation;

}
