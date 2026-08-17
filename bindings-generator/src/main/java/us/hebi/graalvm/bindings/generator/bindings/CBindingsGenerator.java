package us.hebi.graalvm.bindings.generator.bindings;

import us.hebi.graalvm.bindings.generator.bindings.features.FeatureGenerator;
import us.hebi.graalvm.bindings.generator.generators.CEntryPointGenerator;
import us.hebi.graalvm.bindings.generator.generators.CEnumGenerator;
import us.hebi.graalvm.bindings.generator.generators.DocumentationUtil;
import us.hebi.graalvm.bindings.generator.generators.IndentingTextRenderer;
import us.hebi.graalvm.bindings.model.*;

import java.io.IOException;
import java.io.Writer;
import java.util.*;
import java.util.function.Function;

/**
 * @author Florian Enner
 * @since 07 Jan 2024
 */
public class CBindingsGenerator extends BindingGenerator {

    public CBindingsGenerator(LibraryDescription lib) {
        super(lib);
    }

    @Override
    public void writeTo(Filer filer) throws IOException {
        for (ClassDescription apiClass : lib.getClasses()) {
            writeClass(apiClass);
        }

        // Header for the shared library
        filer.createFile("include", lib.getName() + "_c.h", writer -> writeHeader(writer, false));

        // Additional sources for the static library
        filer.createFile("..", lib.getName() + "_c.h", writer -> writeHeader(writer, true));
        filer.createFile("..", lib.getName() + "_c.cpp", this::writeImplementation);
        filer.createFile("..", lib.getName() + "_objc.m", this::writeObjCImplementation);
        filer.createFile("..", lib.getName() + "_static.cmake", this::writeCMakeStatic);
        filer.createFile("..", "CMakeLists.txt", this::writeCMakeLists);
        filer.createFile("..", "mex.cmake", writer -> writer.write(loadContent("c/mex.cmake")));

        // Clean-room MEX SDK so the gateway builds without a MATLAB installation. The header
        // is written from the documented C API, the def files list the import symbols that
        // mex.cmake turns into import libraries on Windows.
        for (var file : List.of("mex.h", "libmx.def", "libmex.def")) {
            filer.createFile("../mex-sdk", file, writer -> writer.write(loadContent("c/mex-sdk/" + file)));
        }

        // find_package entry point for the prebuilt static package
        filer.createFile("..", lib.getName() + "-config.cmake", this::writeCMakeConfig);
        filer.createFile("..", lib.getName() + "-config-version.cmake", this::writeCMakeConfigVersion);

        // Wrappers around the platform C compiler that native-image is pointed at with
        // --native-compiler-path. They intercept the link step to additionally emit the static
        // library and the cmake fragments that the second stage needs. Emitted here so that a
        // consuming project does not have to vendor them. Loaded raw: the batch file is full of
        // '%%' variables that must not be treated as template tokens.
        for (var script : List.of("compiler-windows.bat", "compiler-linux.sh", "compiler-osx.sh")) {
            filer.createFile("..", script, writer -> writer.write(loadContent("c/" + script)));
        }
    }

    private static String getInternalQualifiedCType(TypeDescription type) {
        var name = switch (type.getDataType()) {
            case ENUM, CALLBACK, OBJECT_HANDLE -> TypeMap.toExternalC(type, true); // a bit of extra type safety
            default -> TypeMap.toInternalC(type.getDataType());
        };
        return type.getIsConst() ? "const " + name : name;
    }

    /**
     * The typedef a callback crosses the ABI as. A plain string because the MEX gateway declares
     * its own copy.
     */
    public static String getCCallbackTypedef(CallbackDescription callback) {
        return getCCallbackTypedef(callback, CBindingsGenerator::getQualifiedCType);
    }

    /**
     * @param types how the calling face spells a type, e.g. the MEX gateway's own struct copies
     */
    public static String getCCallbackTypedef(CallbackDescription callback, Function<TypeDescription, String> types) {
        var parameters = new ArrayList<String>();
        for (var parameter : callback.getParameters()) {
            parameters.add(getCallbackParameterType(parameter, types) + " " + parameter.getParamName());
        }
        return "typedef " + types.apply(callback.getReturnType()) + " (*" + callback.getCPrefix() + callback.getName() + ")("
                + String.join(", ", parameters) + ");";
    }

    /**
     * A struct view is a GraalVM word type, so a callback receives it by (const) pointer rather than
     * by value like the entry points do
     */
    public static String getCallbackParameterType(TypeDescription type, Function<TypeDescription, String> types) {
        return type.getDataType() == DataType.STRUCT ? "const " + types.apply(type) + "*" : types.apply(type);
    }

    public static String getQualifiedCType(TypeDescription type) {
        var name = TypeMap.toExternalC(type, true);
        return type.getIsConst() ? "const " + name : name;
    }

    /**
     * The exported functions of one class: declarations into the header, entry point
     * declarations plus the forwarding shims into the source.
     */
    private void writeClass(ClassDescription apiClass) {
        var type = apiClass.getType();
        if (apiClass.getInstantiable()) {
            typeDefs.addLine("typedef struct " + TypeMap.toExternalC(type, false) + "_* " + TypeMap.toExternalC(type, true) + ";");
        }
        header.addLine().addLine("// ==== " + type.getTypeName() + " ====");
        source.addLine().addLine("// ==== " + type.getTypeName() + " ====");

        String isolateName = apiClass.tryGetIsolateName().orElse("global");
        for (var method : apiClass.getMethods()) {
            var shim = new Shim(isolateName, method.getReturnType(), method.getCName(), isThrowing(method));
            if (!method.getIsStatic()) {
                shim.addParam(type, "obj");
            }
            for (var param : method.getParameters()) {
                shim.addParam(param, param.getParamName());
            }
            shim.write();
        }

        // additional methods
        if (apiClass.getInstantiable() && !apiClass.hasParent()) {
            var voidType = TypeDescription.newInstance().setDataType(DataType.VOID);
            new Shim(isolateName, voidType, apiClass.getCDestructorName(), false)
                    .addParam(type, "obj")
                    .write();
        }

        // To have a pure C library, we need to add casts to go from child to parent pointers
        apiClass.tryGetToParentCastMethod().ifPresent(this::writeParentCast);
    }

    private void writeParentCast(MethodDescription method) {
        var decl = header.snapshot();
        var def = source.snapshot();
        for (var out : List.of(decl, def)) {
            out.set("PARENT", getQualifiedCType(method.getReturnType()))
                    .set("NAME", method.getCName())
                    .set("CHILD", getQualifiedCType(method.getParameters().get(0)));
        }
        decl.addLine("%%DLL_PUBLIC%%%%PARENT%% %%NAME%%(%%CHILD%% ptr);");
        def.beginControlFlow("%%PARENT%% %%NAME%%(%%CHILD%% ptr) {")
                .addLine("return reinterpret_cast<%%PARENT%%>(ptr);")
                .endControlFlow("}");
    }

    /**
     * One exported function: the public signature for the header, and for the source the GraalVM
     * entry point it forwards to plus the shim itself. The entry point takes the isolate thread
     * first, and structs cross it by pointer where the public face passes them by value.
     */
    private final class Shim {

        Shim(String isolateName, TypeDescription returnType, String name, boolean throwing) {
            this.name = name;
            this.returnType = returnType;
            this.structReturn = isPassedByAddress(returnType.getDataType());
            internalParams.add(getIsolateThreadType() + "* isolate");
            args.add(ISOLATE_NAMESPACE + isolateName + "()");
            if (throwing) {
                String errorParam = getErrorType() + "* " + ERROR_PARAM_NAME;
                params.add(errorParam);
                internalParams.add(errorParam);
                args.add(ERROR_PARAM_NAME);
            }
        }

        Shim addParam(TypeDescription type, String name) {
            params.add(getQualifiedCType(type) + " " + name);
            internalParams.add(isPassedByAddress(type.getDataType())
                    ? "const " + TypeMap.toExternalC(type, false) + "* " + name
                    : getInternalQualifiedCType(type) + " " + name);
            args.add(switch (type.getDataType()) {
                case SIZE_T -> "static_cast<long long>(" + name + ")";
                // the public face says uint8_t*, the GraalVM entry point char*
                case UINT8_POINTER -> "reinterpret_cast<char*>(" + name + ")";
                // structs cross the entry point as a pointer to the by-value parameter
                case STRUCT -> "&" + name;
                default -> name;
            });
            return this;
        }

        void write() {
            // A by-value struct return gets filled through a trailing out pointer
            if (structReturn) {
                internalParams.add(TypeMap.toExternalC(returnType, false) + "* " + OUT_PARAM_NAME);
                args.add("&result_");
            }

            var decl = header.snapshot();
            var def = source.snapshot();
            for (var out : List.of(decl, def)) {
                out.set("RETURN_TYPE", getQualifiedCType(returnType))
                        .set("NAME", name)
                        .setArgs("PARAMS", params);
            }
            decl.addLine("%%DLL_PUBLIC%%%%RETURN_TYPE%% %%NAME%%(%%PARAMS%%);");

            def.set("INTERNAL_RETURN_TYPE", structReturn ? "void" : getInternalQualifiedCType(returnType))
                    .set("INTERNAL_NAME", getInternalName(name))
                    .setArgs("INTERNAL_PARAMS", internalParams)
                    .setArgs("ARGS", args);
            def.addLine("%%INTERNAL_RETURN_TYPE%% %%INTERNAL_NAME%%(%%INTERNAL_PARAMS%%);");
            def.beginControlFlow("%%RETURN_TYPE%% %%NAME%%(%%PARAMS%%) {");
            if (structReturn) {
                def.addLine("%%RETURN_TYPE%% result_;");
                def.addLine("%%INTERNAL_NAME%%(%%ARGS%%);");
                def.addLine("return result_;");
            } else {
                boolean isVoid = returnType.getDataType() == DataType.VOID;
                def.addLine((isVoid ? "" : "return ") + "%%INTERNAL_NAME%%(%%ARGS%%);");
            }
            def.endControlFlow("}");
        }

        /**
         * User structs cross the entry point as a pointer, both as a parameter
         * and as the trailing out-struct a by-value return is written through.
         */
        private static boolean isPassedByAddress(DataType type) {
            return type == DataType.STRUCT;
        }

        private static String getInternalName(String cEntryPointName) {
            return CEntryPointGenerator.INTERNAL_PREFIX + cEntryPointName;
        }

        private final String name;
        private final TypeDescription returnType;
        private final boolean structReturn;
        private final List<String> params = new ArrayList<>();
        private final List<String> internalParams = new ArrayList<>();
        private final List<String> args = new ArrayList<>();

    }

    private static void writeStructDefinition(IndentingTextRenderer out, StructDescription struct) {
        // Note: the names have no effect on the memory layout, so each language can use their matching convention
        out.addLine();
        struct.tryGetComment().ifPresent(comment -> out.add(DocumentationUtil.generateLineComments(comment)));
        out.beginControlFlow("typedef struct {");
        for (var field : struct.getFields()) {
            out.addLine(TypeMap.toStructFieldC(field.getDataType(), field.getIsConst()) + " " + field.getLowerUnderscoreName() + ";");
        }
        out.endControlFlow("} " + struct.getCName() + ";");
    }

    /**
     * The same declarations serve the shared library, which exports them, and the static
     * build, which does not.
     */
    private void writeHeader(Writer writer, boolean exportSymbols) throws IOException {
        final String DLL_DEFINES = !exportSymbols ? "" : """
                #if !defined(_WIN32) && (defined(__unix__) || defined(__unix) || (defined(__APPLE__) && defined(__MACH__)))
                # ifndef DLL_PUBLIC
                #  define DLL_PUBLIC __attribute__ ((visibility ("default")))
                # endif
                # ifndef DLL_LOCAL
                #  define DLL_LOCAL __attribute__ ((visibility ("hidden")))
                # endif
                #else
                # ifndef DLL_PUBLIC
                #  define DLL_PUBLIC __declspec( dllexport )
                # endif
                # ifndef DLL_LOCAL
                #  define DLL_LOCAL
                # endif
                #endif
                """;

        var content = new IndentingTextRenderer()
                .setIf("DLL_PUBLIC", exportSymbols, "DLL_PUBLIC ")
                .set("ERROR_TYPE", getErrorType());
        content.add("""
                // Error information for every method that can throw. A non-zero code
                // means the call failed and the return value can be ignored. Contents
                // are library owned and only valid until the next call on this thread.
                typedef struct {
                    const char* message;
                    const char* id;
                    int32_t code;
                } %%ERROR_TYPE%%;
                """);
        for (var struct : lib.getStructs()) {
            writeStructDefinition(content, struct);
        }

        // Callbacks come after structs as they may have struct inputs
        if (lib.getCallbacks().length() > 0) {
            content.addLine();
            for (var callback : lib.getCallbacks()) {
                content.addLine(getCCallbackTypedef(callback));
            }
        }
        content.addLine().add(typeDefs.render()).addLine();

        for (var enumDesc : lib.getEnums()) {
            content.add(CEnumGenerator.getCEnumDefinition(enumDesc)).addLine();
        }

        content.add(header.render());
        for (FeatureGenerator feature : features) {
            content.addLine().add(feature.getCHeader(false));
        }

        writer.write(loadTemplate("c/libName_c.h")
                .add("content", content.render())
                .add("defines", DLL_DEFINES)
                .render());
    }

    private void writeImplementation(Writer writer) throws IOException {
        var content = new IndentingTextRenderer().add(source.render());
        for (FeatureGenerator feature : features) {
            content.add(feature.getCSource());
        }

        // One accessor per named isolate, the global one lives in the template
        Set<String> isolateNames = new TreeSet<>();
        for (var aClass : lib.getClasses()) {
            aClass.tryGetIsolateName().ifPresent(isolateNames::add);
        }
        var isolates = new IndentingTextRenderer()
                .set("THREAD_TYPE", getIsolateThreadType())
                .set("ISOLATE_TYPE", getIsolateType());
        for (var isolateName : isolateNames) {
            isolates.set("NAME", isolateName).add("""
                    %%THREAD_TYPE%%* %%NAME%%() {
                        static %%ISOLATE_TYPE%%* isolate = createIsolate();
                        static thread_local ThreadHandle thread(isolate);
                        return thread.ptr;
                    }

                    """);
        }

        writer.write(loadTemplate("c/libName_c.cpp")
                .add("isolateApi", getIsolateApiDeclarations())
                .add("isolates", isolates.render().trim())
                .add("libName", lib.getName())
                .add("cPrefix", lib.getCPrefix())
                .add("content", content.render())
                .render());
    }

    private void writeObjCImplementation(Writer writer) throws IOException {
        for (FeatureGenerator feature : features) {
            writer.write(feature.getObjCSource());
        }
    }

    /**
     * Library-specific aliases for the GraalVM isolate C API. The image exports the
     * generic graal_* symbols as well, but the aliases are what the generated code
     * calls so that two generated libraries can be linked into the same binary.
     * (not part of the user-facing C ABI)
     */
    private String getIsolateApiDeclarations() {
        return new IndentingTextRenderer()
                .set("ISOLATE_TYPE", getIsolateType())
                .set("THREAD_TYPE", getIsolateThreadType())
                .set("PREFIX", lib.getCPrefix())
                .add("""
                        typedef struct %%PREFIX%%_Isolate_s %%ISOLATE_TYPE%%;
                        typedef struct %%PREFIX%%_IsolateThread_s %%THREAD_TYPE%%;

                        %%THREAD_TYPE%%* %%PREFIX%%_createIsolate();
                        %%ISOLATE_TYPE%%* %%PREFIX%%_getIsolate(%%THREAD_TYPE%%* thread);
                        %%THREAD_TYPE%%* %%PREFIX%%_attachThread(%%ISOLATE_TYPE%%* isolate);
                        %%THREAD_TYPE%%* %%PREFIX%%_getCurrentThread(%%ISOLATE_TYPE%%* isolate);
                        int %%PREFIX%%_detachThread(%%THREAD_TYPE%%* thread);
                        int %%PREFIX%%_tearDownIsolate(%%THREAD_TYPE%%* thread);""")
                .render();
    }

    private String getIsolateType() {
        return lib.getCPrefix() + "_Isolate";
    }

    private String getErrorType() {
        return lib.getCPrefix() + "_ErrorInfo";
    }

    private String getIsolateThreadType() {
        return lib.getCPrefix() + "_IsolateThread";
    }

    private void writeCMakeStatic(Writer writer) throws IOException {
        writer.write(loadTemplateWithLibVars("c/libName_static.cmake")
                .add("appleLibs", getExtraAppleLibs())
                .render());
    }

    private void writeCMakeLists(Writer writer) throws IOException {
        writer.write(loadTemplateWithLibVars("c/CMakeLists.txt")
                .add("appleLibs", getExtraAppleLibs())
                .add("matlab_namespace", MatlabBindingsGenerator.getNamespacePrefix(lib))
                .render());
    }

    private void writeCMakeConfig(Writer writer) throws IOException {
        writer.write(loadTemplateWithLibVars("c/libName-config.cmake").render());
    }

    private void writeCMakeConfigVersion(Writer writer) throws IOException {
        writer.write(loadTemplateWithLibVars("c/libName-config-version.cmake").render());
    }

    private String getExtraAppleLibs() {
        List<String> libs = new ArrayList<>();
        for (FeatureGenerator feature : features) {
            libs.addAll(feature.getAppleLibraries());
        }
        return String.join(",\n", libs);
    }

    private static final String ISOLATE_NAMESPACE = "isolate::";
    static final String ERROR_PARAM_NAME = "_error";
    static final String OUT_PARAM_NAME = "structOut";

    // Built up class by class, then spliced into the whole-file templates. The export
    // macro stays a token until the header decides whether it exports.
    private final IndentingTextRenderer typeDefs = new IndentingTextRenderer();
    private final IndentingTextRenderer header = new IndentingTextRenderer();
    private final IndentingTextRenderer source = new IndentingTextRenderer();

}
