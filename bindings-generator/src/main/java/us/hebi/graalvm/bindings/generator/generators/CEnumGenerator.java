package us.hebi.graalvm.bindings.generator.generators;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import us.hebi.graalvm.bindings.generator.annotations.AnnotatedCLibClass;
import us.hebi.graalvm.bindings.generator.annotations.AnnotatedCLibEnum;
import us.hebi.graalvm.bindings.model.EnumDescription;
import us.hebi.graalvm.bindings.model.EnumValueDescription;

import javax.lang.model.element.Modifier;
import java.util.function.Function;

/**
 * @author Florian Enner
 * @since 04 Feb 2025
 */
public class CEnumGenerator extends AbstractGenerator {

    public static ClassName getCEnumWrapperClassName(TypeName enumType) {
        var className = (ClassName) enumType; // TODO: what do we do if it's not a ClassName?
        return getGeneratedClassName(className, "$$CEnum");
    }

    private static ClassName getDirectivesClassName(ClassName enumType) {
        return getGeneratedClassName(enumType, "$$Directives");
    }

    private static ClassName getGeneratedClassName(ClassName enumType, String suffix) {
        var packageName = enumType.packageName() + ".generated";
        var simpleName = String.join("_", enumType.simpleNames()) + suffix;
        return ClassName.get(packageName, simpleName);
    }

    @Override
    protected void process(AnnotatedCLibClass apiClass) {
        for (AnnotatedCLibEnum nestedEnum : apiClass.getNestedEnums()) {
            process(nestedEnum.getClassName(), nestedEnum.toDescription());
        }
    }

    protected void process(ClassName sourceEnum, EnumDescription desc) {
        var values = AnnotatedCLibEnum.getValues(sourceEnum, env).stream()
                .map(AnnotatedCLibEnum.EnumValue::getName)
                .toList();

        // Java wrapper
        var targetEnumClass = getCEnumWrapperClassName(sourceEnum);
        var cEnumClass = generateJavaCEnumWrapper(sourceEnum, targetEnumClass, desc);
        createSourceFile(targetEnumClass.packageName(), cEnumClass);

    }

    private static TypeSpec generateJavaCEnumWrapper(ClassName javaEnum,
                                                     ClassName wrapperClass,
                                                     EnumDescription desc) {

        var wrapper = TypeSpec.classBuilder(wrapperClass.simpleName())
                .addModifiers(Modifier.PUBLIC);

        // Java mapping methods to convert to Java enum
        MethodSpec.Builder javaToInt = MethodSpec.methodBuilder(fromJavaMethod)
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(int.class)
                .addParameter(javaEnum, "value")
                .beginControlFlow("return switch (value)");

        MethodSpec.Builder intToJava = MethodSpec.methodBuilder(toJavaMethod)
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(javaEnum)
                .addParameter(int.class, "value")
                .beginControlFlow("return switch (value)");

        // Adding cases
        for (var value : desc.getValues()) {
            javaToInt.addStatement("case $N -> $L", value.getName(), value.getCValue());
            intToJava.addStatement("case $L -> $T.$N", value.getCValue(), javaEnum, value.getName());
        }
        intToJava.addStatement("default -> throw new IllegalArgumentException(\"Unknown enum value: \" + value)");

        // Finalize
        wrapper.addMethod(javaToInt.endControlFlow("").build()); // adds a ' ;' at the end
        wrapper.addMethod(intToJava.endControlFlow("").build());

        return wrapper.build();
    }

    public static String getCEnumDefinition(EnumDescription desc) {
        var out = new IndentingTextRenderer().set("NAME", desc.getCName());
        out.beginControlFlow("typedef enum %%NAME%% {");
        addValues(out, desc, EnumValueDescription::getCName);
        out.endControlFlow("} %%NAME%%;");
        out.add("""
                #if defined(_MSC_VER) || defined(__cplusplus)
                static_assert(sizeof(%%NAME%%) == 4, "error: unexpected size for %%NAME%% enum");
                #else
                _Static_assert(sizeof(%%NAME%%) == 4, "error: unexpected size for %%NAME%% enum");
                #endif
                """);
        return out.render();
    }

    public static String getCppEnumDefinition(EnumDescription desc) {
        var out = new IndentingTextRenderer().setIndentString("  ");
        out.beginControlFlow("enum class " + desc.getName() + " : int32_t {");
        addValues(out, desc, EnumValueDescription::getName);
        return out.endControlFlow("};").render();
    }

    private static void addValues(IndentingTextRenderer out, EnumDescription desc, Function<EnumValueDescription, String> name) {
        var values = desc.getValues();
        for (int i = 0; i < values.length(); i++) {
            String separator = i < values.length() - 1 ? "," : "";
            out.addLine(name.apply(values.get(i)) + " = " + values.get(i).getCValue() + separator);
        }
    }

    public static final String toJavaMethod = "toJava";
    public static final String fromJavaMethod = "fromJava";

}
