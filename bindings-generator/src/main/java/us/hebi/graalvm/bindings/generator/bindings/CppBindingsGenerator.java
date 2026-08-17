package us.hebi.graalvm.bindings.generator.bindings;

import com.google.common.base.CaseFormat;
import com.google.common.base.Joiner;
import us.hebi.graalvm.bindings.generator.bindings.features.FeatureGenerator;
import us.hebi.graalvm.bindings.generator.bindings.features.LibraryInfoFeature;
import us.hebi.graalvm.bindings.generator.bindings.marshallers.StructMarshallers;
import us.hebi.graalvm.bindings.generator.generators.CEnumGenerator;
import us.hebi.graalvm.bindings.generator.generators.DocumentationUtil;
import us.hebi.graalvm.bindings.generator.generators.IndentingTextRenderer;
import us.hebi.graalvm.bindings.model.*;

import java.io.IOException;
import java.io.Writer;
import java.util.*;
import java.util.stream.StreamSupport;

import static us.hebi.graalvm.bindings.generator.bindings.TypeMap.*;

/**
 * @author Florian Enner
 * @since 20 Nov 2024
 */
public class CppBindingsGenerator extends BindingGenerator {

    public CppBindingsGenerator(LibraryDescription lib) {
        super(lib);
    }

    @Override
    public void writeTo(Filer filer) throws IOException {
        filer.createFile("include", lib.getName() + ".hpp", writer ->
                writeApiDefinition(writer, false));
        filer.createFile("include", lib.getName() + "_dynamic.hpp", writer ->
                writeApiDefinition(writer, true));
        filer.createFile("", lib.getName() + ".cmake", this::writeCMakeFile);
    }

    private static final boolean OMIT_C_HEADER = true;

    private void writeCMakeFile(Writer writer) throws IOException {
        writer.write(loadTemplateWithLibVars("cpp/libName.cmake").render());
    }

    /**
     * The header declares the whole API first and defines it at the end, so declarations
     * and out-of-line definitions build up in two renderers.
     */
    private void writeApiDefinition(Writer writer, boolean useDynamicLookup) throws IOException {
        var apiClasses = getApiClasses();
        var decl = newRenderer();
        var def = newRenderer();

        writeIncludes(decl, useDynamicLookup);

        // Single namespaces require C++17, so write them in the old style
        decl.add(getCppNamespace(lib, true)).addLine().addLine();

        writeEnumDefinitions(decl);
        writeStructDefinitions(decl);
        writeForwardDeclarations(decl, apiClasses, useDynamicLookup);
        writeExceptionClass(decl);

        decl.addLine("// ==== Class Wrappers ====");
        for (ClassDescription apiClass : apiClasses) {
            writeClassWrapper(new ClassScope(apiClass, decl, def), useDynamicLookup);
        }

        writeCLibraryAccess(decl, apiClasses, useDynamicLookup);

        decl.addLine("// ==== C++ Implementations ====");
        for (FeatureGenerator feature : features) {
            def.add(feature.getCppWrapper(useDynamicLookup));
        }
        if (useDynamicLookup) {
            writeDynamicLookupImplementations(def);
        }
        def.add(getCppNamespace(lib, false));

        writer.write(decl.render());
        writer.write(def.render());
    }

    /**
     * Two space indent like the hand-written C++, plus the names every section refers to
     */
    private IndentingTextRenderer newRenderer() {
        return new IndentingTextRenderer()
                .setIndentString("  ")
                .set("LIB_NAME", lib.getName())
                .set("INTERNAL", INTERNAL_POINTER_NAMESPACE)
                .set("ERROR_STRUCT", ERROR_STRUCT_NAME)
                .set("ERROR_TYPE", getErrorType())
                .set("ERROR", ERROR_LOCAL_NAME);
    }

    private void writeIncludes(IndentingTextRenderer decl, boolean useDynamicLookup) {
        decl.addLine("#pragma once").addLine();

        // One sorted set for the base headers and the marshaller contributions.
        // TODO: cppOverload blocks may need to add headers as well
        var headers = new TreeSet<>(List.of("cstddef", "cstdint", "stdexcept", "string", "vector"));
        if (useDynamicLookup) {
            headers.add("cassert");
            headers.add("iostream");
        }
        headers.addAll(getMarshallerIncludes());
        for (String header : headers) {
            decl.addLine("#include <" + header + ">");
        }

        if (useDynamicLookup) {
            // Load DLL
            decl.add("""

                    #ifdef WIN32
                    #include <Windows.h>
                    #else
                    #include <dlfcn.h>
                    #endif
                    """);
        }

        // TODO: rewrite C header, so we can get nice C++ enums w/o collisions?
        // if (useDynamicLookup && !OMIT_C_HEADER) {
        //     out.write("#include \"" + api.getLibName() + "_c.h\"\n");
        // }
    }

    private List<String> getMarshallerIncludes() {
        var headers = new ArrayList<String>();
        for (StructDescription struct : lib.getStructs()) {
            StructMarshallers.resolve(struct)
                    .flatMap(StructMarshaller::cpp)
                    .ifPresent(cpp -> headers.addAll(cpp.systemIncludes()));
        }
        return headers;
    }

    /**
     * The exception copies message and id out of the per-thread buffers, so it stays
     * valid past the next call. checkError substitutes for NULL fields because it is
     * the only caller that can see one.
     */
    private void writeExceptionClass(IndentingTextRenderer decl) {
        decl.add("""
                class Exception : public std::runtime_error {
                public:
                  explicit Exception(const char* message) : std::runtime_error(message) {}
                  Exception(const char* message, const char* id) : std::runtime_error(message), id_(id) {}
                  virtual ~Exception() noexcept = default;
                  const char* id() const noexcept { return id_.c_str(); } // Kind of failure, e.g., bindings:Generic
                private:
                  std::string id_;
                };

                inline void checkError(const %%ERROR_TYPE%%& error) {
                  if (error.code != 0) {
                    const char* message = error.message != nullptr ? error.message : "unknown error";
                    const char* id = error.id != nullptr ? error.id : "";
                    throw Exception(message, id);
                  }
                }

                inline void checkNotNull(const void* ptr, const char* message) {
                  if (!ptr) {
                    throw Exception(message);
                  }
                }

                """);
    }

    /**
     * Enum definitions, which are normally included via the C header.
     */
    private void writeEnumDefinitions(IndentingTextRenderer decl) {
        for (EnumDescription generatedEnum : lib.getEnums()) {
            decl.add(CEnumGenerator.getCppEnumDefinition(generatedEnum)).addLine();
        }
    }

    /**
     * User structs are part of the public API, so they live in the library namespace
     */
    private void writeStructDefinitions(IndentingTextRenderer decl) {
        for (StructDescription struct : lib.getStructs()) {
            // A marshalled struct has no public face, users see the language type it maps to
            if (isMarshalled(struct)) {
                continue;
            }
            if (struct.hasComment()) {
                decl.add(DocumentationUtil.generateLineComments("", struct.getComment()));
            } else {
                decl.addLine("// Plain data struct, passed by value. Layout changes are not ABI backwards compatible");
            }
            writeStructLayout(decl, struct.getName(), struct);
            decl.addLine();
        }
    }

    private static void writeStructLayout(IndentingTextRenderer decl, String name, StructDescription struct) {
        decl.beginControlFlow("struct " + name + " {");
        for (var field : struct.getFields()) {
            decl.addLine(TypeMap.toStructFieldC(field.getDataType(), field.getIsConst()) + " " + field.getLowerUnderscoreName() + ";");
        }
        decl.endControlFlow("};");
    }

    private void writeForwardDeclarations(IndentingTextRenderer decl, List<ClassDescription> apiClasses, boolean useDynamicLookup) {
        // Forward declare struct/pointer types in case we have early references
        decl.addLine("// ==== Forward Declarations ====");
        for (ClassDescription apiClass : apiClasses) {
            if (!apiClass.getInstantiable()) continue;
            decl.addLine("class " + apiClass.getType().getTypeName() + ";");
        }

        // Opaque pointers, layout mirrors and the error struct live in an internal namespace
        decl.addLine().addLine("namespace %%INTERNAL%% {");
        if (useDynamicLookup || OMIT_C_HEADER) {
            for (ClassDescription apiClass : apiClasses) {
                if (apiClass.getInstantiable()) {
                    String ptrType = apiClass.getType().getTypeName() + C_PTR_SUFFIX;
                    decl.addLine("using " + ptrType + " = struct " + getCppClassName(apiClass.getType()) + "_*;");
                }
            }
        }
        for (StructDescription struct : lib.getStructs()) {
            if (isMarshalled(struct)) {
                writeStructLayout(decl, getInternalStructName(struct.getName()), struct);
            }
        }
        decl.add("""
                struct %%ERROR_STRUCT%% {
                  const char* message;
                  const char* id;
                  int32_t code;
                };
                }

                """);

        // Emitted after the internal namespace to allow callbacks with structs
        if (lib.getCallbacks().length() > 0) {
            for (var callback : lib.getCallbacks()) {
                decl.addLine(getCppCallbackAlias(callback));
            }
            decl.addLine();
        }
    }

    private void writeClassWrapper(ClassScope scope, boolean useDynamicLookup) {
        var apiClass = scope.apiClass;
        var decl = scope.decl;
        var def = scope.def;
        def.addLine("// %%CLASS%%");

        if (apiClass.getType().hasComment()) {
            decl.add(DocumentationUtil.generateDoxygen("", apiClass.getType()));
        }

        if (scope.useNamespace) {
            decl.addLine("namespace %%NAMESPACE%% {");
        } else {
            decl.addLine(apiClass.hasParent() ? "class %%CLASS%% : public %%PARENT%% {" : "class %%CLASS%% {");
            decl.indent();
            for (String friendClass : getFriendClasses(lib, apiClass)) {
                decl.addLine("friend class " + friendClass + ";");
            }
            writeAccessLabel(decl, "public:");
        }

        for (MethodDescription method : scope.methods) {
            writeMethod(new MethodScope(scope, method), useDynamicLookup);
        }

        if (scope.useNamespace) {
            decl.addLine("} // namespace %%NAMESPACE%%");
        } else {
            writeHandleBoilerplate(scope, useDynamicLookup);
            decl.unindent().addLine("};");
        }
        decl.addLine();
        def.addLine();
    }

    /**
     * Access specifiers sit one level out from the members they introduce
     */
    private static void writeAccessLabel(IndentingTextRenderer decl, String label) {
        decl.unindent().addLine(label).indent();
    }

    private void writeMethod(MethodScope m, boolean useDynamicLookup) {
        if (m.method.hasDocumentation()) {
            m.decl.add(DocumentationUtil.generateDoxygen("", m.method));
        }
        if (m.method.getIsConstructor()) {
            writeConstructor(m, useDynamicLookup);
        } else {
            writeFunction(m, useDynamicLookup);
        }

        // Add user-friendly overloads to the bottom
        writeCppOverloads(m);
    }

    private void writeConstructor(MethodScope m, boolean useDynamicLookup) {
        var def = m.def;
        m.decl.addLine("%%CLASS%%(%%PARAMS_WITH_DEFAULTS%%);");

        if (m.apiClass.hasParent()) {
            // The base class has no default constructor, so the value constructor has to delegate
            // to the pointer constructor that initializes the inherited part. Base initializers run
            // before the constructor body, so the null check moves into a factory that runs
            // while the delegated arguments get evaluated. It is a free function in the internal
            // namespace rather than a class member so that it cannot collide with a wrapped method.
            def.set("FACTORY", getConstructorFactoryName(m.apiClass.getType()));
            def.addLine("namespace %%INTERNAL%% {");
            def.addLine("// The null check happens here because the base class gets initialized before the constructor body runs");
            def.beginControlFlow("inline %%CTYPE%% %%FACTORY%%(%%PARAMS%%) {");
            writeCallSetup(m, useDynamicLookup);
            def.addLine("auto ptr = %%CNAME%%(%%ARGS%%);");
            if (isThrowing(m.method)) {
                def.addLine("checkError(%%ERROR%%);");
                def.addLine("checkNotNull(ptr, \"Could not create %%CLASS%%\");");
            }
            def.addLine("return ptr;");
            def.endControlFlow("}");
            def.addLine("}");
            // The delegation forwards the declared arguments, not the C call arguments, which
            // carry the error struct and the receiver.
            def.addLine("inline %%CLASS%%::%%CLASS%%(%%PARAMS%%) : %%CLASS%%(%%INTERNAL%%::%%FACTORY%%(%%ARG_NAMES%%)) {}");
        } else {
            def.beginControlFlow("inline %%CLASS%%::%%CLASS%%(%%PARAMS%%) {");
            writeCallSetup(m, useDynamicLookup);
            def.addLine("ptr_ = %%CNAME%%(%%ARGS%%);");
            if (isThrowing(m.method)) {
                def.addLine("checkError(%%ERROR%%);");
                def.addLine("checkNotNull(ptr_, \"Could not create %%CLASS%%\");");
            }
            def.endControlFlow("}");
        }
    }

    private void writeFunction(MethodScope m, boolean useDynamicLookup) {
        var method = m.method;
        var def = m.def;
        m.decl.addLine("%%STATIC%%%%RETURN_TYPE%% %%NAME%%(%%PARAMS_WITH_DEFAULTS%%)%%CONST%%%%NOEXCEPT%%;");
        def.beginControlFlow("inline %%RETURN_TYPE%% %%NAMESPACE%%::%%NAME%%(%%PARAMS%%)%%CONST%%%%NOEXCEPT%% {");

        def.addLineIf(useDynamicLookup, getDynamicLookup(m.apiClass, method));
        def.addLineIf(method.hasCppBody(), method.getCppBody().strip());

        // A plain value return has to be captured rather than returned directly, because
        // the error check comes after the call.
        boolean throwing = isThrowing(method);
        def.addLineIf(throwing, "%%ERROR_TYPE%% %%ERROR%% = {};");
        var returnType = method.getReturnType().getDataType();
        var marshaller = getCppMarshaller(method.getReturnType());
        String capture = switch (returnType) {
            case VOID -> "";
            case OBJECT_HANDLE, CHAR_POINTER -> "auto ptr = ";
            // The layout mirror has to be captured, the public type is built from it below
            default -> marshaller.isPresent() ? "auto " + STRUCT_LOCAL_NAME + " = " : throwing ? "auto result_ = " : "return ";
        };
        def.addLine(capture + "%%CNAME%%(%%ARGS%%);");

        if (throwing) {
            def.addLine("checkError(%%ERROR%%);");
            def.addLineIf(returnType == DataType.OBJECT_HANDLE, "checkNotNull(ptr, \"Could not create %%RETURN_TYPE%% in %%CLASS%%::%%NAME%%\");");
        }

        // Convert pointers to objects
        switch (returnType) {
            case OBJECT_HANDLE -> def.addLine("return %%RETURN_TYPE%%(ptr);");
            case CHAR_POINTER -> def.addLine("return !ptr ? std::string() : std::string(ptr); // copy utf8");
            case VOID -> {
            }
            default -> {
                if (marshaller.isPresent()) {
                    def.addLine(marshaller.get().unpack(STRUCT_LOCAL_NAME));
                } else if (throwing) {
                    def.addLine("return result_;");
                }
            }
        }
        def.endControlFlow("}");
    }

    /**
     * The dynamic symbol lookup and the error struct a throwing call reports through
     */
    private void writeCallSetup(MethodScope m, boolean useDynamicLookup) {
        m.def.addLineIf(useDynamicLookup, getDynamicLookup(m.apiClass, m.method));
        m.def.addLineIf(isThrowing(m.method), "%%ERROR_TYPE%% %%ERROR%% = {};");
    }

    /**
     * Move, assign, destroy and the raw pointer field. Only instantiable classes own a
     * handle, so this is what turns the wrapper into an RAII type.
     */
    private void writeHandleBoilerplate(ClassScope scope, boolean useDynamicLookup) {
        var apiClass = scope.apiClass;
        var decl = scope.decl;
        var def = scope.def;

        decl.addLine("%%CLASS%%(%%CLASS%%&& from) noexcept;");
        decl.addLine("%%CLASS%%& operator=(%%CLASS%%&& from) noexcept;");

        if (!apiClass.hasParent()) {
            decl.addLine(apiClass.hasChildren() ? "virtual ~%%CLASS%%() noexcept;" : "~%%CLASS%%() noexcept;");
            if (apiClass.hasChildren()) {
                writeAccessLabel(decl, "protected:");
                decl.addLine("explicit %%CLASS%%(%%CTYPE%% cPointer) noexcept : ptr_(cPointer) {}");
            }
            writeAccessLabel(decl, "private:");
            decl.addLine("void cleanup() noexcept;");
            decl.addLineIf(!apiClass.hasChildren() && !scope.hasDedicatedConstructor,
                    "explicit %%CLASS%%(%%CTYPE%% cPointer) noexcept : ptr_(cPointer) {}");
            decl.addLine("%%CTYPE%% ptr_{};");

            def.beginControlFlow("inline void %%CLASS%%::cleanup() noexcept {");
            def.beginControlFlow("if (ptr_ != nullptr) {");
            def.addLineIf(useDynamicLookup, getDynamicLookup(apiClass.getCDestructorName(), "void", scope.cType));
            def.addLine(apiClass.getCDestructorName() + "(ptr_);");
            def.endControlFlow("}");
            def.endControlFlow("}");
            def.add("""
                    inline %%CLASS%%::%%CLASS%%(%%CLASS%%&& from) noexcept : ptr_(from.ptr_) {
                      from.ptr_ = nullptr;
                    };
                    inline %%CLASS%%& %%CLASS%%::operator=(%%CLASS%%&& from) noexcept {
                      cleanup();
                      ptr_ = from.ptr_;
                      from.ptr_ = nullptr;
                      return *this;
                    };
                    inline %%CLASS%%::~%%CLASS%%() noexcept {
                      cleanup();
                    }
                    """);
        } else {
            writeAccessLabel(decl, apiClass.hasChildren() ? "protected:" : "private:");
            decl.addLine("explicit %%CLASS%%(%%CTYPE%% cPointer) noexcept;");
            if (apiClass.hasChildren()) {
                writeAccessLabel(decl, "private:");
            }
            decl.addLine("static %%PARENT_CTYPE%% get%%PARENT%%Pointer(%%CTYPE%% cPointer) noexcept;");
            decl.addLine("%%CTYPE%% ptr_{};");

            def.beginControlFlow("inline %%PARENT_CTYPE%% %%CLASS%%::get%%PARENT%%Pointer(%%CTYPE%% cPointer) noexcept {");
            def.addLineIf(useDynamicLookup, getDynamicLookup(scope.castToParent, scope.parentCType, scope.cType));
            def.addLine("return %%CAST%%(cPointer);");
            def.endControlFlow("}");
            def.add("""
                    inline %%CLASS%%::%%CLASS%%(%%CTYPE%% cPointer) noexcept : %%PARENT%%(get%%PARENT%%Pointer(cPointer)), ptr_(cPointer) {}
                    inline %%CLASS%%::%%CLASS%%(%%CLASS%%&& from) noexcept : %%PARENT%%(std::move(from)), ptr_(from.ptr_) {
                      from.ptr_ = nullptr;
                    };
                    inline %%CLASS%%& %%CLASS%%::operator=(%%CLASS%%&& from) noexcept {
                      %%PARENT%%::operator=(std::move(from));
                      ptr_ = from.ptr_;
                      from.ptr_ = nullptr;
                      return *this;
                    };
                    """);
        }
    }

    /**
     * How the wrapper reaches the C symbols: either a dynamic lookup helper, or plain
     * extern "C" declarations when the C header is not included.
     */
    private void writeCLibraryAccess(IndentingTextRenderer decl, List<ClassDescription> apiClasses, boolean useDynamicLookup) {
        if (useDynamicLookup) {
            // Lookup class (first thing after the API definitions)
            decl.addLine("// ==== C Library Lookup ====");
            decl.addLine("namespace {");
            writeDynamicLookupClass(decl);
            decl.addLine("} // namespace").addLine();
        } else if (OMIT_C_HEADER) {
            decl.addLine("// ==== C Library Functions ====");
            decl.beginControlFlow("extern \"C\" {");
            for (ClassDescription apiClass : apiClasses) {
                for (MethodDescription method : apiClass.getMethods()) { // TODO: make C method use int for enums. Maybe void* for all pointers, so we get around `using`
                    decl.addLine(getCFuncSignature(apiClass, method, isThrowing(method)));
                }
                var cast = apiClass.tryGetToParentCastMethod();
                if (cast.isPresent()) {
                    // A cast is generated plumbing rather than an API call and never throws
                    decl.addLine(getCFuncSignature(apiClass, cast.get(), false));
                } else if (apiClass.getInstantiable()) {
                    decl.addLine("void " + apiClass.getCDestructorName() + "(" + getCppType(apiClass.getType()) + ");");
                }
            }
            for (FeatureGenerator feature : features) {
                decl.add(feature.getCHeader(true));
            }
            decl.endControlFlow("}").addLine();
        }
    }

    /**
     * Per-class naming and layout decisions, plus the declaration and definition renderers
     * with the class names set. An instantiable class becomes a C++ class with member
     * functions, everything else becomes a namespace of free functions, and nearly every
     * step has to branch on which of the two it is.
     */
    private final class ClassScope {

        ClassScope(ClassDescription apiClass, IndentingTextRenderer decl, IndentingTextRenderer def) {
            this.apiClass = apiClass;
            this.useNamespace = !apiClass.getInstantiable();
            this.cppClassName = apiClass.getType().getTypeName();
            this.cppNamespace = CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, cppClassName);
            this.namespace = useNamespace ? cppNamespace : cppClassName;
            this.cType = getCppType(apiClass.getType());
            this.parentCType = apiClass.hasParent() ? getCppType(apiClass.getParent()) : null;
            this.castToParent = apiClass.hasParent() ? apiClass.getToParentCastMethod().getCName() : null;

            // Move constructors to the top
            this.methods = StreamSupport.stream(apiClass.getMethods().spliterator(), false)
                    .sorted(Comparator.comparing(MethodDescription::getIsConstructor).reversed())
                    .toList();
            this.hasDedicatedConstructor = methods.stream().anyMatch(MethodDescription::getIsConstructor);

            this.decl = decl.snapshot();
            this.def = def.snapshot();
            for (var renderer : List.of(this.decl, this.def)) {
                renderer.set("CLASS", cppClassName)
                        .set("NAMESPACE", namespace)
                        .set("CTYPE", cType);
                if (apiClass.hasParent()) {
                    renderer.set("PARENT", getCppClassName(apiClass.getParent()))
                            .set("PARENT_CTYPE", parentCType)
                            .set("CAST", castToParent);
                }
            }
        }

        final ClassDescription apiClass;
        final boolean useNamespace;
        final String cppClassName;
        final String cppNamespace;
        final String namespace;
        final String cType;
        final String parentCType;
        final String castToParent;
        final List<MethodDescription> methods;
        final boolean hasDedicatedConstructor;
        final IndentingTextRenderer decl;
        final IndentingTextRenderer def;

    }

    /**
     * The class renderers with one method's names set. The C++ parameter list carries default
     * values only in the declaration, and the C call passes the error struct and the receiver
     * ahead of the declared arguments.
     */
    private final class MethodScope {

        MethodScope(ClassScope scope, MethodDescription method) {
            this.apiClass = scope.apiClass;
            this.method = method;

            var cArgs = new ArrayList<String>();
            var params = new ArrayList<String>();
            var paramsWithDefaults = new ArrayList<String>();
            var argNames = new ArrayList<String>();

            // error out-struct, ahead of the instance so it is argument zero everywhere
            if (isThrowing(method)) {
                cArgs.add("&" + ERROR_LOCAL_NAME);
            }
            if (!method.getIsStatic()) {
                cArgs.add("ptr_");
            }
            for (TypeDescription parameter : method.getParameters()) {
                String name = parameter.getParamName();
                cArgs.add(name);
                argNames.add(name);
                params.add(getCppType(parameter) + " " + name);
                paramsWithDefaults.add(getCppType(parameter) + " " + name + getDefaultParamInit(parameter));
            }

            this.decl = scope.decl.snapshot();
            this.def = scope.def.snapshot();
            for (var renderer : List.of(decl, def)) {
                renderer.set("NAME", method.getName())
                        .set("CNAME", method.getCName())
                        .set("RETURN_TYPE", getCppReturnType(method))
                        .setIf("STATIC", method.getIsStatic() && !scope.useNamespace, "static ")
                        .setIf("CONST", hasFlag(method, MethodFlag.Const), " const")
                        .setIf("NOEXCEPT", hasFlag(method, MethodFlag.Noexcept), " noexcept")
                        .setArgs("PARAMS", params)
                        .setArgs("PARAMS_WITH_DEFAULTS", paramsWithDefaults)
                        .setArgs("ARGS", cArgs)
                        .setArgs("ARG_NAMES", argNames);
            }
        }

        final ClassDescription apiClass;
        final MethodDescription method;
        final IndentingTextRenderer decl;
        final IndentingTextRenderer def;

    }

    public static String getCppNamespace(LibraryDescription lib, boolean intro) {
        StringBuilder out = new StringBuilder();
        for (var name : lib.getNamespace()) {
            if (intro) {
                out.append("\nnamespace ").append(name).append(" {");
            } else {
                out.append("} // namespace ").append(name).append("\n");
            }
        }
        return out.toString();
    }

    private static String getDefaultParamInit(TypeDescription p) {
        return p.hasDefaultValue() ? " = " + p.getDefaultValue() : "";
    }

    private static String getCppClassName(TypeDescription type) {
        return getCppType(type, false);
    }

    private static String getCppType(TypeDescription type) {
        return getCppType(type, true);
    }

    private String getCppReturnType(MethodDescription method) {
        if (method.getReturnType().getDataType() == DataType.CHAR_POINTER) {
            return "std::string";
        }
        var marshalled = getCppMarshaller(method.getReturnType());
        if (marshalled.isPresent()) {
            return marshalled.get().returnType();
        }
        return getCppType(method.getReturnType(), false);
    }

    private String getCReturnType(MethodDescription method) {
        return getCType(method.getReturnType());
    }

    private static boolean isMarshalled(StructDescription struct) {
        return StructMarshallers.resolve(struct).flatMap(StructMarshaller::cpp).isPresent();
    }

    private static Optional<StructMarshaller.CppStructMarshaller> getCppMarshaller(TypeDescription type) {
        if (type.getDataType() != DataType.STRUCT) return Optional.empty();
        return StructMarshallers.resolve(type).flatMap(StructMarshaller::cpp);
    }

    /**
     * The C header's function pointer as an alias. Parameter names are kept for readability.
     */
    private static String getCppCallbackAlias(CallbackDescription callback) {
        var parameters = new ArrayList<String>();
        for (var parameter : callback.getParameters()) {
            parameters.add(CBindingsGenerator.getCallbackParameterType(parameter, CppBindingsGenerator::getCType)
                    + " " + parameter.getParamName());
        }
        return "using " + callback.getName() + " = " + getCType(callback.getReturnType()) + " (*)(" + String.join(", ", parameters) + ");";
    }

    private static String getCType(TypeDescription type) {
        var string = switch (type.getDataType()) {
            case CALLBACK, OBJECT_HANDLE, ENUM, STRUCT -> getCppType(type, true);
            default -> TypeMap.toExternalC(type.getDataType());
        };
        return type.getIsConst() ? "const " + string : string;
    }

    private static String getCppType(TypeDescription type, boolean internalPtr) {
        String string = switch (type.getDataType()) {
            case OBJECT_HANDLE -> internalPtr
                    ? INTERNAL_POINTER_NAMESPACE + "::" + type.getTypeName() + C_PTR_SUFFIX
                    : type.getTypeName();
            case ENUM, CALLBACK -> type.getTypeName();
            // A marshalled struct only exists as the internal layout mirror
            case STRUCT -> StructMarshallers.resolve(type).flatMap(StructMarshaller::cpp).isPresent()
                    ? INTERNAL_POINTER_NAMESPACE + "::" + getInternalStructName(type.getTypeName())
                    : type.getTypeName();
            default -> TypeMap.toExternalCpp(type.getDataType());
        };
        return type.getIsConst() ? "const " + string : string;
    }

    /**
     * The mirror shares the C name minus the library prefix, so it carries the canonical
     * UpperCamel struct name. The {@code internal} namespace is what separates it from a
     * public wrapper type of the same name.
     */
    private static String getInternalStructName(String structName) {
        return TypeMap.toStructCName("", structName);
    }

    private static final String INTERNAL_POINTER_NAMESPACE = "internal";
    private static final String ERROR_STRUCT_NAME = "ErrorInfo";
    private static final String ERROR_LOCAL_NAME = "error_";
    private static final String STRUCT_LOCAL_NAME = "struct_";

    private static String getErrorType() {
        return INTERNAL_POINTER_NAMESPACE + "::" + ERROR_STRUCT_NAME;
    }

    /**
     * The C++ helper a child class's value constructor delegates through, see writeConstructor.
     * It carries the class name and lives in the internal namespace, so it cannot collide with
     * a wrapped method.
     */
    private static String getConstructorFactoryName(TypeDescription type) {
        return "create" + getCppClassName(type) + "Pointer";
    }

    private void writeCppOverloads(MethodScope m) {
        var method = m.method;

        // User-supplied overloads
        for (MethodOverload overload : method.getCppOverloads()) {
            writeCppOverload(m, overload.getParameters(), overload.getBody(), overload.getCall());
        }

        // Don't generate auto-overloads if we have manual ones
        if (method.getCppOverloads().length() > 0) {
            return;
        }

        // Default overload
        boolean needsDefaultOverload = false;

        List<String> parameters = new ArrayList<>(method.getParameters().length());
        List<String> call = new ArrayList<>(method.getParameters().length());

        for (TypeDescription p : method.getParameters()) {

            switch (p.getDataType()) {
                case OBJECT_HANDLE -> {
                    parameters.add(getCppClassName(p) + "& " + p.getParamName() + getDefaultParamInit(p));
                    call.add(p.getParamName() + ".ptr_");
                    needsDefaultOverload = true;
                    continue;
                }
                case CHAR_POINTER -> {
                    parameters.add("const std::string& " + p.getParamName() + getDefaultParamInit(p));
                    call.add(p.getParamName() + ".c_str()");
                    needsDefaultOverload = true;
                    continue;
                }
                case FLOAT64_POINTER -> { // TODO: handle other pointer types
                    if (p.getIsConst()) {
                        parameters.add("const std::vector<double>& " + p.getParamName() + getDefaultParamInit(p));
                        call.add(p.getParamName() + ".data()");
                        needsDefaultOverload = true;
                        continue;
                    }
                }
                case STRUCT -> {
                    // The raw layout overload stays next to it, so the call site can avoid the copy
                    var marshaller = getCppMarshaller(p);
                    if (marshaller.isPresent()) {
                        parameters.add(marshaller.get().paramType() + " " + p.getParamName() + getDefaultParamInit(p));
                        call.add(marshaller.get().pack(p.getParamName()));
                        needsDefaultOverload = true;
                        continue;
                    }
                }
            }

            parameters.add(getCppType(p) + " " + p.getParamName() + getDefaultParamInit(p));
            call.add(p.getParamName());

        }

        if (needsDefaultOverload) {
            writeCppOverload(m, String.join(", ", parameters), "", String.join(", ", call));
        }

    }

    /**
     * A convenience signature that forwards to the wrapped method
     */
    private void writeCppOverload(MethodScope m, String params, String body, String call) {
        var decl = m.decl.snapshot()
                .set("PARAMS", params);
        var def = m.def.snapshot()
                .set("PARAMS", params.replaceAll(" = [^,)]*", "")) // no default args in impl
                .set("CALL", call);
        decl.addLine("%%STATIC%%%%RETURN_TYPE%% %%NAME%%(%%PARAMS%%)%%CONST%%%%NOEXCEPT%%;");
        def.beginControlFlow("inline %%RETURN_TYPE%% %%NAMESPACE%%::%%NAME%%(%%PARAMS%%)%%CONST%%%%NOEXCEPT%% {");
        def.addLineIf(!body.isBlank(), body.strip());
        def.addLine(TypeMap.isVoid(m.method.getReturnType().getDataType()) ? "%%NAME%%(%%CALL%%);" : "return %%NAME%%(%%CALL%%);");
        def.endControlFlow("}");
    }

    /**
     * The C signature the wrapper calls: the error struct and the receiver come before the
     * declared parameters.
     */
    private String getCParamTypes(ClassDescription apiClass, MethodDescription method, boolean throwing) {
        List<String> cParamTypes = new ArrayList<>();
        if (throwing) {
            cParamTypes.add(getErrorType() + "*");
        }
        if (!method.getIsStatic()) {
            cParamTypes.add(getCType(apiClass.getType()));
        }
        for (TypeDescription parameter : method.getParameters()) {
            cParamTypes.add(getCType(parameter));
        }
        return String.join(", ", cParamTypes);
    }

    private String getDynamicLookup(ClassDescription apiClass, MethodDescription method) {
        return getDynamicLookup(method.getCName(), getCReturnType(method), getCParamTypes(apiClass, method, isThrowing(method)));
    }

    /**
     * Resolves the symbol once, on first use of the enclosing function
     */
    private static String getDynamicLookup(String symbol, String returnType, String paramTypes) {
        return "static auto " + symbol + " = DynamicLookup::instance().getFunc<" + returnType + "(*)(" + paramTypes + ")>(\"" + symbol + "\");";
    }

    private String getCFuncSignature(ClassDescription apiClass, MethodDescription method, boolean throwing) {
        // TODO: C++ types due to enum mismatches. Properly handle C types w/ conversions?
        return getCReturnType(method) + " " + method.getCName() + "(" + getCParamTypes(apiClass, method, throwing) + ");";
    }

    /**
     * Other classes using this class need access to some private methods
     */
    private static Collection<String> getFriendClasses(LibraryDescription desc, ClassDescription thisClass) {
        var type = thisClass.getType().getTypeName();
        Set<String> friends = new TreeSet<>();

        for (ClassDescription apiClass : desc.getClasses()) {
            if (type.equals(apiClass.getType().getTypeName())) {
                continue;
            }

            var cppClass = apiClass.getType().getTypeName();
            for (MethodDescription method : apiClass.getMethods()) {
                if (type.equals(method.getReturnType().getTypeName())) {
                    friends.add(cppClass);
                }
                for (TypeDescription parameter : method.getParameters()) {
                    if (type.equals(parameter.getTypeName())) {
                        friends.add(cppClass);
                    }
                }
            }
        }

        return friends;
    }

    private void writeDynamicLookupClass(IndentingTextRenderer decl) {
        decl.add("""
                struct DynamicLookup {
                public:
                  static DynamicLookup& instance() {
                #ifdef WIN32
                    static DynamicLookup lib("%%LIB_NAME%%.dll");
                #elif __APPLE__
                    static DynamicLookup lib("lib%%LIB_NAME%%.dylib");
                #else
                    static DynamicLookup lib("./lib%%LIB_NAME%%.so", "lib%%LIB_NAME%%.so");
                #endif
                    return lib;
                  }

                  bool isLoaded() const { return lib_ != nullptr; };

                  template<typename FuncType> FuncType getFunc(const char* func) const;
                  DynamicLookup(const DynamicLookup&) = delete;
                  DynamicLookup &operator = (const DynamicLookup&) = delete;

                private:
                  explicit DynamicLookup(const std::string& path, const std::string& alt_path = "");
                  void* lib_{};
                  std::string path_{};
                };
                """);
    }

    private void writeDynamicLookupImplementations(IndentingTextRenderer def) {
        // The version check carries the namespace token, so it has to land first
        def.set("VERSION_CHECK", new LibraryInfoFeature(lib).getVersionCompatibilityCheckCpp());
        def.set("CPP_NAMESPACE", Joiner.on("::").join(lib.getNamespace()));
        def.add("""

                template<typename FuncType> FuncType DynamicLookup::getFunc(const char* func) const {
                  assert(lib_ != nullptr && "Fatal error -- cannot call %%CPP_NAMESPACE%% functions on unloaded library");
                  if (!lib_) {
                    std::cerr << "Fatal error -- cannot call %%CPP_NAMESPACE%% functions on unloaded library\\n";
                    return nullptr;
                  }
                #ifdef WIN32
                  FARPROC symbol = ::GetProcAddress((HMODULE) lib_, func);
                #else
                  void* symbol = dlsym(lib_, func);
                #endif
                  if (!symbol) {
                    std::cerr << "Fatal error -- %%CPP_NAMESPACE%% library is missing expected symbol: " << func << "\\n";
                  }
                  assert(symbol != nullptr);
                  return reinterpret_cast<FuncType>(symbol);
                }

                DynamicLookup:: DynamicLookup(const std::string& path, const std::string& alt_path) {
                  path_ = path;
                #ifdef WIN32
                  // convert to wide string to support non-ascii paths
                  int size_needed = MultiByteToWideChar(CP_UTF8, 0, path.c_str(), (int)path.size(), NULL, 0);
                  std::wstring wstrTo(size_needed, 0);
                  MultiByteToWideChar(CP_UTF8, 0, path.c_str(), (int)path.size(), &wstrTo[0], size_needed);
                  lib_ = ::LoadLibraryW(wstrTo.c_str());
                  if (!lib_ && !alt_path.empty()) {
                    size_needed = MultiByteToWideChar(CP_UTF8, 0, alt_path.c_str(), (int)alt_path.size(), NULL, 0);
                    wstrTo = std::wstring(size_needed, 0);
                    MultiByteToWideChar(CP_UTF8, 0, alt_path.c_str(), (int)alt_path.size(), &wstrTo[0], size_needed);
                    lib_ = ::LoadLibraryW(wstrTo.c_str());
                  }
                #else
                  lib_ = dlopen(path.c_str(), RTLD_LAZY);
                  if (!lib_ && !alt_path.empty()) {
                    lib_ = dlopen(alt_path.c_str(), RTLD_LAZY);
                  }
                #endif
                  if (!lib_) {
                    std::cerr << "%%CPP_NAMESPACE%% library not found! Include \\""<< path <<"\\" in library search path.\\n";
                    return;
                  }

                %%VERSION_CHECK%%
                }

                """);
    }

}
