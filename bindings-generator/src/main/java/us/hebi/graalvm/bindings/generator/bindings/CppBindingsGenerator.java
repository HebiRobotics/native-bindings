package us.hebi.graalvm.bindings.generator.bindings;

import com.google.common.base.CaseFormat;
import com.google.common.base.Joiner;
import us.hebi.graalvm.bindings.generator.bindings.features.FeatureGenerator;
import us.hebi.graalvm.bindings.generator.bindings.features.LibraryInfoFeature;
import us.hebi.graalvm.bindings.generator.generators.CEnumGenerator;
import us.hebi.graalvm.bindings.generator.generators.DocumentationUtil;
import us.hebi.graalvm.bindings.generator.generators.GeneratorUtil;
import us.hebi.graalvm.bindings.generator.generators.TokenReplacer;
import us.hebi.graalvm.bindings.model.*;

import java.io.IOException;
import java.io.StringWriter;
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

    private void writeApiDefinition(Writer out, boolean useDynamicLookup) throws IOException {

        // Load DLL
        out.write("#pragma once\n\n");
        out.write("#include <algorithm>\n"); // TODO: used for std::min() in overloads, but should be added via annotation? Windows.h defines min/max macros, so we need to use (std::min) to get around macro expansion
        if (useDynamicLookup) {
            out.write("""
                    #include <cassert>
                    #include <cstddef>
                    #include <cstdint>
                    #include <stdexcept>
                    #include <iostream>
                    #include <string>
                    #include <vector>

                    #ifdef WIN32
                    #include <Windows.h>
                    #else
                    #include <dlfcn.h>
                    #endif
                    """);
        } else {
            // Note: write the include section separately so we get alphabetical sorting
            out.write("""
                    #include <cstddef>
                    #include <cstdint>
                    #include <stdexcept>
                    #include <string>
                    #include <vector>
                    """);
        }

        // TODO: rewrite C header, so we can get nice C++ enums w/o collisions?
        // if (useDynamicLookup && !OMIT_C_HEADER) {
        //     out.write("#include \"" + api.getLibName() + "_c.h\"\n");
        // }

        // Sort utility classes to the end
        var apiClasses = getApiClasses();

        // Single namespaces require C++17, so write them in the old style
        out.write(getCppNamespace(lib, true));
        out.write("\n\n");

        out.write("""
                class Exception : public std::exception {
                public:
                  Exception(const char* message) noexcept : msg_(message) {}
                  Exception(const std::string& message) noexcept : msg_(message) {}
                  virtual ~Exception() noexcept {}
                  const char* what() const noexcept override {
                    return msg_.c_str();
                  }
                protected:
                  std::string msg_;
                };
                """);
        out.write("\n");

        // Enum definitions (normally included in the C header)
        for (EnumDescription generatedEnum : lib.getEnums()) {
            out.write(CEnumGenerator.getCppEnumDefinition(generatedEnum));
            out.write("\n");
        }

        // Forward declare struct/pointer types in case we have early references
        out.write("// ==== Forward Declarations ====\n");
        for (ClassDescription apiClass : apiClasses) {
            if (!apiClass.getInstantiable()) continue;
            String cppClassName = apiClass.getType().getTypeName();
            out.write(String.format("class %s;\n", cppClassName));
        }
        for (var typedef : lib.getTypedefs()) {
            out.write(typedef.getCppDefinition());
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
        out.write("}\n\n");

        // Forward declare the whole API. Write the implementations at the end
        StringWriter impl = new StringWriter();

        out.write("// ==== Class Wrappers ====\n");
        for (ClassDescription apiClass : apiClasses) {
            boolean useNamespace = !apiClass.getInstantiable();
            String cppClassName = apiClass.getType().getTypeName();
            String cppNamespace = CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, cppClassName);
            String namespace = useNamespace ? cppNamespace : cppClassName;
            String indent = useNamespace ? "" : "  ";

            impl.write("// " + cppClassName + "\n");

            if (apiClass.getType().hasComment()) {
                out.write(DocumentationUtil.generateDoxygen("", apiClass.getType()));
            }

            if (apiClass.getInstantiable()) {

                if (apiClass.hasParent()) {
                    out.write(String.format("class %s : public %s {\n", cppClassName, getCppClassName(apiClass.getParent())));
                } else {
                    out.write(String.format("class %s {\n", cppClassName));
                }

                for (String friendClass : getFriendClasses(lib, apiClass)) {
                    out.write("  friend class %s;\n".formatted(friendClass));
                }

                out.write("public:\n");

            } else {

                out.write("namespace %s {\n".formatted(cppNamespace));

            }

            // Move constructors to the top
            var methods = StreamSupport.stream(apiClass.getMethods().spliterator(), false)
                    .sorted(Comparator.comparing(MethodDescription::getIsConstructor).reversed())
                    .toList();
            var hasDedicatedConstructor = methods.stream().anyMatch(MethodDescription::getIsConstructor);

            // Wrap all methods
            List<String> cParams = new ArrayList<>();
            List<String> cppParams = new ArrayList<>();
            List<String> cppHeaderParams = new ArrayList<>();
            for (MethodDescription method : methods) {

                cppParams.clear();
                cppHeaderParams.clear();
                cParams.clear();

                // instance reference
                if (!method.getIsStatic()) {
                    cParams.add("ptr_");
                }

                // other arguments
                for (TypeDescription parameter : method.getParameters()) {
                    cParams.add(parameter.getParamName());
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

                // C++ function signature
                if (method.hasDocumentation()) {
                    out.write(DocumentationUtil.generateDoxygen(indent, method));
                }
                if (!useNamespace) {
                    out.write("  ");
                }
                if (method.getIsConstructor()) {
                    out.write("%s(%s);\n".formatted(cppClassName, argJoiner.join(cppHeaderParams)));
                    impl.write("inline %s::%s(%s) {\n".formatted(cppClassName, cppClassName, argJoiner.join(cppParams)));
                    if (useDynamicLookup) {
                        impl.write(getDynamicFunctionLookup(apiClass, method));
                    }
                    impl.write("  ptr_ = %s(%s);\n".formatted(method.getCName(), argJoiner.join(cParams)));
                    if (isThrowing(method)) {
                        impl.write("""
                                  if (!ptr_) {
                                    throw Exception("Could not create %s");
                                  }
                                """.formatted(getCppClassName(apiClass.getType())));
                    }
                    impl.write("}\n");

                } else {

                    if (method.getIsStatic() && !useNamespace) {
                        out.write("static ");
                    }

                    String cppReturnType = getCppReturnType(method);
                    String constkw = hasFlag(method, MethodFlag.Const) ? " const" : "";
                    String noexcept = hasFlag(method, MethodFlag.Noexcept) ? " noexcept" : "";

                    String headerSignature = String.format("%s(%s)",
                            method.getName(),
                            argJoiner.join(cppHeaderParams));
                    out.write(cppReturnType + " " + headerSignature + constkw + noexcept + ";\n");

                    String methodSignature = String.format("%s(%s)",
                            method.getName(),
                            argJoiner.join(cppParams));
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

                    // Forwarding call
                    impl.write("  ");

                    // return
                    switch (method.getReturnType().getDataType()) {
                        case VOID -> {
                        }
                        case OBJECT_HANDLE, CHAR_POINTER -> impl.write("auto ptr = ");
                        case VOID_WITH_ERROR_CODE -> impl.write("int status_ = ");
                        default -> impl.write("return ");
                    }


                    // Call C method
                    impl.write(String.format("%s(%s);\n",
                            method.getCName(),
                            argJoiner.join(cParams)));

                    // Check for errors
                    if (isThrowing(method)) {
                        switch (method.getReturnType().getDataType()) {
                            case OBJECT_HANDLE -> impl.write("""
                                      if (!ptr) {
                                        throw Exception("Could not create %s in %s::%s");
                                      }
                                    """.formatted(getCppClassName(method.getReturnType()), getCppClassName(apiClass.getType()), method.getName()));
                            case VOID_WITH_ERROR_CODE -> impl.write("""
                                      if (status_ != 0) {
                                        throw Exception("Encountered error in %s::%s");
                                      }
                                    """.formatted(getCppClassName(apiClass.getType()), method.getName()));
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
                    }

                    // end
                    impl.write("}\n");

                }

                // Add user-friendly overloads to the bottom
                addCppOverloads(apiClass, namespace, method, out, impl);

            }

            if (apiClass.getInstantiable()) {

                var replace = new TokenReplacer()
                        .add("%%CLASS%%", cppClassName)
                        .add("%%C_DESTRUCTOR_LOOKUP%%", !useDynamicLookup ? "" :
                                "\n    static auto %%C_DESTRUCTOR%% = DynamicLookup::instance().getFunc<void(*)(%%CTYPE%%)>(\"%%C_DESTRUCTOR%%\");")
                        .add("%%C_DESTRUCTOR%%", apiClass.getCDestructorName())
                        .add("%%CTYPE%%", getCppType(apiClass.getType()))
                        .add("%%VIRTUAL_DESTRUCTOR%%", apiClass.hasChildren() ? "virtual " : "");

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

                    if (!apiClass.hasChildren() && !hasDedicatedConstructor) {
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
                    replace = replace
                            .add("%%PARENT%%", getCppClassName(apiClass.getParent()))
                            .add("%%PARENT_CTYPE%%", getCppType(apiClass.getParent()))
                            .add("%%CAST%%", apiClass.getToParentCastMethod().getCName());
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
                            """
                            .replaceAll("%%DYNAMIC_LOOKUP%%", useDynamicLookup ? "\n  static auto %%CAST%% = DynamicLookup::instance().getFunc<%%PARENT_CTYPE%%(*)(%%CTYPE%%)>(\"%%CAST%%\");" : "");
                }

                out.write(replace.apply(classDef));
                impl.write(replace.apply(classImpl));

            }

            out.write("}");
            if (useNamespace) {
                out.write(" // namespace " + namespace);
            } else {
                out.write(";");
            }
            out.write("\n\n");
            impl.write("\n");

        }

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
                    writeCFuncSignature(apiClass, cast.get(), out);
                } else if (apiClass.getInstantiable()) {
                    out.write("  void %s(%s);\n".formatted(apiClass.getCDestructorName(), getCppType(apiClass.getType())));
                }
            }
            for (FeatureGenerator feature : features) {
                out.write(feature.getCHeader(true));
            }
            out.write("}\n\n");
        }

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
        return getCppType(method.getReturnType(), false);
    }

    private String getCReturnType(MethodDescription method) {
        return getCType(method.getReturnType());
    }

    private static String getCType(TypeDescription type) {
        var string = switch (type.getDataType()) {
            case TYPEDEF, OBJECT_HANDLE, ENUM -> getCppType(type, true);
            default -> TypeMap.toExternalC(type.getDataType());
        };
        return type.getIsConst() ? "const " + string : string;
    }

    private static String getCppType(TypeDescription type, boolean internalPtr) {
        String string = switch (type.getDataType()) {
            case OBJECT_HANDLE -> internalPtr
                    ? INTERNAL_POINTER_NAMESPACE + "::" + type.getTypeName() + C_PTR_SUFFIX
                    : type.getTypeName();
            case ENUM, TYPEDEF -> type.getTypeName();
            default -> TypeMap.toExternalCpp(type.getDataType());
        };
        return type.getIsConst() ? "const " + string : string;
    }

    private static final String INTERNAL_POINTER_NAMESPACE = "internal";

    interface OverloadWriter {
        void write(String params, String body, String call) throws IOException;
    }

    private void addCppOverloads(ClassDescription apiClass, String namespace, MethodDescription method, Writer out, Writer impl) throws IOException {
        final boolean useNamespace = !apiClass.getInstantiable();

        OverloadWriter overloadWriter = (params, body, call) -> {

            TokenReplacer replaceTokens = new TokenReplacer()
                    .add("%%CONST%%", hasFlag(method, MethodFlag.Const) ? " const" : "")
                    .add("%%NOEXCEPT%%", hasFlag(method, MethodFlag.Noexcept) ? " noexcept" : "")
                    .add("%%STATIC%%", apiClass.getInstantiable() && method.getIsStatic() ? "static " : "")
                    .add("%%INDENT%%", useNamespace ? "" : "  ")
                    .add("%%NAMESPACE%%", namespace)
                    .add("%%NAME%%", method.getName())
                    .add("%%RETURN_TYPE%%", getCppReturnType(method))
                    .add("%%RETURN%%", TypeMap.isVoid(method.getReturnType().getDataType()) ? "" : "return ")
                    .add("%%PARAMS%%", params)
                    .add("%%PARAMS_IMPL%%", params.replaceAll(" = [^,)]*", "")) // no default args in impl
                    .add("%%BODY%%", body.replaceAll("\n", "\n  "))
                    .add("%%CALL%%", call);

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
            }

            parameters.add(getCppType(p) + " " + p.getParamName() + getDefaultParamInit(p));
            call.add(p.getParamName());

        }

        if (needsDefaultOverload) {
            overloadWriter.write(argJoiner.join(parameters), "", argJoiner.join(call));
        }

    }

    private String getDynamicFunctionLookup(ClassDescription apiClass, MethodDescription method) {
        // Build parameters including obj reference
        List<String> cParamTypes = new ArrayList<>();
        if (!method.getIsStatic()) {
            cParamTypes.add(getCType(apiClass.getType()));
        }
        for (TypeDescription parameter : method.getParameters()) {
            cParamTypes.add(getCType(parameter));
        }
        var allFunctionArgs = argJoiner.join(cParamTypes);

        // Write the implementation
        return "  static auto %s = DynamicLookup::instance().getFunc<%s(*)(%s)>(\"%s\");\n".formatted(
                method.getCName(),
                getCReturnType(method),
                allFunctionArgs,
                method.getCName()
        );

    }

    private void writeCFuncSignature(ClassDescription apiClass, MethodDescription method, Writer impl) throws IOException {
        // TODO: C++ types due to enum mismatches. Properly handle C types w/ conversions?
        List<String> cParamTypes = new ArrayList<>();
        if (!method.getIsStatic()) {
            cParamTypes.add(getCType(apiClass.getType()));
        }
        for (TypeDescription parameter : method.getParameters()) {
            cParamTypes.add(getCType(parameter));
        }
        var allFunctionArgs = argJoiner.join(cParamTypes);

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
                .replaceAll("%%CPP_NAMESPACE%%", argJoiner.on("::").join(lib.getNamespace()))
        );
    }

    private static final Joiner argJoiner = Joiner.on(", ");

}
