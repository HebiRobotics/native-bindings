package us.hebi.graalvm.bindings.generator.generators;

import com.squareup.javapoet.*;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.ObjectHandle;
import org.graalvm.nativeimage.ObjectHandles;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.word.WordFactory;
import us.hebi.graalvm.bindings.generator.annotations.AnnotatedCLibClass;
import us.hebi.graalvm.bindings.model.DataType;

import static javax.lang.model.element.Modifier.*;
import static us.hebi.graalvm.bindings.generator.generators.RuntimeClasses.*;

/**
 * @author Florian Enner
 * @since 07 Jan 2024
 */
public class CEntryPointGenerator extends AbstractGenerator {

    @Override
    protected void process(AnnotatedCLibClass apiClass) {
        createSourceFile(generateJavaCode(apiClass));
    }

    JavaFile generateJavaCode(AnnotatedCLibClass apiClass) {

        // Generate simple class
        final TypeSpec.Builder cEntriesClass = TypeSpec.classBuilder(apiClass.getWrapperType())
                .addModifiers(PUBLIC, FINAL);

        // Create additional methods to release and cleanup provided instances
        if (apiClass.enableInstancing()) {
            generateInstanceMethods(apiClass, cEntriesClass);
        }

        // Add method wrappers
        for (var method : apiClass.getPublicApiMethods()) {
            final MethodSpec.Builder builder = createCEntryPoint(method.getCEntryPointName());

            CodeBlock.Builder invocation = CodeBlock.builder();
            CodeBlock.Builder invocationClose = CodeBlock.builder();

            // Handle conversions with return types
            var returnType = method.getReturnType();
            if (!returnType.isVoid()) {
                builder.returns(returnType.getCEntryType());

                // Wrap known Java types as handles if needed
                returnType.getApiTypeWrapperClass().ifPresent(wrapperClass -> {
                    invocation.add("$T.$N(", wrapperClass, getRegisterMethod);
                    invocationClose.add(")");
                });
                returnType.getGeneratedCEnumType().ifPresent(cEnumClass -> {
                    invocation.add("$T.$N(", cEnumClass, CEnumGenerator.fromJavaMethod);
                    invocationClose.add(")");
                });
                if (returnType.isString()) {
                    invocation.add("$T.toTempCharPointer(", CTypes);
                    invocationClose.add(")");
                }

            }

            // Instance methods pass themselves as the first argument
            if (method.isStatic()) {
                invocation.add("$T.$N(", apiClass.getJavaType(), method.getMethodName());
                invocationClose.add(")");
            } else {
                builder.addParameter(ObjectHandle.class, "_obj");
                invocation.add("$N(_obj).$N(", getInstanceMethod, method.getMethodName());
                invocationClose.add(")");
            }

            // Add all additional arguments
            int i = 0;
            for (var param : method.getParameters()) {
                if (i++ > 0) invocation.add(", ");
                if (param.isIsolateThread()) {
                    // Pass the existing isolate parameter
                    invocation.add("$N", ISOLATE_NAME);
                } else {
                    builder.addParameter(param.getCEntryType(), param.getName());
                    var apiTypeWrapper = param.getApiTypeWrapperClass();
                    var generatedCEnum = param.getGeneratedCEnumType();
                    if (apiTypeWrapper.isPresent()) {
                        // convert handle to java object
                        invocation.add("$T.$N($N)", apiTypeWrapper.get(), getInstanceMethod, param.getName());
                    } else if (generatedCEnum.isPresent()) {
                        // convert generated wrapper to Java enum
                        invocation.add("$T.$N($N)", generatedCEnum.get(), CEnumGenerator.toJavaMethod, param.getName());
                    } else if (param.isString()) {
                        // convert from char*
                        invocation.add("$T.utf8ToJavaString($N)", RuntimeClasses.CTypes, param.getName());
                    } else if (param.isSizeType()) {
                        if (param.getJavaType() == TypeName.LONG) {
                            invocation.add("$T.capSizeToLong($N)", RuntimeClasses.CTypes, param.getName());
                        } else {
                            invocation.add("$T.capSizeToInt($N)", RuntimeClasses.CTypes, param.getName());
                        }
                    } else {
                        // forward as is
                        invocation.add("$N", param.getName());
                    }
                }
            }

            // Add guards
            DataType throwingReturnType = method.getReturnType().getDataType();
            if (throwingReturnType == DataType.VOID && method.isThrowing()) {
                throwingReturnType = DataType.VOID_WITH_ERROR_CODE;
                builder.returns(TypeName.INT);
            }
            addGuardedInvocation(
                    builder,
                    invocation.add(invocationClose.build()).build(),
                    throwingReturnType
            );
            cEntriesClass.addMethod(builder.build());

        }

        // Build class
        return JavaFile.builder(apiClass.getWrapperType().packageName(), cEntriesClass.build())
                .indent("    ")
                .skipJavaLangImports(true)
                .build();

    }

    private static MethodSpec.Builder createCEntryPoint(String entryPointName) {
        return MethodSpec.methodBuilder(entryPointName)
                .addAnnotation(AnnotationSpec.builder(CEntryPoint.class)
                        .addMember("name", "$S", INTERNAL_PREFIX + entryPointName)
                        .build())
                .addModifiers(PUBLIC, STATIC)
                .addParameter(IsolateThread.class, ISOLATE_NAME);
    }

    private static void generateInstanceMethods(AnnotatedCLibClass apiClass, TypeSpec.Builder cEntriesClass) {
        final String handlesFieldName = "INSTANCES";

        // We create a new instances field for each type, but for inherited classes we have to share a root-level one
        var root = apiClass.getRoot();
        if (root.isPresent()) {
            cEntriesClass.addField(FieldSpec.builder(ObjectHandles.class, handlesFieldName, PUBLIC, STATIC, FINAL)
                    .initializer("$T.$N", root.get().getApiTypeWrapperClass().get(), handlesFieldName)
                    .build());
        } else {
            cEntriesClass.addField(FieldSpec.builder(ObjectHandles.class, handlesFieldName, PUBLIC, STATIC, FINAL)
                    .initializer("$T.create()", ObjectHandles.class)
                    .build());
        }
        cEntriesClass.addMethod(MethodSpec.methodBuilder(getRegisterMethod)
                .addModifiers(PUBLIC, STATIC)
                .returns(ObjectHandle.class)
                .addParameter(apiClass.getJavaType(), "instance", FINAL)
                .addStatement("return $T.create($N, instance)", HandlesUtil, handlesFieldName)
                .build());
        cEntriesClass.addMethod(MethodSpec.methodBuilder(getInstanceMethod)
                .addModifiers(PUBLIC, STATIC)
                .returns(apiClass.getJavaType())
                .addParameter(ObjectHandle.class, "handle", FINAL)
                .addStatement("return $T.get($N, handle)", HandlesUtil, handlesFieldName)
                .build());
        if (!apiClass.getParent().isPresent()) {
            cEntriesClass.addMethod(createCEntryPoint(apiClass.getDestructorCEntryName())
                    .addParameter(ObjectHandle.class, "handle", FINAL)
                    .addStatement("$T.destroy($N, handle)", HandlesUtil, handlesFieldName)
                    .build());
        }
    }

    // Info about the annotated class
    private final static String getRegisterMethod = "_registerInstance";
    private final static String getInstanceMethod = "_getInstance";
    private static final String ISOLATE_NAME = "_isolate";

    public static final String INTERNAL_PREFIX = "__";

}
