
package us.hebi.graalvm.bindings.generator.bindings;

import com.google.common.base.Joiner;
import us.hebi.graalvm.bindings.generator.generators.DocumentationUtil;
import us.hebi.graalvm.bindings.generator.generators.GeneratorUtil;
import us.hebi.graalvm.bindings.generator.generators.IndentingTextRenderer;
import us.hebi.graalvm.bindings.model.*;

import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.util.function.Predicate.*;
import static us.hebi.quickbuf.ProtoUtil.*;

/**
 * @author Florian Enner
 * @since 18 Mär 2025
 */
public class MatlabBindingsGenerator extends BindingGenerator {

    public MatlabBindingsGenerator(LibraryDescription lib) {
        super(lib);
    }

    @Override
    public void writeTo(Filer filer) throws IOException {
        // Rather than adding a prefix to each class, we use a namespace directory to avoid
        // collisions with built-in classes.
        var directory = "+" + getNamespacePrefix(lib);

        // Public API
        for (var apiClass : getApiClassesAsStream().toList()) {
            filer.createFile(directory, getMatlabClassName(apiClass) + ".m",
                    w -> w.write(createClassWrapperSource(apiClass)));
        }
        for (var enumClass : lib.getEnums()) {
            filer.createFile(directory, getMatlabEnumClassName(enumClass) + ".m",
                    w -> w.write(createEnumClassSource(enumClass)));
        }

        // Type hints for the public API (must be placed in the directory that gets added to the path)
        filer.createFile(directory, "../functionSignatures.json", w -> w.write(createFunctionSignaturesSource()));

        // Internal utility methods (can be called without the full prefix)
        var privateDir = directory + "/private";
        filer.createFile(privateDir, "locate_binary.m", w -> w.write(createBinaryLocatorSource()));
        filer.createFile(privateDir, "build_native.m", w -> w.write(createBuildNativeSource()));
        if (lib.hasDownloadUrl()) {
            filer.createFile(privateDir, "download_binary.m", w -> w.write(createDownloadBinarySource()));
        }
        filer.createFile(privateDir, getDispatchFunctionName() + ".m", w -> w.write(createFallbackDispatchSource()));
        filer.createFile(privateDir, getDispatchFunctionName() + ".cpp", w -> w.write(createMexDispatchSource()));

    }

    private IndentingTextRenderer withLibVars() {
        return new IndentingTextRenderer()
                .set("LIB_NAME", lib.getName())
                .set("LIB_VERSION", lib.getVersion())
                .set("LIB_BUILD_NUMBER", lib.getBuild())
                .set("DISPATCH", getDispatchFunctionName());
    }

    public static String getNamespacePrefix(LibraryDescription lib) {
        return Joiner.on('_').join(lib.getNamespace());
    }

    private String getDispatchFunctionName() {
        return getNamespacePrefix(lib) + "_native";
    }

    private String createBinaryLocatorSource() {
        return withLibVars()
                .set("DOWNLOAD_BLOCK", createDownloadBlock())
                .addResourceContent(getClass(), "matlab/locate_binary.m")
                .render();
    }

    /**
     * The call into the generated download_binary. Libraries that do not publish their
     * binaries leave the url empty and get a locator that only looks locally.
     */
    private String createDownloadBlock() {
        if (!lib.hasDownloadUrl()) {
            return "";
        }
        return """

                        % Otherwise download it into a shared dir. We use the OS cache
                        % so that the binaries can be shared and don't pollute the root dir.
                        if ~exist(binPath, 'file')
                            download_binary(downloadDir, [libName '-' version '-' platform '.zip']);
                        end
                """;
    }

    private String createDownloadBinarySource() {
        return withLibVars()
                .set("DOWNLOAD_URL", lib.getDownloadUrl())
                .addResourceContent(getClass(), "matlab/download_binary.m")
                .render();
    }

    private String createBuildNativeSource() {
        return withLibVars().addResourceContent(getClass(), "matlab/build_native.m").render();
    }

    private String createFallbackDispatchSource() {
        return withLibVars().addResourceContent(getClass(), "matlab/libName_native.m").render();
    }

    /**
     * Fast FNV-1a 32-bit hash. Matches the C++ logic by treating characters as 8-bit ASCII.
     */
    public static long fnv1a_32_ascii(String s, long seed, long prime) {
        long hash = seed & 0xffffffffL; // simulate unsigned 32 bit behavior
        for (int i = 0; i < s.length(); i++) {
            hash ^= (s.charAt(i) & 0xff); // XOR lower 8 bits of UTF-16
            hash = (hash * (prime & 0xffffffffL)) & 0xffffffffL; // multiply by FNV prime and mask to 32 bit
        }
        return hash;
    }

    private long findCollisionFreeSeed(List<String> symbols, long seed, final long prime) {
        checkArgument(symbols.stream().distinct().count() == symbols.size(), "symbols must be unique!");
        Set<Long> seen = new HashSet<>();
        outer:
        while (true) {
            seen.clear();
            for (String name : symbols) {
                long h = fnv1a_32_ascii(name, seed, prime);
                if (!seen.add(h)) {
                    seed++; // Increment and try again
                    continue outer;
                }
            }
            return seed;
        }
    }

    private String getMexCType(TypeDescription param) {
        if (param.getDataType() == DataType.TYPEDEF) {
            return "void*"; // callbacks are not supported at this time
        }
        if (param.getDataType() == DataType.STRUCT) {
            return param.getTypeName(); // layout-compatible local struct generated into the MEX gateway
        }
        return (param.getIsConst() ? "const " : "") + TypeMap.toExternalC(param.getDataType());
    }

    private String getMexToC(TypeDescription param, int index) {
        return switch (param.getDataType()) {
            case OBJECT_HANDLE, VOID_POINTER, TYPEDEF ->
                    (param.hasParamName() ? "%s %s = ".formatted(getMexCType(param), param.getParamName()) : "") +
                    "getPtr<%s>(prhs[%d])".formatted(getMexCType(param), index);

            case ENUM, BOOL, INT8, INT16, INT32, INT64,
                 FLOAT32, FLOAT64, SIZE_T, CHAR16 -> "%s %s = getScalar<%s>(prhs[%d], \"%s\")".formatted(
                    getMexCType(param),
                    param.getParamName(),
                    getMexCType(param), index,
                    param.getParamName()
            );

            case FLOAT64_POINTER ->
                    "MatlabArray<double, mxDOUBLE_CLASS> %s(prhs[%d])".formatted(param.getParamName(), index);
            case FLOAT32_POINTER ->
                    "MatlabArray<float, mxSINGLE_CLASS> %s(prhs[%d])".formatted(param.getParamName(), index);
            case INT64_POINTER ->
                    "MatlabArray<int64_t, mxINT64_CLASS> %s(prhs[%d])".formatted(param.getParamName(), index);
            case INT32_POINTER ->
                    "MatlabArray<int32_t, mxINT32_CLASS> %s(prhs[%d])".formatted(param.getParamName(), index);
            case INT16_POINTER ->
                    "MatlabArray<int16_t, mxINT16_CLASS> %s(prhs[%d])".formatted(param.getParamName(), index);

            case STRUCT -> getMexMarshaller(param)
                    .map(marshaller -> marshaller.read("prhs[%d]".formatted(index), param.getParamName()))
                    .orElseGet(() -> "%1$s %2$s = get%1$s(prhs[%3$d])".formatted(
                            getMexCType(param), param.getParamName(), index));

            case CHAR_POINTER -> "MatlabString %s(prhs[%d])".formatted(param.getParamName(), index);

            case CHAR_POINTER_POINTER -> "MatlabStringArray %s(prhs[%d])".formatted(param.getParamName(), index);

            default -> throw new IllegalStateException("Unsupported input type: " + param.getDataType());
        };
    }

    private String getCToMex(TypeDescription type) {
        return switch (type.getDataType()) {
            // Numbers and Booleans
            case ENUM, BOOL, INT8, INT16, INT32, FLOAT32, FLOAT64, CHAR16 ->
                    "createMexScalar(%%RETURN_VALUE%%)";

            // Too wide for the 53 bit mantissa of MATLAB's default double
            case INT64 -> "createMexInt64(%%RETURN_VALUE%%)";
            case SIZE_T -> "createMexSize(%%RETURN_VALUE%%)";

            // Opaque Handles and Pointers
            case OBJECT_HANDLE, VOID_POINTER -> "createMexPointer(%%RETURN_VALUE%%)";

            // Strings (C-String to MATLAB String)
            case CHAR_POINTER -> "createMexString(%%RETURN_VALUE%%)";

            // User structs (copied into a 1x1 MATLAB struct). Concatenated rather than
            // formatted, because String::formatted would eat the %% around the token.
            case STRUCT -> getMexMarshaller(type)
                    .map(marshaller -> marshaller.write("%%RETURN_VALUE%%"))
                    .orElseGet(() -> "createMex" + type.getTypeName() + "(%%RETURN_VALUE%%)");

            default -> throw new IllegalStateException("Unsupported output type: " + type.getDataType());
        };
    }

    private static String getDispatchIdentifier(String cName) {
        // return "uint32(" + fnv1a_32_ascii(cName, 0x811c9dc5L, 0x01000193L) + ")"; // ~5% faster, but less binary compatible
        return quoted(cName);
    }

    private String createMexDispatchSource() {
        var cases = withLibVars().indent(2)
                .set("PRE", "")
                .set("POST", "")
                .set("CALL", "%%CNAME%%(%%ARGS%%);")
                .set("RETURN_VALUE", "_value")
                .set("ARG_COUNT", "0");

        // Find a collision-free hash seed
        List<String> symbols = GeneratorUtil.getAllCMethodSymbols(lib);
        long prime = 0x01000193L;
        long seed = findCollisionFreeSeed(symbols, 0x811c9dc5L, prime);
        Function<String, String> computeHash = cname -> String.valueOf(fnv1a_32_ascii(cname, seed, prime));

        Consumer<IndentingTextRenderer> addCase = (template) -> {
            template.beginControlFlow("case /* hash32_fnv1a(\"%%CNAME%%\") */ %%HASH%%: {");
            template.addLine("static auto %%CNAME%% = libLoader.getFunc<%%RETURN_TYPE%%(*)(%%TYPES%%)>(\"%%CNAME%%\");");
            template.addLine("checkArgCount(nrhs, %%ARG_COUNT%%, \"%%CNAME%%\");");
            template.addLine("%%PRE%%%%CALL%%%%POST%%");
            template.addLine("break;").unindent().addLine("}");
        };

        final StringBuilder pre = new StringBuilder();
        final StringBuilder post = new StringBuilder();
        final List<String> types = new ArrayList<>();
        final List<String> args = new ArrayList<>();
        final int FIRST_INDEX = 1; // first real argument after function name
        for (ClassDescription clazz : lib.getClasses()) {
            cases.addLine("\n// " + getMatlabClassName(clazz));
            for (MethodDescription method : clazz.getMethods()) {
                int index = FIRST_INDEX;
                pre.setLength(0);
                post.setLength(0);
                types.clear();
                args.clear();
                // The error struct is filled by the call and raises before any output is built
                if (isThrowing(method)) {
                    pre.append("ErrorInfo _error = {};\n");
                    post.append("\ncheckError(_error);");
                    types.add("ErrorInfo*");
                    args.add("&_error");
                }
                if (!method.getIsStatic()) {
                    types.add(getMexCType(clazz.getType()));
                    args.add(getMexToC(clazz.getType(), index++));
                }
                for (var param : method.getParameters()) {
                    types.add(getMexCType(param));
                    pre.append(getMexToC(param, index++)).append(";\n");
                    args.add(param.getParamName());
                }
                var vars = cases.snapshot();
                vars.set("RETURN_TYPE", getMexCType(method.getReturnType()));
                if (method.getReturnType().getDataType() != DataType.VOID) {
                    pre.append("%%RETURN_TYPE%% %%RETURN_VALUE%% = ");
                    post.append("\nif (nlhs > 0) plhs[0] = ").append(getCToMex(method.getReturnType())).append(";");
                }
                addCase.accept(vars.set("CNAME", method.getCName())
                        .set("HASH", computeHash.apply(method.getCName()))
                        .set("ARG_COUNT", String.valueOf(index - FIRST_INDEX))
                        .set("PRE", pre.toString())
                        .set("POST", post.toString())
                        .set("TYPES", joiner.join(types))
                        .set("ARGS", joiner.join(args)));
            }
            if (clazz.getInstantiable()) {
                if (clazz.hasCDestructorName()) {
                    addCase.accept(cases.snapshot()
                            .set("RETURN_TYPE", "void")
                            .set("TYPES", getMexCType(clazz.getType()))
                            .set("ARGS", getMexToC(clazz.getType(), FIRST_INDEX))
                            .set("CNAME", clazz.getCDestructorName())
                            .set("HASH", computeHash.apply(clazz.getCDestructorName()))
                            .set("ARG_COUNT", "1")
                    );
                }
                if (clazz.hasToParentCastMethod()) {
                    addCase.accept(cases.snapshot()
                            .set("RETURN_TYPE", getMexCType(clazz.getToParentCastMethod().getReturnType()))
                            .set("TYPES", getMexCType(clazz.getType()))
                            .set("CNAME", clazz.getToParentCastMethod().getCName())
                            .set("HASH", computeHash.apply(clazz.getToParentCastMethod().getCName()))
                            .set("ARGS", getMexToC(clazz.getType(), FIRST_INDEX))
                            .set("ARG_COUNT", "1")
                            .set("CALL", "if (nlhs > 0) plhs[0] = createMexPointer(%%CNAME%%(%%ARGS%%));"));
                }
            }
        }

        // Generate
        return withLibVars()
                .set("HASH_SEED", String.format("0x%08X", seed))
                .set("HASH_PRIME", String.format("0x%08X", prime))
                .set("STRUCT_HELPERS", createStructHelpers())
                .set("STRUCT_RETURN_HELPERS", createStructReturnHelpers())
                .set("SWITCH_CASES", cases.render())
                .addResourceContent(getClass(), "matlab/libName_native.cpp")
                .render();
    }

    /**
     * Layout-compatible copies of the user structs plus their readers. The token they fill sits
     * at the end of an existing line, so a library without structs renders the template unchanged.
     */
    private String createStructHelpers() {
        if (lib.getStructs().length() == 0) {
            return "";
        }

        var out = new IndentingTextRenderer();
        if (needsStructFieldReader()) {
            out.add("""


                    // Reads one field of a MATLAB struct argument. mxGetField returns null for a field
                    // that does not exist, which the scalar check cannot tell apart from a bad value.
                    template <typename T>
                    inline T getStructField(const mxArray* arg, const char* structName, const char* fieldName) {
                        const mxArray* field = mxGetField(arg, 0, fieldName);
                        if (!field) {
                            mexErrMsgIdAndTxt("%%LIB_NAME%%:InvalidInput", "Struct '%s' is missing field '%s'.", structName, fieldName);
                        }
                        return getScalar<T>(field, fieldName);
                    }
                    """);
        }

        for (var struct : lib.getStructs()) {
            var content = out.snapshot().set("STRUCT_NAME", struct.getName());
            content.add("""

                    // Layout-compatible with the by-value %%STRUCT_NAME%% struct in the C ABI
                    struct %%STRUCT_NAME%% {
                    """).indent();
            for (var field : struct.getFields()) {
                content.addLine(TypeMap.toStructFieldC(field.getDataType()) + " " + field.getLowerUnderscoreName() + ";");
            }
            content.unindent().add("};\n");

            // A marshalled struct brings its own conversions, the struct field reader below is the default
            var marshaller = StructMarshallers.resolve(struct).flatMap(StructMarshaller::matlab);
            if (marshaller.isPresent()) {
                content.add(marshaller.get().helpers());
                continue;
            }

            content.add("""

                    inline %%STRUCT_NAME%% get%%STRUCT_NAME%%(const mxArray* arg) {
                        // mxIsStruct is not part of the clean-room SDK subset, and is documented as this check
                        if (mxGetClassID(arg) != mxSTRUCT_CLASS) {
                            mexErrMsgIdAndTxt("%%LIB_NAME%%:TypeError", "Argument must be a '%%STRUCT_NAME%%' struct.");
                        }
                        return %%STRUCT_NAME%%{
                    """).indentBy(2);
            var fields = struct.getFields();
            for (int i = 0; i < fields.length(); i++) {
                var field = fields.get(i);
                // A pointer field is not read from MATLAB, but the initializer is positional
                String value = TypeMap.isPointerType(field.getDataType())
                        ? "nullptr"
                        : "getStructField<" + TypeMap.toStructFieldC(field.getDataType())
                          + ">(arg, \"%%STRUCT_NAME%%\", \"" + field.getName() + "\")";
                content.addLine(value + (i == fields.length() - 1 ? "" : ","));
            }
            content.unindentBy(2).add("""
                        };
                    }
                    """);
        }

        // The token sits at the end of a line, so the template supplies the newline
        return out.stripTrailingWhitespace().render();
    }

    /**
     * Writers for the structs that a method returns. They fill their own token rather than
     * joining the readers above, because they call the scalar helpers that are declared
     * further down in the template.
     */
    private String createStructReturnHelpers() {
        var out = new IndentingTextRenderer();
        for (var struct : lib.getStructs()) {
            if (!isReturnedByValue(struct)) continue;
            if (StructMarshallers.resolve(struct).flatMap(StructMarshaller::matlab).isPresent()) continue;
            var content = out.snapshot().set("STRUCT_NAME", struct.getName());
            var fields = struct.getFields();
            List<String> names = new ArrayList<>();
            // Pointer fields have no lifetime story here, only the layout mirror carries them
            for (var field : fields) {
                if (TypeMap.isPointerType(field.getDataType())) continue;
                names.add(cQuoted(field.getName()));
            }
            content.add("""


                    // The 1x1 MATLAB struct a returned %%STRUCT_NAME%% maps to
                    inline mxArray* createMex%%STRUCT_NAME%%(const %%STRUCT_NAME%%& value) {
                    """).indent()
                    .addLine("const char* fields[] = {" + joiner.join(names) + "};")
                    .addLine("mxArray* result = mxCreateStructMatrix(1, 1, " + names.size() + ", fields);");
            for (var field : fields) {
                if (TypeMap.isPointerType(field.getDataType())) continue;
                content.addLine("mxSetField(result, 0, " + cQuoted(field.getName()) + ", "
                                + getStructFieldToMex(field.getDataType(), "value." + field.getLowerUnderscoreName()) + ");");
            }
            content.addLine("return result;").unindent().add("}\n");
        }

        // The token sits at the end of a line, so the template supplies the newline
        return out.stripTrailingWhitespace().render();
    }

    private static Optional<StructMarshaller.MexStructMarshaller> getMexMarshaller(TypeDescription type) {
        if (type.getDataType() != DataType.STRUCT) return Optional.empty();
        return StructMarshallers.resolve(type).flatMap(StructMarshaller::matlab);
    }

    /**
     * @return true if any struct is read from a MATLAB struct argument, which is what needs the field reader
     */
    private boolean needsStructFieldReader() {
        for (var struct : lib.getStructs()) {
            if (StructMarshallers.resolve(struct).flatMap(StructMarshaller::matlab).isEmpty()) return true;
        }
        return false;
    }

    /** MATLAB string literals are single-quoted, the gateway is C++ */
    private static String cQuoted(String input) {
        return "\"" + input + "\"";
    }

    private static String getStructFieldToMex(DataType type, String value) {
        return switch (type) {
            case BOOL -> "mxCreateLogicalScalar(" + value + " != 0)"; // the layout keeps a boolean as int32
            case INT64 -> "createMexInt64(" + value + ")"; // too wide for the 53 bit mantissa
            case SIZE_T -> "createMexSize(" + value + ")"; // unsigned and too wide for the 53 bit mantissa
            default -> "createMexScalar(" + value + ")";
        };
    }

    private boolean isReturnedByValue(StructDescription struct) {
        for (var apiClass : lib.getClasses()) {
            for (var method : apiClass.getMethods()) {
                var returnType = method.getReturnType();
                if (returnType.getDataType() == DataType.STRUCT
                    && returnType.getTypeName().equals(struct.getName())) {
                    return true;
                }
            }
        }
        return false;
    }

    // =========================================================================
    // Enum class
    // =========================================================================

    private String createEnumClassSource(EnumDescription enumClass) {
        var content = withLibVars()
                .set("ENUM_NAME", getMatlabEnumClassName(enumClass));
        content.beginControlFlow("""
                classdef %%ENUM_NAME%% < int32
                % auto-generated class for holding %%ENUM_NAME%% constants
                """);

        // List all values
        content.beginControlFlow("enumeration");
        for (var value : enumClass.getValues()) {
            content.add(value.getName()).add("(").add(value.getCValue()).add(")\n");
        }
        content.endControlFlow("end\n");

        // Input map
        content.beginControlFlow("methods (Static, Access = public)");
        content.beginControlFlow("function out = toNativeValue(input)");
        content.add("""

                if isstring(input)
                    input = char(input);
                elseif ~ischar(input)
                    input = int32(input);
                end

                """);

        content.beginControlFlow("switch input");
        for (var value : enumClass.getValues()) {
            String options = toCellArray(
                    quoted(value.getName()),
                    quoted(value.getCName()),
                    String.valueOf(value.getCValue()));
            content.add("case ").add(options).addLine()
                    .indent()
                    .add("out = ").add(value.getCValue()).addLine(";")
                    .unindent();
        }
        content.add("""
                otherwise
                    error('input is not a valid %%ENUM_NAME%% value. Use char or int constants.');
                """);
        content.endControlFlow("end\n");
        content.addLine("out = int32(out);");

        content.endControlFlow("end\n");
        content.endControlFlow("end\n");

        return content.endControlFlow("end").render();
    }

    private static String quoted(String input) {
        return "'" + input + "'";
    }

    private static String toCellArray(String... options) {
        return switch (options.length) {
            case 0 -> "{}";
            case 1 -> options[0];
            default -> "{" + joiner.join(Arrays.asList(options)) + "}";
        };
    }

    // =========================================================================
    // Class wrapper
    // =========================================================================

    private String createClassWrapperSource(ClassDescription apiClass) {
        var content = withLibVars()
                .set("CLASS_NAME", getMatlabClassName(apiClass))
                .set("REF_FIELD", apiClass.hasParent() ? "ref_" + getMatlabClassName(apiClass.getType()) : "ptr")
                .set("GET_REF_METHOD", "c_ptr")
                .set("PARENT_NAME", apiClass.hasParent() ? getFullyQualifiedClassName(apiClass.getParent()) : "handle");
        apiClass.tryGetCDestructorName().ifPresent(cName -> content.set("C_DESTRUCTOR", getDispatchIdentifier(cName)));
        apiClass.tryGetToParentCastMethod().ifPresent(cName -> content.set("CAST", getDispatchIdentifier(cName.getCName())));

        // Class declaration
        content.add("classdef ");
        if (!apiClass.hasChildren()) content.add("(Sealed) ");
        content.add("%%CLASS_NAME%%");
        if (apiClass.getInstantiable()) {
            if (!apiClass.hasParent()) {
                content.add(" < handle & matlab.mixin.SetGet");
            } else {
                content.add(" < %%PARENT_NAME%%");
            }
        }
        content.add("\n");

        if (apiClass.getType().hasComment()) {
            content.add(DocumentationUtil.generateMatlabdoc("", apiClass.getType()));
        }

        content.indent();

        // Static methods
        var staticMethods = getMethods(apiClass, m -> m.getIsStatic() && !m.getIsConstructor());
        if (!staticMethods.isEmpty()) {
            content.beginControlFlow("\nmethods(Static)\n");
            staticMethods.forEach(m -> addWrapperMethod(apiClass, m, content.snapshot()));
            content.endControlFlow("end");
        }

        // Dependent properties
        var properties = GeneratorUtil.getPropertyNames(apiClass);
        if (!properties.isEmpty()) {
            content.beginControlFlow("\nproperties (Dependent)");
            for (var property : properties) {
                content.addLine(property);
            }
            content.endControlFlow("end");
        }

        // Instance methods
        var instanceMethods = getMethods(apiClass, not(MethodDescription::getIsStatic));
        if (!instanceMethods.isEmpty()) {
            content.beginControlFlow("\nmethods\n");
            instanceMethods.forEach(m -> addWrapperMethod(apiClass, m, content.snapshot()));
            content.endControlFlow("end");
        }

        // Constructor and other stuff
        addConstructorSection(apiClass, content.snapshot());
        addBoilerplateCode(apiClass, content.snapshot());

        return content.endControlFlow("\nend").stripTrailingWhitespace().render();

    }

    private void addConstructorSection(ClassDescription apiClass, IndentingTextRenderer content) {
        if (!apiClass.getInstantiable()) return;
        var constructors = getMethods(apiClass, MethodDescription::getIsConstructor);
        content.addLine("");

        // Default constructor for C pointers
        if (constructors.isEmpty()) {

            if (apiClass.hasParent()) {
                content.add("""
                        methods(Access = public, Hidden = true)
                            function this = %%CLASS_NAME%%(%%REF_FIELD%%, varargin)
                                this@%%PARENT_NAME%%(%%DISPATCH%%(%%CAST%%, %%REF_FIELD%%));
                                this.%%REF_FIELD%% = %%REF_FIELD%%;
                                if numel(varargin) > 1 % Call setters from the leaf to make sure that the ref field is set
                                    this.set(varargin{:});
                                end
                            end
                        end
                        """);
            } else {
                content.add("""
                        methods(Access = public, Hidden = true)
                            function this = %%CLASS_NAME%%(%%REF_FIELD%%, varargin)
                                if ~isnumeric(%%REF_FIELD%%) && ~isa(%%REF_FIELD%%, 'lib.pointer')
                                    error('%%CLASS_NAME%% constructor expects a C pointer type');
                                end
                                this.%%REF_FIELD%% = %%REF_FIELD%%;

                                % Apply name-value pairs to properties, if provided
                                if numel(varargin) > 1
                                    this.set(varargin{:});
                                end
                            end
                        end
                        """);
            }

            return;

        }

        // Currently real constructors are only supported at the leaf nodes as we'd have to overload the first argument
        checkState(constructors.size() == 1, "A class can only have one constructor");
        checkState(!apiClass.hasChildren(), "Only leaf nodes can have defined constructors");
        content.beginControlFlow("methods(Access = public)");
        addWrapperMethod(apiClass, constructors.get(0), content.snapshot());
        content.stripTrailingWhitespace().endControlFlow("\nend");

    }

    private void addBoilerplateCode(ClassDescription apiClass, IndentingTextRenderer content) {
        if (!apiClass.getInstantiable()) {
            return;
        }

        // Every class keeps a ref to its own pointer
        content.add("""
                properties (SetAccess = private, GetAccess = private, Hidden = true)
                    %%REF_FIELD%% = [];
                end

                methods(Access = public, Hidden = true)

                    function ptr = %%GET_REF_METHOD%%(this)
                        ptr = this.%%REF_FIELD%%;
                    end
                """);

        content.indent();
        if (!apiClass.hasParent()) {
            // the parent also needs to implement the destructor and hide the handle methods from the docs
            content.add("""

                    function delete(this) % destructor
                         if ~isempty(this.%%REF_FIELD%%)
                            %%DISPATCH%%(%%C_DESTRUCTOR%%, this.%%REF_FIELD%%);
                            this.%%REF_FIELD%% = [];
                        end
                    end

                    % Inherited handle methods that should be omitted from auto - complete
                    function varargout = addlistener(varargin)
                        varargout{:} = addlistener@handle(varargin{:});
                    end
                    function varargout = eq(varargin)
                        varargout{:} = eq@handle(varargin{:});
                    end
                    function varargout = findobj(varargin)
                        varargout{:} = findobj@handle(varargin{:});
                    end
                    function varargout = findprop(varargin)
                        varargout{:} = findprop@handle(varargin{:});
                    end
                    function varargout = ge(varargin)
                        varargout{:} = ge@handle(varargin{:});
                    end
                    function varargout = gt(varargin)
                        varargout{:} = gt@handle(varargin{:});
                    end
                    function varargout = le(varargin)
                        varargout{:} = le@handle(varargin{:});
                    end
                    function varargout = listener(varargin)
                        varargout{:} = listener@handle(varargin{:});
                    end
                    function varargout = lt(varargin)
                        varargout{:} = lt@handle(varargin{:});
                    end
                    function varargout = ne(varargin)
                        varargout{:} = ne@handle(varargin{:});
                    end
                    function varargout = notify(varargin)
                        varargout{:} = notify@handle(varargin{:});
                    end
                    """);
        }

        content.unindent().addLine("\nend");

    }

// =========================================================================
// Method wrapper
// =========================================================================

    private void addWrapperMethod(ClassDescription apiClass, MethodDescription method, IndentingTextRenderer
            content) {
        var params = new ParameterLists(lib, method);

        // Property accessor naming
        String funcName = method.getName();
        if (GeneratorUtil.isSetter(method)) {
            funcName = "set." + method.getPropertyName();
        } else if (GeneratorUtil.isGetter(method)) {
            funcName = "get." + method.getPropertyName();
        } else if (GeneratorUtil.isConstructor(method)) {
            funcName = "%%CLASS_NAME%%";
        }
        content.set("FUNC_NAME", funcName);
        content.set("CFUNC_NAME", method.getCName());
        content.set("EXTERNAL_ARGS", joiner.join(params.externalInput));
        content.set("INTERNAL_ARGS", joiner.join(params.internalInput));
        content.set("RETURN_TYPE", getFullyQualifiedClassName(method.getReturnType()));

        // MATLAB requires an explicit superclass constructor call before 'this' can be assigned
        content.set("SUPER_CTOR", GeneratorUtil.isConstructor(method) && apiClass.hasParent()
                ? "this@%%PARENT_NAME%%(%%DISPATCH%%(%%CAST%%, ptr_));\n"
                : "");

        // Build the internal function
        content.add("function ").add(wrapOutputAssignment(params.externalOutput)).addLine("%%FUNC_NAME%%(%%EXTERNAL_ARGS%%)").indent();
        if (method.hasDocumentation()) {
            content.addLine(DocumentationUtil.generateMatlabdoc("", method));
        }

        // Anything that has to happen before the call
        var overload = method.getMatlabOverload();
        overload.tryGetBody().map(String::stripTrailing).ifPresent(content::addLine);
        if (!params.preCallActions.isEmpty()) content.addLine(params.preCallActions.toString().stripTrailing());
        overload.tryGetPostBody().map(String::stripTrailing).ifPresent(content::addLine);

        // The actual C call
        content.add(wrapOutputAssignment(params.internalOutput))
                .addLine("%%DISPATCH%%(%%INTERNAL_ARGS%%);");

        // Anything that has to happen after the call
        if (!params.postCallActions.isEmpty()) content.addLine(params.postCallActions.toString().stripTrailing());
        overload.tryGetPostCall().map(String::stripTrailing).ifPresent(content::addLine);

        content.endControlFlow("end\n");

    }

    /**
     * C string defaults arrive as a quoted literal, e.g. "Hello". MATLAB needs a single quoted
     * char array, as the MEX gateway rejects the string objects that double quotes create.
     */
    static String convertCDefaultParamString(TypeDescription param) {
        checkArgument(param.hasDefaultValue(), "Missing default value for c_default_param");
        var value = param.getDefaultValue();
        if (param.getDataType() == DataType.CHAR_POINTER && value.length() >= 2
            && value.startsWith("\"") && value.endsWith("\"")) {
            return quoted(value.substring(1, value.length() - 1).replace("'", "''"));
        }
        return value;
    }

    /**
     * The default value as MATLAB should see it.
     * <p>
     * Defaults are stored zero based, like everything else in the descriptor model. An
     * index default therefore has to shift into MATLAB's own indexing here, so that the
     * conversion below can subtract unconditionally instead of special casing whether the
     * argument was supplied. Emitting the default in Java's space and then converting it
     * would decrement it a second time.
     */
    static String getMatlabDefaultValue(TypeDescription param) {
        var value = convertCDefaultParamString(param);
        if (!param.getIsIndex()) {
            return value;
        }
        // Guaranteed to parse: the processor rejects a non-integer default on an index
        return Long.toString(Long.parseLong(value.trim()) + 1);
    }

    /**
     * Converts a one-based MATLAB index to the zero-based value the C ABI expects. Applies
     * to supplied and defaulted arguments alike, because {@link #getMatlabDefaultValue} has
     * already put the default into the same space.
     */
    static String getIndexInputConversion(TypeDescription param) {
        return "%1$s = %1$s - 1;\n".formatted(param.getParamName());
    }

    static String wrapOutputAssignment(List<String> list) {
        return switch (list.size()) {
            case 0 -> "";
            case 1 -> "~".equalsIgnoreCase(list.get(0)) ? "" : list.get(0) + " = ";
            default -> "[" + joiner.join(list) + "] = ";
        };
    }

// =========================================================================
// ParameterLists
// =========================================================================

    /**
     * Builds the external (MATLAB signature) and internal parameter lists,
     * plus any output conversion code needed after the C call.
     */
    static class ParameterLists {

        final List<String> externalInput = new ArrayList<>();
        final List<String> externalOutput = new ArrayList<>();
        final List<String> internalInput = new ArrayList<>();
        final List<String> internalOutput = new ArrayList<>();
        final StringBuilder preCallActions = new StringBuilder();
        final StringBuilder postCallActions = new StringBuilder();

        ParameterLists(LibraryDescription lib, MethodDescription method) {
            final boolean needsSelfRef = !method.getIsStatic() && !method.getIsConstructor();
            final boolean needsVarargin = method.getReturnType().getDataType() == DataType.OBJECT_HANDLE
                                          && !GeneratorUtil.getSetters(lib, method.getReturnType()).isEmpty();

            // native dispatch always needs the library ref and function name
            internalInput.add(getDispatchIdentifier(method.getCName()));

            // Return type handling. calllib returns the return first and then the original arguments
            switch (method.getReturnType().getDataType()) {

                case OBJECT_HANDLE -> {
                    internalOutput.add("ptr_");

                    postCallActions.append("""
                            if isempty(ptr_)
                                error('Failed to create %%RETURN_TYPE%% in %%CLASS_NAME%%.%%FUNC_NAME%%');
                            end
                            """);
                    if (GeneratorUtil.isConstructor(method)) {
                        externalOutput.add("this");
                        postCallActions.append("%%SUPER_CTOR%%this.%%REF_FIELD%% = ptr_;\n");
                        if (needsVarargin) {
                            postCallActions.append("""
                                    if numel(varargin) > 0
                                        this.set(varargin{:});
                                    end
                                    """);
                        }

                    } else {
                        externalOutput.add("obj");
                        if (needsVarargin) {
                            postCallActions.append("obj = %%RETURN_TYPE%%(ptr_, varargin{:});\n");
                        } else {
                            postCallActions.append("obj = %%RETURN_TYPE%%(ptr_);\n");
                        }
                    }

                }

                case VOID -> {
                    // nothing to do
                }

                default -> {
                    String result = findUniqueOutputParam(method);
                    internalOutput.add(result);
                    externalOutput.add(result);
                    if (method.getReturnType().getIsIndex()) {
                        postCallActions.append("%1$s = %1$s + 1;\n".formatted(result));
                    }
                    // TODO: add throwing checks for the other types?
                }
            }

            // instance methods also need the obj ref (echoed as ~ in output)
            if (needsSelfRef) {
                externalInput.add("this");
                internalInput.add("this.%%REF_FIELD%%");
                internalOutput.add("~");
            }

            // TODO: handle optional arguments combined with varargin

            // Check if the external arguments are overloaded
            boolean isMultiArgsSetter = GeneratorUtil.isSetter(method) && method.getParameters().length() > 1;
            var overload = method.getMatlabOverload();
            if (overload.hasParameters()) {
                // Copy as user specified
                externalInput.add(overload.getParameters());

            } else if (isMultiArgsSetter) {
                // Convert multi args setter to tuples
                String prop = method.getPropertyName();
                int count = method.getParameters().length();
                externalInput.add(prop);

                StringBuilder unpack = new StringBuilder();
                List<String> names = new ArrayList<>();
                int i = 0;
                for (var p : method.getParameters()) {
                    names.add(p.getParamName());
                    unpack.append("    ").append(p.getParamName()).append(" = ")
                            .append(prop).append("(").append(++i).append(")").append(";\n");
                }

                // MATLAB doesn't have native tuples, so we need to handle both cells and arrays
                preCallActions.append("""
                        if numel(%1$s) ~= %2$d
                            error('Property "%1$s" requires %2$d elements');
                        end
                        if iscell(%1$s)
                            [%3$s] = %1$s{:};
                        else
                        %4$s
                        end
                        """.formatted(prop, count, joiner.join(names), unpack.toString().stripTrailing()));

            } else {

                // Optional args overlap with varargin, so we can need to merge varargin
                int handledArgsIx = 0;
                for (var param : method.getParameters()) {
                    if (param.hasDefaultValue()) {
                        break;
                    }
                    externalInput.add(param.getParamName());
                    handledArgsIx++;
                }

                if (!needsVarargin) {

                    // No need to merge, so we can just make optional params named
                    int argsOffset = needsSelfRef ? 2 : 1;
                    for (int i = handledArgsIx; i < method.getParameters().length(); i++) {
                        var param = method.getParameters().get(i);
                        checkArgument(param.hasDefaultValue(), "There can be no required after optional parameters");
                        externalInput.add(param.getParamName());
                        preCallActions.append("""
                                if nargin < %d
                                    %s = %s;
                                end
                                """.formatted(argsOffset + i, param.getParamName(), getMatlabDefaultValue(param)));
                    }

                } else {

                    // Here it gets tricky since we need to get the optional arguments from varargin
                    externalInput.add("varargin");
                    for (int i = handledArgsIx; i < method.getParameters().length(); i++) {
                        var param = method.getParameters().get(i);
                        checkArgument(param.hasDefaultValue(), "There can be no required after optional parameters");
                        preCallActions.append("""
                                %1$s = %2$s;
                                if ~isempty(varargin) && ~ischar(varargin{1}) && ~isstring(varargin{1})
                                    %1$s = varargin{1};
                                    varargin(1) = [];
                                end
                                """.formatted(param.getParamName(), getMatlabDefaultValue(param)));
                    }

                }

            }

            // MATLAB counts from one. Index parameters convert here, which is after any
            // default has been applied and before a user supplied postBody runs. An overload
            // that replaces the signature or the call site owns the conversion itself.
            if (!overload.hasParameters() && !overload.hasCall()) {
                for (var param : method.getParameters()) {
                    if (param.getIsIndex()) {
                        preCallActions.append(getIndexInputConversion(param));
                    }
                }
            }

            // Check if the arguments for the internal call site are overloaded
            if (overload.hasCall()) {
                internalInput.add(overload.getCall());
            } else {
                for (var param : method.getParameters()) {
                    internalInput.add(switch (param.getDataType()) {
                        case OBJECT_HANDLE -> param.getParamName() + ".%%GET_REF_METHOD%%()"; // pass C pointer
                        case ENUM -> getFullyQualifiedType(lib, param) + ".toNativeValue(" + param.getParamName() + ")";
                        default -> param.getParamName();
                    });
                }
            }

            // Pointer parameters are caller-owned buffers that the native side mutates in place,
            // same as in C. The dispatch never echoes them back, so they are always ignored.
            for (TypeDescription parameter : method.getParameters()) {
                if (parameter.getDataType() == DataType.OBJECT_HANDLE
                        || TypeMap.isPointerType(parameter.getDataType())) {
                    internalOutput.add("~");
                }
            }

            // setters can never have output
            if (GeneratorUtil.isSetter(method)) {
                externalOutput.clear();
            }

            // remove otherwise unnecessary outputs
            removeLastIgnored(internalOutput);
            removeLastIgnored(externalOutput);

        }

        private void removeLastIgnored(List<String> list) {
            // We can always safely ignore the last output params
            while (!list.isEmpty() && "~".equals(list.getLast())) {
                list.remove(list.size() - 1);
            }
        }

        private static String findUniqueOutputParam(MethodDescription desc) {
            Predicate<String> isUsed = name -> {
                for (TypeDescription p : desc.getParameters()) {
                    if (name.equalsIgnoreCase(p.getParamName())) return true;
                }
                return false;
            };

            if (!isUsed.test("result")) return "result";

            var name = "out";
            while (isUsed.test(name)) name += "_";
            return name;
        }
    }

    private String getFullyQualifiedClassName(TypeDescription type) {
        return getFullyQualifiedType(lib, type);
    }

    private static String getMatlabClassName(ClassDescription desc) {
        return getMatlabClassName(desc.getType());
    }

    private static String getMatlabEnumClassName(EnumDescription desc) {
        return desc.getName();
    }

    private static String getMatlabClassName(TypeDescription desc) {
        return desc.getTypeName();
    }

    private static String getFullyQualifiedType(LibraryDescription lib, TypeDescription type) {
        return lib.getName() + "." + getMatlabClassName(type);
    }

    private static EnumDescription getEnumDescriptionFor(LibraryDescription lib, TypeDescription type) {
        for (var desc : lib.getEnums()) {
            if (desc.getName().equals(type.getTypeName())) {
                return desc;
            }
        }
        throw new IllegalArgumentException("No enum found for type " + type.getTypeName());
    }

    // ---- JSON function signatures for auto-complete ----

    private String createFunctionSignaturesSource() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("_schemaVersion", "1.0.0");

        for (var apiClass : lib.getClasses()) {
            String classPrefix = getFullyQualifiedType(lib, apiClass.getType());
            for (var method : apiClass.getMethods()) {
                if (GeneratorUtil.isAccessor(method)) continue;

                Map<String, Object> methodData = new LinkedHashMap<>();
                List<Map<String, Object>> inputs = new ArrayList<>();

                // Return type
                if (!TypeMap.isVoid(method.getReturnType().getDataType())) {
                    var hints = mapToMatlabType(method.getReturnType());
                    if (!"numeric".equals(hints.get(0))) { // MATLAB does not like hints to default
                        methodData.put("outputs", Arrays.asList(Map.of(
                                "name", "obj",
                                "type", hints)
                        ));
                    }
                }

                // Instance methods need 'this'
                if (!method.getIsStatic() && !method.getIsConstructor()) {
                    inputs.add(Map.of("name", "this", "type", classPrefix));
                }

                boolean isMultiArgsSetter = GeneratorUtil.isSetter(method) && method.getParameters().length() > 1;
                if (isMultiArgsSetter) {
                    // Boxed parameters
                    inputs.add(Map.of(
                            "name", method.getPropertyName(),
                            "type", Arrays.asList("numeric", "cell"),
                            "kind", "required"
                    ));
                } else {

                    // Normally defined parameters
                    for (var param : method.getParameters()) {
                        inputs.add(Map.of(
                                "name", param.getParamName(),
                                "type", mapToMatlabType(param),
                                "kind", param.hasDefaultValue() ? "ordered" : "required"
                        ));
                    }

                    // Varargs
                    var setters = GeneratorUtil.getSetters(lib, method.getReturnType());
                    for (var setter : setters) {
                        inputs.add(Map.of(
                                "name", setter.getPropertyName(),
                                "type", mapToMatlabType(setter.getParameters().get(0)),
                                "kind", "namevalue"
                        ));
                    }

                }

                methodData.put("inputs", inputs);

                // Key format: package.Class.method
                String key = classPrefix;
                if (!method.getIsConstructor()) {
                    key += "." + method.getName();
                }
                root.put(key, methodData);
            }
        }

        return mapToJson(root);
    }

    private List<String> mapToMatlabType(TypeDescription type) {
        return switch (type.getDataType()) {
            case BOOL -> List.of("logical");
            case INT8, INT16, INT32, INT64, FLOAT32, FLOAT64, SIZE_T,
                 INT16_POINTER, INT32_POINTER, INT64_POINTER, FLOAT32_POINTER, FLOAT64_POINTER -> List.of("numeric");
            case CHAR_POINTER -> List.of("char");
            case CHAR_POINTER_POINTER -> List.of("cell");
            case STRUCT -> List.of(getMexMarshaller(type)
                    .map(StructMarshaller.MexStructMarshaller::typeHint)
                    .orElse("struct"));
            case OBJECT_HANDLE -> List.of(getFullyQualifiedClassName(type));
            case ENUM -> List.of(
                    getFullyQualifiedClassName(type), "choices={" + getEnumChoices(type) + "}");
            default -> List.of("any");
        };
    }

    private String getEnumChoices(TypeDescription type) {
        // MATLAB only supports string completion, so we map to the
        // original strings without prefix and map them internally.
        var desc = getEnumDescriptionFor(lib, type);
        List<String> choices = new ArrayList<>();
        for (var value : desc.getValues()) {
            choices.add("'" + value.getName() + "'");
        }
        return joiner.join(choices);
    }

    private String mapToJson(Object obj) {
        return mapToJson(obj, 0);
    }

    // Pretty print. Generated by LLM
    private String mapToJson(Object obj, int indent) {
        String space = "  ".repeat(indent);
        String nextSpace = "  ".repeat(indent + 1);

        if (obj instanceof Map<?, ?> map) {
            return map.entrySet().stream()
                    .sorted(Comparator.comparing(e -> e.getKey().toString()))
                    .map(e -> "\n" + nextSpace + "\"" + e.getKey() + "\": " + mapToJson(e.getValue(), indent + 1))
                    .collect(Collectors.joining(",", "{", "\n" + space + "}"));
        } else if (obj instanceof List<?> list) {
            return list.stream()
                    .map(item -> mapToJson(item, indent + 1))
                    .collect(Collectors.joining(", ", "[", "]"));
        } else if (obj instanceof String s) {
            return "\"" + s.replace("\"", "\\\"") + "\"";
        }
        return String.valueOf(obj);
    }

}
