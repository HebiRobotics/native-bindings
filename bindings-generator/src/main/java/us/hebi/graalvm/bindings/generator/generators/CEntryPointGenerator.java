package us.hebi.graalvm.bindings.generator.generators;

import com.squareup.javapoet.*;
import org.graalvm.nativeimage.Isolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.ObjectHandle;
import org.graalvm.nativeimage.ObjectHandles;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.word.WordFactory;
import us.hebi.graalvm.bindings.generator.annotations.AnnotatedCLibClass;
import us.hebi.graalvm.bindings.generator.annotations.AnnotatedCLibMethod;
import us.hebi.graalvm.bindings.generator.bindings.TypeMap;

import java.util.TreeSet;

import static javax.lang.model.element.Modifier.*;
import static us.hebi.graalvm.bindings.generator.generators.RuntimeClasses.*;

/**
 * @author Florian Enner
 * @since 07 Jan 2024
 */
public class CEntryPointGenerator extends AbstractGenerator {

    @Override
    protected void process(AnnotatedCLibClass apiClass) {
        String packageName = apiClass.getWrapperType().packageName();
        wrapperPackages.add(packageName);
        createSourceFile(packageName, generateEntryPointWrapper(apiClass));
    }

    @Override
    public void finish() {
        // Use the first package (sorted for determinism) for global isolate methods
        if (!hasProcessedAny() || wrapperPackages.isEmpty()) return;
        createSourceFile(wrapperPackages.first(), generateIsolateApi());
    }

    /**
     * Prefixed aliases for the GraalVM isolate C API. A static image exports the
     * generic graal_* symbols, so two generated libraries in one binary would
     * collide on duplicate names.
     */
    TypeSpec generateIsolateApi() {
        final String prefix = getOptions().getCPrefix();
        final TypeSpec.Builder isolateClass = TypeSpec.classBuilder(ISOLATE_API_CLASS_NAME)
                .addModifiers(PUBLIC, FINAL);
        isolateClass.addMethod(createBuiltinEntryPoint(prefix, "createIsolate", CEntryPoint.Builtin.CREATE_ISOLATE)
                .returns(IsolateThread.class)
                .build());
        isolateClass.addMethod(createBuiltinEntryPoint(prefix, "getIsolate", CEntryPoint.Builtin.GET_ISOLATE)
                .returns(Isolate.class)
                .addParameter(IsolateThread.class, "thread")
                .build());
        isolateClass.addMethod(createBuiltinEntryPoint(prefix, "attachThread", CEntryPoint.Builtin.ATTACH_THREAD)
                .returns(IsolateThread.class)
                .addParameter(Isolate.class, "isolate")
                .build());
        isolateClass.addMethod(createBuiltinEntryPoint(prefix, "getCurrentThread", CEntryPoint.Builtin.GET_CURRENT_THREAD)
                .returns(IsolateThread.class)
                .addParameter(Isolate.class, "isolate")
                .build());
        isolateClass.addMethod(createBuiltinEntryPoint(prefix, "detachThread", CEntryPoint.Builtin.DETACH_THREAD)
                .returns(TypeName.INT)
                .addParameter(IsolateThread.class, "thread")
                .build());
        isolateClass.addMethod(createBuiltinEntryPoint(prefix, "tearDownIsolate", CEntryPoint.Builtin.TEAR_DOWN_ISOLATE)
                .returns(TypeName.INT)
                .addParameter(IsolateThread.class, "thread")
                .build());
        return isolateClass.build();
    }

    private static MethodSpec.Builder createBuiltinEntryPoint(String prefix, String methodName, CEntryPoint.Builtin builtin) {
        return MethodSpec.methodBuilder(methodName)
                .addAnnotation(AnnotationSpec.builder(CEntryPoint.class)
                        .addMember("name", "$S", AnnotatedCLibClass.getCEntryName(true, prefix, "", methodName))
                        .addMember("builtin", "$T.$L", CEntryPoint.Builtin.class, builtin.name())
                        .build())
                .addModifiers(PUBLIC, STATIC, NATIVE);
    }

    TypeSpec generateEntryPointWrapper(AnnotatedCLibClass apiClass) {

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

            // Anything that can throw reports through a caller-provided struct. It goes ahead
            // of the instance handle so that it is argument zero of every public C signature
            if (method.isThrowing()) {
                builder.addParameter(ErrorInfo, ERROR_OUT_PARAM);
            }

            CodeBlock.Builder invocation = CodeBlock.builder();
            CodeBlock.Builder invocationClose = CodeBlock.builder();

            // Handle conversions with return types. A marshalled return stays void: the
            // entry point fills a trailing out-struct instead (see below).
            var returnType = method.getReturnType();
            var allocatingReturn = returnType.getAllocatingReturn();
            if (!returnType.isVoid() && allocatingReturn.isEmpty()) {
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
                invocation.add("$T.$N(", apiClass.getJavaType(), method.getJavaMethodName());
                invocationClose.add(")");
            } else {
                builder.addParameter(ObjectHandle.class, "_obj");
                invocation.add("$N(_obj).$N(", getInstanceMethod, method.getJavaMethodName());
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

            // A marshalled return fills a trailing caller-provided struct
            if (allocatingReturn.isPresent()) {
                builder.addParameter(returnType.getStructViewType().get(), STRUCT_OUT);
            }

            // Add guards
            addGuardedInvocation(
                    builder,
                    invocation.add(invocationClose.build()).build(),
                    method.getReturnType().getDataType(),
                    allocatingReturn.orElse(null),
                    method.isThrowing() ? getErrorIdPrefix() : null,
                    getOutStructZeroing(method)
            );
            cEntriesClass.addMethod(builder.build());

        }

        // Build class
        return cEntriesClass.build();

    }

    /**
     * A throw leaves the out struct as the caller passed it in, so every field gets zeroed to
     * make the failure contents deterministic. The switches in {@link RuntimeClasses} are keyed
     * on the data type alone and cannot express per-field writes, hence the block is built here.
     */
    private static CodeBlock getOutStructZeroing(AnnotatedCLibMethod method) {
        var code = CodeBlock.builder();
        if (method.getOutStruct().isEmpty()) {
            return code.build();
        }
        var outName = method.getOutParameter().get().getName();
        for (var accessor : method.getOutStruct().get().getAccessors()) {
            if (accessor.isGetter()) continue;
            // A word type is never assigned null, hence the factory call
            if (TypeMap.isPointerType(TypeMap.getGraalDataType(accessor.getJavaType()))) {
                code.addStatement("$N.$N($T.nullPointer())", outName, accessor.getMethodName(), WordFactory.class);
            } else {
                code.addStatement("$N.$N($L)", outName, accessor.getMethodName(),
                        TypeName.BOOLEAN.equals(accessor.getJavaType()) ? "false" : "0");
            }
        }
        return code.build();
    }

    /**
     * The C prefix carries a trailing underscore, which would make the id read as {@code lib_:Name}.
     */
    private String getErrorIdPrefix() {
        String prefix = getOptions().getCPrefix();
        return prefix.endsWith("_") ? prefix.substring(0, prefix.length() - 1) : prefix;
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

    private final TreeSet<String> wrapperPackages = new TreeSet<>();

    // Info about the annotated class
    private final static String getRegisterMethod = "_registerInstance";
    private final static String getInstanceMethod = "_getInstance";
    private static final String ISOLATE_NAME = "_isolate";
    private static final String ISOLATE_API_CLASS_NAME = "_Isolates$CEntries";

    public static final String INTERNAL_PREFIX = "__";

}
