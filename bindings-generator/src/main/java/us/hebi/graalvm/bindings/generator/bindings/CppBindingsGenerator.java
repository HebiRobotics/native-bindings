package us.hebi.graalvm.bindings.generator.bindings;

import com.google.common.base.CaseFormat;
import com.google.common.base.Joiner;
import us.hebi.graalvm.bindings.generator.bindings.features.FeatureGenerator;
import us.hebi.graalvm.bindings.generator.bindings.features.LibraryInfoFeature;
import us.hebi.graalvm.bindings.generator.bindings.marshallers.StructMarshallers;
import us.hebi.graalvm.bindings.generator.generators.CEnumGenerator;
import us.hebi.graalvm.bindings.generator.generators.DocumentationUtil;
import us.hebi.graalvm.bindings.generator.generators.GeneratorUtil;
import us.hebi.graalvm.bindings.generator.generators.IndentingTextRenderer;
import us.hebi.graalvm.bindings.model.*;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.*;
import java.util.function.Function;
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

    private void writeApiDefinition(Writer out, boolean useDynamicLookup) throws IOException {
        // Sort utility classes to the end
        var apiClasses = getApiClasses();

        // Forward declare the whole API. Write the implementations at the end
        StringWriter impl = new StringWriter();

        writeIncludes(out, useDynamicLookup);

        // Single namespaces require C++17, so write them in the old style
        out.write(getCppNamespace(lib, true));
        out.write("\n\n");

        writeEnumDefinitions(out);
        writeStructDefinitions(out);
        writeForwardDeclarations(out, apiClasses, useDynamicLookup);
        writeExceptionClass(out);

        out.write("// ==== Class Wrappers ====\n");
        for (ClassDescription apiClass : apiClasses) {
            writeClassWrapper(new ClassScope(apiClass), out, impl, useDynamicLookup);
        }

        writeCLibraryAccess(out, apiClasses, useDynamicLookup);

        out.write("// ==== C++ Implementations ====\n");
        out.write(impl.toString());
        for (FeatureGenerator feature : features) {
            out.write(feature.getCppWrapper(useDynamicLookup));
        }

        // Write cross-platform implementations for dynamic lookup
        if (useDynamicLookup) {
            writeDynamicLookupImplementations(out);
        }

        out.write(getCppNamespace(lib, false));

    }

    private void writeIncludes(Writer out, boolean useDynamicLookup) throws IOException {
        out.write("#pragma once\n\n");

        // One sorted set for the base headers and the marshaller contributions.
        // TODO: cppOverload blocks may need to add headers as well
        var headers = new TreeSet<>(List.of("cstddef", "cstdint", "stdexcept", "string", "vector"));
        if (useDynamicLookup) {
            headers.add("cassert");
            headers.add("iostream");
        }
        headers.addAll(getMarshallerIncludes());
        for (String header : headers) {
            out.write("#include <" + header + ">\n");
        }

        if (useDynamicLookup) {
            // Load DLL
            out.write("""

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
    private void writeExceptionClass(Writer out) throws IOException {
        out.write("""
                class Exception : public std::runtime_error {
                public:
                  explicit Exception(const char* message) : std::runtime_error(message) {}
                  Exception(const char* message, const char* id) : std::runtime_error(message), id_(id) {}
                  virtual ~Exception() noexcept = default;
                  const char* id() const noexcept { return id_.c_str(); } // Kind of failure, e.g., bindings:Generic
                private:
                  std::string id_;
                };
                
                inline void checkError(const %s& error) {
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
                """.formatted(getErrorType()));
        out.write("\n");
    }

    /**
     * Enum definitions, which are normally included via the C header.
     */
    private void writeEnumDefinitions(Writer out) throws IOException {
        for (EnumDescription generatedEnum : lib.getEnums()) {
            out.write(CEnumGenerator.getCppEnumDefinition(generatedEnum));
            out.write("\n");
        }
    }

    /**
     * User structs are part of the public API, so they live in the library namespace
     */
    private void writeStructDefinitions(Writer out) throws IOException {
        for (StructDescription struct : lib.getStructs()) {
            // A marshalled struct has no public face, users see the language type it maps to
            if (StructMarshallers.resolve(struct).flatMap(StructMarshaller::cpp).isPresent()) {
                continue;
            }
            if (struct.hasComment()) {
                out.write(DocumentationUtil.generateLineComments("", struct.getComment()));
            } else {
                out.write("// Plain data struct, passed by value. Layout changes are not ABI backwards compatible\n");
            }
            out.write("struct " + struct.getName() + " {\n");
            for (var field : struct.getFields()) {
                out.write("  " + TypeMap.toStructFieldC(field.getDataType(), field.getIsConst()) + " " + field.getLowerUnderscoreName() + ";\n");
            }
            out.write("};\n\n");
        }
    }

    private void writeForwardDeclarations(Writer out, List<ClassDescription> apiClasses, boolean useDynamicLookup) throws IOException {
        // Forward declare struct/pointer types in case we have early references
        out.write("// ==== Forward Declarations ====\n");
        for (ClassDescription apiClass : apiClasses) {
            if (!apiClass.getInstantiable()) continue;
            String cppClassName = apiClass.getType().getTypeName();
            out.write(String.format("class %s;\n", cppClassName));
        }
        for (var callback : lib.getCallbacks()) {
            out.write(getCppCallbackAlias(callback));
            out.write("\n");
        }

        // Forward declare opaque pointers in an internal namespace
        out.write("\nnamespace %s {\n".formatted(INTERNAL_POINTER_NAMESPACE));
        if (useDynamicLookup || OMIT_C_HEADER) {
            for (ClassDescription apiClass : apiClasses) {
                if (apiClass.getInstantiable()) {
                    String ptrType = apiClass.getType().getTypeName() + C_PTR_SUFFIX;
                    out.write("using %s = struct %s_*;\n".formatted(ptrType, getCppClassName(apiClass.getType())));
                }
            }
        }

        // Struct layouts for the internal mapping
        for (StructDescription struct : lib.getStructs()) {
            if (StructMarshallers.resolve(struct).flatMap(StructMarshaller::cpp).isEmpty()) continue;
            out.write("struct %s {\n".formatted(getInternalStructName(struct.getName())));
            for (var field : struct.getFields()) {
                out.write("  " + TypeMap.toStructFieldC(field.getDataType(), field.getIsConst()) + " " + field.getLowerUnderscoreName() + ";\n");
            }
            out.write("};\n");
        }

        // Error struct
        out.write("struct %s {\n  const char* message;\n  const char* id;\n  int32_t code;\n};\n".formatted(ERROR_STRUCT_NAME));
        out.write("}\n\n");
    }

    private void writeClassWrapper(ClassScope scope, Writer out, Writer impl, boolean useDynamicLookup) throws IOException {
        var apiClass = scope.apiClass;
        impl.write("// " + scope.cppClassName + "\n");

        if (apiClass.getType().hasComment()) {
            out.write(DocumentationUtil.generateDoxygen("", apiClass.getType()));
        }

        if (apiClass.getInstantiable()) {

            if (apiClass.hasParent()) {
                out.write(String.format("class %s : public %s {\n", scope.cppClassName, getCppClassName(apiClass.getParent())));
            } else {
                out.write(String.format("class %s {\n", scope.cppClassName));
            }

            for (String friendClass : getFriendClasses(lib, apiClass)) {
                out.write("  friend class %s;\n".formatted(friendClass));
            }

            out.write("public:\n");

        } else {

            out.write("namespace %s {\n".formatted(scope.cppNamespace));

        }

        for (MethodDescription method : scope.methods) {
            writeMethod(scope, method, out, impl, useDynamicLookup);
        }

        if (apiClass.getInstantiable()) {
            writeHandleBoilerplate(scope, out, impl, useDynamicLookup);
        }

        out.write("}");
        if (scope.useNamespace) {
            out.write(" // namespace " + scope.namespace);
        } else {
            out.write(";");
        }
        out.write("\n\n");
        impl.write("\n");
    }

    /**
     * The three parameter lists a method needs: the arguments forwarded to the C symbol,
     * the C++ signature used for the out-of-line definition, and the same signature with
     * default values, which may only appear in the declaration.
     */
    private final class MethodParams {

        MethodParams(MethodDescription method) {
            // error out-struct, ahead of the instance so it is argument zero everywhere
            if (isThrowing(method)) {
                cParams.add("&" + ERROR_LOCAL_NAME);
            }

            // instance reference
            if (!method.getIsStatic()) {
                cParams.add("ptr_");
            }

            // other arguments
            for (TypeDescription parameter : method.getParameters()) {
                cParams.add(parameter.getParamName());
                paramNames.add(parameter.getParamName());
                String value = String.format("%s %s",
                        getCppType(parameter),
                        parameter.getParamName()
                );
                cppParams.add(value);
                if (parameter.hasDefaultValue()) {
                    value += " = " + parameter.getDefaultValue();
                }
                cppHeaderParams.add(value);
            }
        }

        final List<String> cParams = new ArrayList<>();
        final List<String> cppParams = new ArrayList<>();
        final List<String> cppHeaderParams = new ArrayList<>();
        final List<String> paramNames = new ArrayList<>(); // declared-only, for the delegating constructor -> factory

    }

    private void writeMethod(ClassScope scope, MethodDescription method, Writer out, Writer impl, boolean useDynamicLookup) throws IOException {
        var params = new MethodParams(method);

        // C++ function signature
        if (method.hasDocumentation()) {
            out.write(DocumentationUtil.generateDoxygen(scope.indent, method));
        }
        if (!scope.useNamespace) {
            out.write("  ");
        }
        if (method.getIsConstructor()) {
            writeConstructor(scope, method, params, out, impl, useDynamicLookup);
        } else {
            writeFunction(scope, method, params, out, impl, useDynamicLookup);
        }

        // Add user-friendly overloads to the bottom
        addCppOverloads(scope.apiClass, scope.namespace, method, out, impl);
    }

    private void writeConstructor(ClassScope scope, MethodDescription method, MethodParams params,
                                  Writer out, Writer impl, boolean useDynamicLookup) throws IOException {
        var apiClass = scope.apiClass;
        String cppClassName = scope.cppClassName;
        List<String> cParams = params.cParams;
        List<String> cppParams = params.cppParams;
        List<String> cppHeaderParams = params.cppHeaderParams;
        out.write("%s(%s);\n".formatted(cppClassName, joiner.join(cppHeaderParams)));
        if (apiClass.hasParent()) {
            // The base class has no default constructor, so the value constructor has to delegate
            // to the pointer constructor that initializes the inherited part. Base initializers run
            // before the constructor body, so the null check moves into a factory that runs
            // while the delegated arguments get evaluated. It is a free function in the internal
            // namespace rather than a class member so that it cannot collide with a wrapped method.
            var factoryName = getConstructorFactoryName(apiClass.getType());
            impl.write("namespace %s {\n".formatted(INTERNAL_POINTER_NAMESPACE));
            impl.write("// The null check happens here because the base class gets initialized before the constructor body runs\n");
            impl.write("inline %s %s(%s) {\n".formatted(
                    getCppType(apiClass.getType()), factoryName, joiner.join(cppParams)));
            if (useDynamicLookup) {
                impl.write(getDynamicFunctionLookup(apiClass, method));
            }
            if (isThrowing(method)) {
                impl.write("  %s %s = {};\n".formatted(getErrorType(), ERROR_LOCAL_NAME));
            }
            impl.write("  auto ptr = %s(%s);\n".formatted(method.getCName(), joiner.join(cParams)));
            if (isThrowing(method)) {
                impl.write(getErrorCheck());
                impl.write("""
                          checkNotNull(ptr, "Could not create %s");
                        """.formatted(getCppClassName(apiClass.getType())));
            }
            impl.write("  return ptr;\n");
            impl.write("}\n");
            impl.write("}\n");
            // The delegation forwards the declared arguments, not the C call arguments, which
            // carry the error struct and the receiver.
            impl.write("inline %s::%s(%s) : %s(%s::%s(%s)) {}\n".formatted(
                    cppClassName, cppClassName, joiner.join(cppParams),
                    cppClassName, INTERNAL_POINTER_NAMESPACE, factoryName, joiner.join(params.paramNames)));
        } else {
            impl.write("inline %s::%s(%s) {\n".formatted(cppClassName, cppClassName, joiner.join(cppParams)));
            if (useDynamicLookup) {
                impl.write(getDynamicFunctionLookup(apiClass, method));
            }
            if (isThrowing(method)) {
                impl.write("  %s %s = {};\n".formatted(getErrorType(), ERROR_LOCAL_NAME));
            }
            impl.write("  ptr_ = %s(%s);\n".formatted(method.getCName(), joiner.join(cParams)));
            if (isThrowing(method)) {
                impl.write(getErrorCheck());
                impl.write("""
                          checkNotNull(ptr_, "Could not create %s");
                        """.formatted(getCppClassName(apiClass.getType())));
            }
            impl.write("}\n");
        }
    }

    private void writeFunction(ClassScope scope, MethodDescription method, MethodParams params,
                               Writer out, Writer impl, boolean useDynamicLookup) throws IOException {
        var apiClass = scope.apiClass;
        String namespace = scope.namespace;
        String indent = scope.indent;
        List<String> cParams = params.cParams;
        List<String> cppParams = params.cppParams;
        List<String> cppHeaderParams = params.cppHeaderParams;

        if (method.getIsStatic() && !scope.useNamespace) {
            out.write("static ");
        }

        String cppReturnType = getCppReturnType(method);
        String constkw = hasFlag(method, MethodFlag.Const) ? " const" : "";
        String noexcept = hasFlag(method, MethodFlag.Noexcept) ? " noexcept" : "";

        String headerSignature = String.format("%s(%s)",
                method.getName(),
                joiner.join(cppHeaderParams));
        out.write(cppReturnType + " " + headerSignature + constkw + noexcept + ";\n");

        String methodSignature = String.format("%s(%s)",
                method.getName(),
                joiner.join(cppParams));
        impl.write("inline " + cppReturnType + " " + namespace + "::" + methodSignature + constkw + noexcept + " {\n");

        // Add dynamic lookup where needed
        if (useDynamicLookup) {
            impl.write(getDynamicFunctionLookup(apiClass, method));
        }

        // Add custom bodies
        if (method.hasCppBody()) {
            impl.write(GeneratorUtil.indent(method.getCppBody().strip(), indent));
            impl.write("\n");
        }

        // The struct the call reports through. A plain value return has to be captured
        // rather than returned directly, because the check comes after the call.
        boolean throwing = isThrowing(method);
        if (throwing) {
            impl.write(String.format("  %s %s = {};\n", getErrorType(), ERROR_LOCAL_NAME));
        }

        // Forwarding call
        impl.write("  ");

        // return
        var marshaller = getCppMarshaller(method.getReturnType());
        switch (method.getReturnType().getDataType()) {
            case VOID -> {
            }
            case OBJECT_HANDLE, CHAR_POINTER -> impl.write("auto ptr = ");
            default -> {
                // The layout mirror has to be captured, the public type is built from it below
                if (marshaller.isPresent()) {
                    impl.write("auto " + STRUCT_LOCAL_NAME + " = ");
                } else {
                    impl.write(throwing ? "auto result_ = " : "return ");
                }
            }
        }

        // Call C method
        impl.write(String.format("%s(%s);\n",
                method.getCName(),
                joiner.join(cParams)));

        // Check for errors
        if (throwing) {
            impl.write(getErrorCheck());
            switch (method.getReturnType().getDataType()) {
                case OBJECT_HANDLE -> impl.write("""
                          checkNotNull(ptr, "Could not create %s in %s::%s");
                        """.formatted(getCppClassName(method.getReturnType()), getCppClassName(apiClass.getType()), method.getName()));
            }
        }

        // Convert pointers to objects
        switch (method.getReturnType().getDataType()) {
            case OBJECT_HANDLE -> impl.write("""
                      return %s(ptr);
                    """.formatted(getCppClassName(method.getReturnType())));
            case CHAR_POINTER -> impl.write("""
                      return !ptr ? std::string() : std::string(ptr); // copy utf8
                    """);
            case VOID -> {
            }
            default -> {
                if (marshaller.isPresent()) {
                    impl.write(marshaller.get().unpack(STRUCT_LOCAL_NAME));
                } else if (throwing) {
                    impl.write("  return result_;\n");
                }
            }
        }

        // end
        impl.write("}\n");

    }

    /**
     * Move, assign, destroy and the raw pointer field. Only instantiable classes own a
     * handle, so this is what turns the wrapper into an RAII type.
     */
    private void writeHandleBoilerplate(ClassScope scope, Writer out, Writer impl, boolean useDynamicLookup) throws IOException {
        var apiClass = scope.apiClass;
        String cppClassName = scope.cppClassName;

        // Shared by the class declaration and its out-of-line definitions. The lookup value
        // expands to further tokens, which the renderer resolves in a second pass.
        var tokens = new LinkedHashMap<String, String>();
        tokens.put("CLASS", cppClassName);
        tokens.put("C_DESTRUCTOR_LOOKUP", !useDynamicLookup ? "" :
                "\n    static auto %%C_DESTRUCTOR%% = DynamicLookup::instance().getFunc<void(*)(%%CTYPE%%)>(\"%%C_DESTRUCTOR%%\");");
        tokens.put("C_DESTRUCTOR", apiClass.getCDestructorName());
        tokens.put("CTYPE", getCppType(apiClass.getType()));
        tokens.put("VIRTUAL_DESTRUCTOR", apiClass.hasChildren() ? "virtual " : "");

        Function<String, String> replace = input -> {
            var renderer = IndentingTextRenderer.of(input);
            tokens.forEach(renderer::set);
            return renderer.render();
        };

        // Header
        String classDef = """
                  %%CLASS%%(%%CLASS%%&& from) noexcept;
                  %%CLASS%%& operator=(%%CLASS%%&& from) noexcept;
                """;
        final String classImpl;

        if (!apiClass.hasParent()) {
            classDef += "  %%VIRTUAL_DESTRUCTOR%%~%%CLASS%%() noexcept;\n";
            if (apiClass.hasChildren()) {
                classDef += """
                        protected:
                          explicit %%CLASS%%(%%CTYPE%% cPointer) noexcept : ptr_(cPointer) {}
                        """;
            }
            classDef += """
                    private:
                      void cleanup() noexcept;
                    """;

            if (!apiClass.hasChildren() && !scope.hasDedicatedConstructor) {
                classDef += """
                          explicit %%CLASS%%(%%CTYPE%% cPointer) noexcept : ptr_(cPointer) {}
                        """;
            }
            classDef += """
                      %%CTYPE%% ptr_{};
                    """;
            classImpl = """
                    inline void %%CLASS%%::cleanup() noexcept {
                      if (ptr_ != nullptr) {%%C_DESTRUCTOR_LOOKUP%%
                        %%C_DESTRUCTOR%%(ptr_);
                      }
                    }
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
                    """;
        } else {
            tokens.put("PARENT", getCppClassName(apiClass.getParent()));
            tokens.put("PARENT_CTYPE", getCppType(apiClass.getParent()));
            tokens.put("CAST", apiClass.getToParentCastMethod().getCName());
            if (apiClass.hasChildren()) {
                classDef += """
                        protected:
                          explicit %%CLASS%%(%%CTYPE%% cPointer) noexcept;
                        private:
                          static %%PARENT_CTYPE%% get%%PARENT%%Pointer(%%CTYPE%% cPointer) noexcept;
                          %%CTYPE%% ptr_{};
                        """;
            } else {
                classDef += """
                        private:
                          explicit %%CLASS%%(%%CTYPE%% cPointer) noexcept;
                          static %%PARENT_CTYPE%% get%%PARENT%%Pointer(%%CTYPE%% cPointer) noexcept;
                          %%CTYPE%% ptr_{};
                        """;
            }
            classImpl = """
                    inline %%PARENT_CTYPE%% %%CLASS%%::get%%PARENT%%Pointer(%%CTYPE%% cPointer) noexcept {%%DYNAMIC_LOOKUP%%
                      return %%CAST%%(cPointer);
                    }
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
                    """;
            tokens.put("DYNAMIC_LOOKUP", !useDynamicLookup ? "" :
                    "\n  static auto %%CAST%% = DynamicLookup::instance().getFunc<%%PARENT_CTYPE%%(*)(%%CTYPE%%)>(\"%%CAST%%\");");
        }

        out.write(replace.apply(classDef));
        impl.write(replace.apply(classImpl));

    }

    /**
     * How the wrapper reaches the C symbols: either a dynamic lookup helper, or plain
     * extern "C" declarations when the C header is not included.
     */
    private void writeCLibraryAccess(Writer out, List<ClassDescription> apiClasses, boolean useDynamicLookup) throws IOException {
        if (useDynamicLookup) {
            // Lookup class (first thing after the API definitions)
            out.write("// ==== C Library Lookup ====\n");
            out.write("namespace {\n");
            writeDynamicLookupClass(out);
            out.write("} // namespace\n\n");
        } else if (OMIT_C_HEADER) {
            out.write("// ==== C Library Functions ====\n");
            out.write("extern \"C\" {\n");
            for (ClassDescription apiClass : apiClasses) {
                for (MethodDescription method : apiClass.getMethods()) { // TODO: make C method use int for enums. Maybe void* for all pointers, so we get around `using`
                    writeCFuncSignature(apiClass, method, out);
                }
                var cast = apiClass.tryGetToParentCastMethod();
                if (cast.isPresent()) {
                    // A cast is generated plumbing rather than an API call and never throws
                    writeCFuncSignature(apiClass, cast.get(), out, false);
                } else if (apiClass.getInstantiable()) {
                    out.write("  void %s(%s);\n".formatted(apiClass.getCDestructorName(), getCppType(apiClass.getType())));
                }
            }
            for (FeatureGenerator feature : features) {
                out.write(feature.getCHeader(true));
            }
            out.write("}\n\n");
        }
    }

    /**
     * Per-class naming and layout decisions. An instantiable class becomes a C++ class with
     * member functions, everything else becomes a namespace of free functions, and nearly
     * every step below has to branch on which of the two it is.
     */
    private final class ClassScope {

        ClassScope(ClassDescription apiClass) {
            this.apiClass = apiClass;
            this.useNamespace = !apiClass.getInstantiable();
            this.cppClassName = apiClass.getType().getTypeName();
            this.cppNamespace = CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, cppClassName);
            this.namespace = useNamespace ? cppNamespace : cppClassName;
            this.indent = useNamespace ? "" : "  ";

            // Move constructors to the top
            this.methods = StreamSupport.stream(apiClass.getMethods().spliterator(), false)
                    .sorted(Comparator.comparing(MethodDescription::getIsConstructor).reversed())
                    .toList();
            this.hasDedicatedConstructor = methods.stream().anyMatch(MethodDescription::getIsConstructor);
        }

        final ClassDescription apiClass;
        final boolean useNamespace;
        final String cppClassName;
        final String cppNamespace;
        final String namespace;
        final String indent;
        final List<MethodDescription> methods;
        final boolean hasDedicatedConstructor;

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

    public static String getCppNamespaceQualifier(LibraryDescription lib) {
        StringBuilder out = new StringBuilder();
        for (String s : lib.getNamespace()) {
            out.append(s).append("::");
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

    private static Optional<StructMarshaller.CppStructMarshaller> getCppMarshaller(TypeDescription type) {
        if (type.getDataType() != DataType.STRUCT) return Optional.empty();
        return StructMarshallers.resolve(type).flatMap(StructMarshaller::cpp);
    }

    /**
     * The same function pointer the C header declares, spelled as an alias. Parameter names are
     * kept because the wrapper header is what a user reads to learn the callback's contract.
     */
    private static String getCppCallbackAlias(CallbackDescription callback) {
        var parameters = new StringJoiner(", ");
        for (var parameter : callback.getParameters()) {
            parameters.add(getCType(parameter) + " " + parameter.getParamName());
        }
        return String.format("using %s = %s (*)(%s);",
                callback.getName(), getCType(callback.getReturnType()), parameters);
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

    private static String getErrorCheck() {
        return "  checkError(%s);\n".formatted(ERROR_LOCAL_NAME);
    }

    /**
     * Name of the factory that value constructors of child classes delegate through. It carries the
     * class name and lives in the internal namespace, so it cannot collide with a wrapped method.
     */
    private static String getConstructorFactoryName(TypeDescription type) {
        return "create" + getCppClassName(type) + "Pointer";
    }

    interface OverloadWriter {
        void write(String params, String body, String call) throws IOException;
    }

    private void addCppOverloads(ClassDescription apiClass, String namespace, MethodDescription method, Writer out, Writer impl) throws IOException {
        final boolean useNamespace = !apiClass.getInstantiable();

        OverloadWriter overloadWriter = (params, body, call) -> {

            Function<String, String> replaceTokens = input -> IndentingTextRenderer.of(input)
                    .set("CONST", hasFlag(method, MethodFlag.Const) ? " const" : "")
                    .set("NOEXCEPT", hasFlag(method, MethodFlag.Noexcept) ? " noexcept" : "")
                    .set("STATIC", apiClass.getInstantiable() && method.getIsStatic() ? "static " : "")
                    .set("INDENT", useNamespace ? "" : "  ")
                    .set("NAMESPACE", namespace)
                    .set("NAME", method.getName())
                    .set("RETURN_TYPE", getCppReturnType(method))
                    .set("RETURN", TypeMap.isVoid(method.getReturnType().getDataType()) ? "" : "return ")
                    .set("PARAMS", params)
                    .set("PARAMS_IMPL", params.replaceAll(" = [^,)]*", "")) // no default args in impl
                    .set("BODY", body.replaceAll("\n", "\n  "))
                    .set("CALL", call)
                    .render();

            out.write(replaceTokens.apply("""
                    %%INDENT%%%%STATIC%%%%RETURN_TYPE%% %%NAME%%(%%PARAMS%%)%%CONST%%%%NOEXCEPT%%;
                    """));
            impl.write(replaceTokens.apply("""
                    inline %%RETURN_TYPE%% %%NAMESPACE%%::%%NAME%%(%%PARAMS_IMPL%%)%%CONST%%%%NOEXCEPT%% {
                      %%BODY%%%%RETURN%%%%NAME%%(%%CALL%%);
                    }
                    """));

        };

        // User-supplied overloads
        for (MethodOverload overload : method.getCppOverloads()) {
            overloadWriter.write(overload.getParameters(), overload.getBody(), overload.getCall());
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
            overloadWriter.write(joiner.join(parameters), "", joiner.join(call));
        }

    }

    private String getDynamicFunctionLookup(ClassDescription apiClass, MethodDescription method) {
        // Build parameters including obj reference
        List<String> cParamTypes = new ArrayList<>();
        if (isThrowing(method)) {
            cParamTypes.add(getErrorType() + "*");
        }
        if (!method.getIsStatic()) {
            cParamTypes.add(getCType(apiClass.getType()));
        }
        for (TypeDescription parameter : method.getParameters()) {
            cParamTypes.add(getCType(parameter));
        }
        var allFunctionArgs = joiner.join(cParamTypes);

        // Write the implementation
        return "  static auto %s = DynamicLookup::instance().getFunc<%s(*)(%s)>(\"%s\");\n".formatted(
                method.getCName(),
                getCReturnType(method),
                allFunctionArgs,
                method.getCName()
        );

    }

    private void writeCFuncSignature(ClassDescription apiClass, MethodDescription method, Writer impl) throws IOException {
        writeCFuncSignature(apiClass, method, impl, isThrowing(method));
    }

    private void writeCFuncSignature(ClassDescription apiClass, MethodDescription method, Writer impl,
                                     boolean throwing) throws IOException {
        // TODO: C++ types due to enum mismatches. Properly handle C types w/ conversions?
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
        var allFunctionArgs = joiner.join(cParamTypes);

        // Write the implementation
        impl.write("  %s %s(%s);\n".formatted(
                getCReturnType(method),
                method.getCName(),
                allFunctionArgs
        ));
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

    private void writeDynamicLookupDestructor(ClassDescription apiClass, Writer writer) throws IOException {
        String cName = apiClass.getCDestructorName();
        String cTypeName = getCppType(apiClass.getType());
        writer.write("  static auto %s = DynamicLookup::instance().getFunc<void(*)(%s)>(\"%s\");\n"
                .formatted(cName, cTypeName, cName, cTypeName));
    }

    private void writeDynamicLookupClass(Writer out) throws IOException {
        out.write("""
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
                """.replaceAll("%%LIB_NAME%%", lib.getName()));
    }

    private void writeDynamicLookupImplementations(Writer out) throws IOException {
        out.write("""
                
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
                
                """
                .replaceAll("%%VERSION_CHECK%%", new LibraryInfoFeature(lib).getVersionCompatibilityCheckCpp())
                .replaceAll("%%LIB_NAME%%", lib.getName())
                .replaceAll("%%CPP_NAMESPACE%%", Joiner.on("::").join(lib.getNamespace()))
        );
    }

}
