package us.hebi.graalvm.bindings.generator.generators;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.MethodSpec;
import org.graalvm.word.WordFactory;
import us.hebi.graalvm.bindings.model.DataType;

/**
 * @author Florian Enner
 * @since 06 Jan 2024
 */
class RuntimeClasses {

    static final ClassName HandlesUtil = ClassName.get("us.hebi.graalvm.bindings.runtime.handles", "Handles");

    static final ClassName ExceptionHandlers = ClassName.get("us.hebi.graalvm.bindings.runtime.exceptions", "ExceptionHandlers");

    public static final ClassName CTypes = ClassName.get("us.hebi.graalvm.bindings.runtime.types", "CTypes");

    public static final ClassName DoubleSpan = ClassName.get("us.hebi.graalvm.bindings.runtime.types", "DoubleSpan");

    /**
     * Name of the trailing out-struct param on entry points that return a span
     */
    public static final String SPAN_OUT = "_spanOut";

    public static void addGuardedInvocation(MethodSpec.Builder builder, CodeBlock invocation, DataType returnType) {

        builder.beginControlFlow("try")
                .addCode(getInvocationCode(returnType, invocation))
                .nextControlFlow("catch (Throwable throwable)")
                .addStatement("$T.setLastError(throwable)", ExceptionHandlers)
                .addCode(getOnErrorReturnCode(returnType))
                .endControlFlow();

    }

    private static CodeBlock getInvocationCode(DataType dataType, CodeBlock invocation) {
        CodeBlock.Builder code = CodeBlock.builder();
        switch (dataType) {
            case VOID -> code.addStatement(invocation);
            case VOID_WITH_ERROR_CODE -> code
                    .addStatement(invocation)
                    .addStatement("return $T.STATUS_CODE_SUCCESS", ExceptionHandlers);
            // The array gets copied into a per-thread scratch buffer that stays valid
            // until the thread's next call, same as the string returns
            case FLOAT64_SPAN -> code
                    .add("double[] _values = ").addStatement(invocation)
                    .addStatement("$N.setData($T.toTempDoublePointer(_values))", SPAN_OUT, CTypes)
                    .addStatement("$N.setLength(_values == null ? 0 : _values.length)", SPAN_OUT);
            default -> code.add("return ").addStatement(invocation);
        }
        return code.build();
    }

    private static CodeBlock getOnErrorReturnCode(DataType dataType) {
        CodeBlock.Builder code = CodeBlock.builder();
        switch (dataType) {
            case BOOL -> code.addStatement("return false");
            case CHAR16, INT8, INT16, INT32, INT64, FLOAT32, FLOAT64, SIZE_T, ENUM -> code.addStatement("return 0");
            case VOID_POINTER, CHAR_POINTER, CHAR_POINTER_POINTER,
                 INT16_POINTER, INT32_POINTER, INT64_POINTER,
                 FLOAT32_POINTER, FLOAT64_POINTER,
                 OBJECT_HANDLE -> code.addStatement("return $T.nullPointer()", WordFactory.class);
            case FLOAT64_SPAN -> code
                    .addStatement("$N.setData($T.nullPointer())", SPAN_OUT, WordFactory.class)
                    .addStatement("$N.setLength(0)", SPAN_OUT);
            case VOID_WITH_ERROR_CODE ->
                    code.addStatement("return $T.returnErrorStatusCode(throwable)", ExceptionHandlers);
            case VOID -> {
            }
            default -> code.addStatement("return null /* unsupported: cause native-image failure */");
        }
        return code.build();
    }

}
