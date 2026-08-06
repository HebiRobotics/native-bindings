package us.hebi.graalvm.bindings.generator.generators;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.MethodSpec;
import org.graalvm.word.WordFactory;
import us.hebi.graalvm.bindings.generator.bindings.StructMarshaller.AllocatingReturn;
import us.hebi.graalvm.bindings.model.DataType;

/**
 * @author Florian Enner
 * @since 06 Jan 2024
 */
class RuntimeClasses {

    static final ClassName HandlesUtil = ClassName.get("us.hebi.graalvm.bindings.runtime.handles", "Handles");

    public static final ClassName CTypes = ClassName.get("us.hebi.graalvm.bindings.runtime.types", "CTypes");

    public static final ClassName CLibError = ClassName.get("us.hebi.graalvm.bindings.runtime.types", "CLibError");

    public static final ClassName ErrorInfo = ClassName.get("us.hebi.graalvm.bindings.runtime.types", "ErrorInfo");

    public static final ClassName MockPointerBase = ClassName.get("us.hebi.graalvm.bindings.runtime.types", "MockPointerBase");

    /**
     * Name of the trailing out-struct param on entry points that return a marshalled struct
     */
    public static final String STRUCT_OUT = "_structOut";

    /**
     * Name of the local holding the Java return value that gets copied into the out-struct
     */
    private static final String RETURN_VALUE = "_values";

    // Name of the leading out-struct param on entry points that can throw
    public static final String ERROR_OUT_PARAM = "_error";

    /**
     * @param allocating marshaller for a return that gets copied into the trailing out-struct,
     *                   or null if the return value crosses the boundary directly
     * @param idPrefix   library name for the fallback error id, or null if there is no error struct
     * @param onError    additional error handling the return type alone cannot express, e.g. zeroing
     *                   the fields of a caller-provided out struct. May be empty.
     */
    public static void addGuardedInvocation(MethodSpec.Builder builder, CodeBlock invocation, DataType returnType,
                                            AllocatingReturn allocating, String idPrefix, CodeBlock onError) {

        if (idPrefix != null) {
            builder.addStatement("$T.clearStruct($N)", CLibError, ERROR_OUT_PARAM);
        }
        builder.beginControlFlow("try")
                .addCode(getInvocationCode(returnType, allocating, invocation))
                .nextControlFlow("catch (Throwable throwable)");
        if (idPrefix != null) {
            builder.addStatement("$T.fillStruct(throwable, $N, $S)", CLibError, ERROR_OUT_PARAM, idPrefix);
        } else {
            builder.addComment("Declared noexcept, so there is no error struct to report through");
        }
        builder.addCode(onError)
                .addCode(getOnErrorReturnCode(returnType, allocating))
                .endControlFlow();

    }

    private static CodeBlock getInvocationCode(DataType dataType, AllocatingReturn allocating, CodeBlock invocation) {
        CodeBlock.Builder code = CodeBlock.builder();
        if (allocating != null) {
            return code
                    .add("$T $N = ", allocating.javaType(), RETURN_VALUE).addStatement(invocation)
                    .add(allocating.fillOut(STRUCT_OUT, RETURN_VALUE))
                    .build();
        }
        switch (dataType) {
            case VOID -> code.addStatement(invocation);
            default -> code.add("return ").addStatement(invocation);
        }
        return code.build();
    }

    private static CodeBlock getOnErrorReturnCode(DataType dataType, AllocatingReturn allocating) {
        CodeBlock.Builder code = CodeBlock.builder();
        if (allocating != null) {
            return code.add(allocating.zeroOut(STRUCT_OUT)).build();
        }
        switch (dataType) {
            case BOOL -> code.addStatement("return false");
            case CHAR16, INT8, INT16, INT32, INT64, FLOAT32, FLOAT64, SIZE_T, ENUM -> code.addStatement("return 0");
            case VOID_POINTER, CHAR_POINTER, CHAR_POINTER_POINTER,
                 INT16_POINTER, INT32_POINTER, INT64_POINTER,
                 FLOAT32_POINTER, FLOAT64_POINTER,
                 OBJECT_HANDLE -> code.addStatement("return $T.nullPointer()", WordFactory.class);
            case VOID -> {
            }
            default -> code.addStatement("return null /* unsupported: cause native-image failure */");
        }
        return code.build();
    }

}
