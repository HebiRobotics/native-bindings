package us.hebi.graalvm.bindings.generator.bindings;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import org.stringtemplate.v4.ST;
import us.hebi.graalvm.bindings.generator.bindings.features.CocoaFeature;
import us.hebi.graalvm.bindings.generator.bindings.features.FeatureGenerator;
import us.hebi.graalvm.bindings.generator.bindings.features.LibraryInfoFeature;
import us.hebi.graalvm.bindings.generator.generators.CEntryPointGenerator;
import us.hebi.graalvm.bindings.generator.generators.CEnumGenerator;
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
        // Init
        features.add(new LibraryInfoFeature(lib));
        features.add(new CocoaFeature(lib));

        // Process all classes
        for (ClassDescription apiClass : lib.getClasses()) {
            process(apiClass);
        }

        // Header for the shared library
        filer.createFile("include", lib.getName() + "_c.h", writer -> writeHeader(writer, false));

        // Additional sources for the static library
        filer.createFile("..", lib.getName() + "_c.h", writer -> writeHeader(writer, true));
        filer.createFile("..", "graal_isolate.h", writer -> writer.write(loadContent("c/graal_isolate.h")));
        filer.createFile("..", lib.getName() + "_c.cpp", this::writeImplementation);
        filer.createFile("..", lib.getName() + "_objc.m", writer -> writer.write(mFile.toString()));
        filer.createFile("..", lib.getName() + "_static.cmake", this::writeCMakeStatic);
        filer.createFile("..", "CMakeLists.txt", this::writeCMakeLists);
        filer.createFile("..", "mex.cmake", writer -> writer.write(loadContent("c/mex.cmake")));

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
            findSpanType(apiClass).ifPresent(spanType -> {
                typeDefs.append(String.format("""

                        // A (pointer, length) view of a contiguous double buffer, passed and returned
                        // by value. Inputs are caller-owned and only read during the call. Returned
                        // spans are library-owned and stay valid until the calling thread's next
                        // library call, so they have to be copied immediately.
                        typedef struct {
                            const double* data;
                            size_t length;
                        } %s;
                        """, TypeMap.toExternalC(spanType, false)));
                spanTypedefWritten = true;
            });
        }

        header.append("\n// ==== ").append(type.getTypeName()).append(" ====\n");
        cFile.append("\n// ==== ").append(type.getTypeName()).append(" ====\n");
        SignatureBuilder func = new SignatureBuilder();
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

    private static Optional<TypeDescription> findSpanType(ClassDescription apiClass) {
        for (var method : apiClass.getMethods()) {
            if (method.getReturnType().getDataType() == DataType.FLOAT64_SPAN) {
                return Optional.of(method.getReturnType());
            }
            for (var param : method.getParameters()) {
                if (param.getDataType() == DataType.FLOAT64_SPAN) {
                    return Optional.of(param);
                }
            }
        }
        return Optional.empty();
    }

    public static void addParentCastMethod(MethodDescription method, StringBuilder header, StringBuilder cFile) {
        Function<String, String> replace = input -> input
                .replaceAll("%%DLL_PUBLIC%%", DLL_PUBLIC)
                .replaceAll("%%PARENT%%", getQualifiedCType(method.getReturnType()))
                .replaceAll("%%NAME%%", method.getCName())
                .replaceAll("%%CHILD%%", getQualifiedCType(method.getParameters().get(0)));
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

    private static final String IsolateStruct = "graal_isolate_t*";
    private static final String IsolateThreadParam = "graal_isolatethread_t* isolate";
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
        Set<String> isolateNames = new HashSet<>();
        for (var aClass : lib.getClasses()) {
            aClass.tryGetIsolateName().ifPresent(isolateNames::add);
        }
        for (var isolateName : isolateNames) {
            isolates.append(createIsolateCode(isolateName));
        }

        fileWriter.write(loadTemplate("c/libName_c.cpp")
                .add("isolates", isolates.toString().trim())
                .add("libName", lib.getName())
                .add("content", writer.toString())
                .render());

        for (FeatureGenerator feature : features) {
            mFile.append(feature.getObjCSource());
        }

    }

    private String createIsolateCode(String functionName) {
        return new ST("""
                graal_isolatethread_t* <name>() {
                     static graal_isolate_t* isolate = createIsolate();
                     static thread_local ThreadHandle thread(isolate);
                     return thread.ptr;
                }
                """)
                .add("name", functionName)
                .render();
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

    private String getExtraAppleLibs() {
        List<String> libs = new ArrayList<>();
        for (FeatureGenerator feature : features) {
            libs.addAll(feature.getAppleLibraries());
        }
        return Joiner.on(",\n").join(libs);
    }

    static class SignatureBuilder {

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
            out.append(IsolateThreadParam);
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
            // gets returned by value
            out.append(") {\n");
            if (spanReturn) {
                invocation.append(", &result_");
                out.append(INDENT).append(getQualifiedCType(returnType)).append(" result_;\n")
                        .append(INDENT).append(invocation).append(");\n")
                        .append(INDENT).append("return result_;\n");
            } else {
                out.append(INDENT).append(invocation).append(");\n");
            }
            out.append("}\n");
        }

        private static String getInternalName(String cEntryPointName) {
            return CEntryPointGenerator.INTERNAL_PREFIX + cEntryPointName;
        }

        private String isolateName;
        private TypeDescription returnType;
        private String cEntryPointName;
        private List<Map.Entry<TypeDescription, String>> params = new ArrayList<>();

        private final StringBuilder invocation = new StringBuilder();

    }

    private static final String isolateNamespace = "isolate::";
    private static String INDENT = "    ";
    private boolean spanTypedefWritten = false;
    StringBuilder typeDefs = new StringBuilder();
    StringBuilder enumDefs = new StringBuilder();
    StringBuilder header = new StringBuilder();
    StringBuilder cFile = new StringBuilder();
    StringBuilder mFile = new StringBuilder();

    private final List<FeatureGenerator> features = new ArrayList<>();

}
