package us.hebi.graalvm.bindings.generator.bindings;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import us.hebi.graalvm.bindings.generator.bindings.features.FeatureGenerator;
import us.hebi.graalvm.bindings.generator.generators.CEntryPointGenerator;
import us.hebi.graalvm.bindings.generator.generators.CEnumGenerator;
import us.hebi.graalvm.bindings.generator.generators.GeneratorUtil;
import us.hebi.graalvm.bindings.generator.generators.IndentingTextRenderer;
import us.hebi.graalvm.bindings.model.*;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.*;
import java.util.AbstractMap.SimpleEntry;

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
        // Process all classes
        for (ClassDescription apiClass : lib.getClasses()) {
            process(apiClass);
        }
        writeLastErrorAccessors();

        // Header for the shared library
        filer.createFile("include", lib.getName() + "_c.h", writer -> writeHeader(writer, false));

        // Additional sources for the static library
        filer.createFile("..", lib.getName() + "_c.h", writer -> writeHeader(writer, true));
        filer.createFile("..", lib.getName() + "_c.cpp", this::writeImplementation);
        filer.createFile("..", lib.getName() + "_objc.m", writer -> writer.write(mFile.toString()));
        filer.createFile("..", lib.getName() + "_static.cmake", this::writeCMakeStatic);
        filer.createFile("..", "CMakeLists.txt", this::writeCMakeLists);
        filer.createFile("..", "mex.cmake", writer -> writer.write(loadContent("c/mex.cmake")));

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
            case ENUM, TYPEDEF, OBJECT_HANDLE -> TypeMap.toExternalC(type, true); // a bit of extra type safety
            default -> TypeMap.toInternalC(type.getDataType());
        };
        return type.getIsConst() ? "const " + name : name;
    }

    public static String getQualifiedCType(TypeDescription type) {
        var name = TypeMap.toExternalC(type, true);
        return type.getIsConst() ? "const " + name : name;
    }

    protected void process(ClassDescription apiClass) {
        // Type definition, e.g., typedef struct HebiCommand_* HebiCommandPtr;
        var type = apiClass.getType();
        if (apiClass.getInstantiable()) {
            typeDefs.append(String.format("typedef struct %s_* %s;\n",
                    TypeMap.toExternalC(type, false),
                    TypeMap.toExternalC(type, true)
            ));
        }

        // Define the span struct once, before the first method that uses it
        if (!spanTypedefWritten) {
            GeneratorUtil.findSpanType(apiClass).ifPresent(spanType -> {
                typeDefs.append(IndentingTextRenderer.of("""

                                // A (pointer, length) view of a contiguous double buffer, passed and returned
                                // by value. Inputs are caller-owned and only read during the call. Returned
                                // spans are library-owned and stay valid until the calling thread's next
                                // library call, so they have to be copied immediately.
                                typedef struct {
                                    const double* data;
                                    size_t length;
                                } %%SPAN_TYPE%%;
                                """)
                        .set("SPAN_TYPE", TypeMap.toExternalC(spanType, false))
                        .render());
                spanTypedefWritten = true;
            });
        }

        header.append("\n// ==== ").append(type.getTypeName()).append(" ====\n");
        cFile.append("\n// ==== ").append(type.getTypeName()).append(" ====\n");
        SignatureBuilder func = new SignatureBuilder(getIsolateThreadType());
        String isolateName = apiClass.tryGetIsolateName().orElse("global");

        // Function signatures, e.g.,
        // HebiStatusCode hebiRobotModelSetBaseFrame(HebiRobotModelPtr robot_model, const double* transform, HebiMatrixOrdering ordering);
        for (var method : apiClass.getMethods()) {
            func.init(isolateName, method.getReturnType(), method.getCName());

            // instance reference
            if (!method.getIsStatic()) {
                func.addParam(type, "obj");
            }

            // arguments
            for (var param : method.getParameters()) {
                // isolates are already added as the first argument
                func.addParam(param, param.getParamName());
            }

            // finish
            func.writeTo(header, cFile);

        }

        // additional methods
        if (apiClass.getInstantiable() && !apiClass.hasParent()) {
            func.init(isolateName, TypeDescription.newInstance().setDataType(DataType.VOID), apiClass.getCDestructorName())
                    .addParam(type, "obj")
                    .writeTo(header, cFile);
        }

        // To have a pure C library, we need to add casts to go from child to parent pointers
        apiClass.tryGetToParentCastMethod().ifPresent(method -> addParentCastMethod(method, header, cFile));

    }

    /**
     * Per-isolate accessors for the calling thread's last error message. The
     * entry point is emitted once for the whole library (the error slot lives in
     * the isolate's image heap), so each isolate only needs a wrapper that binds
     * it to its own isolate thread.
     */
    private void writeLastErrorAccessors() {
        header.append("\n// ==== Last Error ====\n");
        cFile.append("\n// ==== Last Error ====\n");
        String internalName = SignatureBuilder.getInternalName(GeneratorUtil.getLastErrorCName(lib, ""));
        cFile.append("const char* ").append(internalName)
                .append("(").append(getIsolateThreadType()).append("* isolate);\n");
        for (String isolateName : GeneratorUtil.getIsolateNames(lib)) {
            String cName = GeneratorUtil.getLastErrorCName(lib, isolateName);
            String isolateFunc = isolateName.isEmpty() ? "global" : isolateName;
            header.append(DLL_PUBLIC).append("const char* ").append(cName).append("();\n");
            cFile.append("const char* ").append(cName).append("() {\n")
                    .append("    return ").append(internalName)
                    .append("(").append(isolateNamespace).append(isolateFunc).append("());\n")
                    .append("}\n");
        }
    }

    public static void addParentCastMethod(MethodDescription method, StringBuilder header, StringBuilder cFile) {
        Function<String, String> replace = input -> IndentingTextRenderer.of(input)
                .set("DLL_PUBLIC", DLL_PUBLIC)
                .set("PARENT", getQualifiedCType(method.getReturnType()))
                .set("NAME", method.getCName())
                .set("CHILD", getQualifiedCType(method.getParameters().get(0)))
                .render();
        header.append(replace.apply("""
                %%DLL_PUBLIC%%%%PARENT%% %%NAME%%(%%CHILD%% ptr);
                """));
        if (cFile != null) {
            cFile.append(replace.apply("""
                    %%PARENT%% %%NAME%%(%%CHILD%% ptr) {
                      return reinterpret_cast<%%PARENT%%>(ptr);
                    }
                    """));
        }
    }

    private static final String DLL_PUBLIC = "DLL_PUBLIC ";

    private void writeHeader(Writer fileWriter, boolean exportSymbols) throws IOException {
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

        StringWriter writer = new StringWriter();

        // Typedefs
        for (var typedef : lib.getTypedefs()) {
            writer.write(typedef.getCDefinition());
            writer.write("\n");
        }
        writer.write(typeDefs.toString());
        writer.write("\n");

        // Enum defs
        for (var enumDesc : lib.getEnums()) {
            writer.write(CEnumGenerator.getCEnumDefinition(enumDesc));
            writer.write("\n");
        }

        // Content
        writer.write(header.toString());
        for (FeatureGenerator feature : features) {
            writer.write("\n");
            writer.write(feature.getCHeader(false));
        }

        // Fix exports
        var content = writer.toString();
        if (!exportSymbols) {
            content = content.replaceAll(DLL_PUBLIC, "");
        }

        fileWriter.write(loadTemplate("c/libName_c.h")
                .add("content", content)
                .add("defines", DLL_DEFINES)
                .render());
    }

    private void writeImplementation(Writer fileWriter) throws IOException {
        var writer = new StringWriter();

        writer.write(cFile.toString());
        for (FeatureGenerator feature : features) {
            writer.write(feature.getCSource());
        }

        StringBuilder isolates = new StringBuilder();
        Set<String> isolateNames = new TreeSet<>();
        for (var aClass : lib.getClasses()) {
            aClass.tryGetIsolateName().ifPresent(isolateNames::add);
        }
        for (var isolateName : isolateNames) {
            isolates.append(createIsolateCode(isolateName));
        }

        fileWriter.write(loadTemplate("c/libName_c.cpp")
                .add("isolateApi", getIsolateApiDeclarations())
                .add("isolates", isolates.toString().trim())
                .add("libName", lib.getName())
                .add("cPrefix", lib.getCPrefix())
                .add("content", writer.toString())
                .render());

        for (FeatureGenerator feature : features) {
            mFile.append(feature.getObjCSource());
        }

    }

    private String createIsolateCode(String functionName) {
        return IndentingTextRenderer.of("""
                        %%THREAD_TYPE%%* %%NAME%%() {
                             static %%ISOLATE_TYPE%%* isolate = createIsolate();
                             static thread_local ThreadHandle thread(isolate);
                             return thread.ptr;
                        }
                        """)
                .set("THREAD_TYPE", getIsolateThreadType())
                .set("ISOLATE_TYPE", getIsolateType())
                .set("NAME", functionName)
                .render();
    }

    /**
     * Library-specific aliases for the GraalVM isolate C API. The image exports the
     * generic graal_* symbols as well, but the aliases are what the generated code
     * calls so that two generated libraries can be linked into the same binary.
     * (not part of the user-facing C ABI)
     */
    private String getIsolateApiDeclarations() {
        return IndentingTextRenderer.of("""
                        typedef struct %%PREFIX%%_isolate_s %%ISOLATE_TYPE%%;
                        typedef struct %%PREFIX%%_isolatethread_s %%THREAD_TYPE%%;

                        %%THREAD_TYPE%%* %%PREFIX%%_createIsolate();
                        %%ISOLATE_TYPE%%* %%PREFIX%%_getIsolate(%%THREAD_TYPE%%* thread);
                        %%THREAD_TYPE%%* %%PREFIX%%_attachThread(%%ISOLATE_TYPE%%* isolate);
                        %%THREAD_TYPE%%* %%PREFIX%%_getCurrentThread(%%ISOLATE_TYPE%%* isolate);
                        int %%PREFIX%%_detachThread(%%THREAD_TYPE%%* thread);
                        int %%PREFIX%%_tearDownIsolate(%%THREAD_TYPE%%* thread);""")
                .set("ISOLATE_TYPE", getIsolateType())
                .set("THREAD_TYPE", getIsolateThreadType())
                .set("PREFIX", lib.getCPrefix())
                .render();
    }

    private String getIsolateType() {
        return lib.getCPrefix() + "_isolate_t";
    }

    private String getIsolateThreadType() {
        return lib.getCPrefix() + "_isolatethread_t";
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
        return Joiner.on(",\n").join(libs);
    }

    static class SignatureBuilder {

        public SignatureBuilder(String isolateThreadType) {
            this.isolateThreadParam = isolateThreadType + "* isolate";
        }

        public SignatureBuilder init(String isolateName, TypeDescription returnType, String cEntryPointName) {
            params.clear();
            this.returnType = returnType;
            this.cEntryPointName = cEntryPointName;
            this.isolateName = isolateName;
            return this;
        }

        public SignatureBuilder addParam(TypeDescription type, String name) {
            params.add(new SimpleEntry<>(type, name));
            return this;
        }

        public void writeTo(StringBuilder header, StringBuilder impl) {
            writeExternalSignature(header);
            writeInternalSignature(impl);
            writeExternalToInternalWrapper(impl);
        }

        public void writeInternalSignature(StringBuilder out) {
            // Original signature generated by GraalVM. Structs cannot cross an entry point
            // by value, so spans pass as struct pointers and returns fill an out-struct.
            // The names differ from the probe header, but only the layout has to match.
            boolean spanReturn = returnType.getDataType() == DataType.FLOAT64_SPAN;
            out.append(spanReturn ? "void" : getInternalQualifiedCType(returnType))
                    .append(" ").append(getInternalName(cEntryPointName)).append("(");
            out.append(isolateThreadParam);
            for (var param : params) {
                if (param.getKey().getDataType() == DataType.FLOAT64_SPAN) {
                    out.append(", const ").append(TypeMap.toExternalC(param.getKey(), false))
                            .append("* ").append(param.getValue());
                } else {
                    out.append(", ").append(getInternalQualifiedCType(param.getKey())).append(" ").append(param.getValue());
                }
            }
            if (spanReturn) {
                out.append(", ").append(TypeMap.toExternalC(returnType, false)).append("* spanOut");
            }
            out.append(");\n");
        }

        public void writeExternalSignature(StringBuilder out) {
            // External signature without GraalVM isolates
            out.append(DLL_PUBLIC);
            out.append(getQualifiedCType(returnType)).append(" ").append(cEntryPointName).append("(");
            int numParams = 0;
            for (var param : params) {
                var key = getQualifiedCType(param.getKey());
                out.append(numParams++ > 0 ? ", " : "").append(key).append(" ").append(param.getValue());
            }
            out.append(");\n");
        }

        public void writeExternalToInternalWrapper(StringBuilder out) {

            // Custom wrapper
            invocation.setLength(0);
            boolean spanReturn = returnType.getDataType() == DataType.FLOAT64_SPAN;
            if (returnType.getDataType() != DataType.VOID && !spanReturn) {
                invocation.append("return ");
            }
            out.append(getQualifiedCType(returnType)).append(" ").append(cEntryPointName).append("(");
            invocation.append(getInternalName(cEntryPointName)).append("(").append(isolateNamespace).append(isolateName).append("()");

            int numParams = 0;
            for (var param : params) {
                String name = param.getValue();
                String value = switch (param.getKey().getDataType()) {
                    case SIZE_T -> "static_cast<long long>(" + name + ")";
                    // spans cross the entry point as a pointer to the by-value parameter
                    case FLOAT64_SPAN -> "&" + name;
                    default -> name;
                };

                String type = getQualifiedCType(param.getKey());
                out.append(numParams++ > 0 ? ", " : "").append(type).append(" ").append(name);
                invocation.append(", ").append(value);
            }

            // Forward wrapper call. Span returns get filled into a local struct that
            // gets returned by value. Seeded with the opening brace so the buffer ends on
            // a newline, which is what makes the renderer indent the first body line.
            var body = IndentingTextRenderer.of(") {\n").indent();
            if (spanReturn) {
                invocation.append(", &result_");
                body.addLine(getQualifiedCType(returnType) + " result_;")
                        .addLine(invocation + ");")
                        .addLine("return result_;");
            } else {
                body.addLine(invocation + ");");
            }
            out.append(body.unindent().addLine("}").render());
        }

        private static String getInternalName(String cEntryPointName) {
            return CEntryPointGenerator.INTERNAL_PREFIX + cEntryPointName;
        }

        private final String isolateThreadParam;
        private String isolateName;
        private TypeDescription returnType;
        private String cEntryPointName;
        private List<Map.Entry<TypeDescription, String>> params = new ArrayList<>();

        private final StringBuilder invocation = new StringBuilder();

    }

    private static final String isolateNamespace = "isolate::";
    private boolean spanTypedefWritten = false;
    StringBuilder typeDefs = new StringBuilder();
    StringBuilder header = new StringBuilder();
    StringBuilder cFile = new StringBuilder();
    StringBuilder mFile = new StringBuilder();

}
