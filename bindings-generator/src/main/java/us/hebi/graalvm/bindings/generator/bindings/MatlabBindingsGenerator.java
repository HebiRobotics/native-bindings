
package us.hebi.graalvm.bindings.generator.bindings;

import com.google.common.base.Joiner;
import us.hebi.graalvm.bindings.generator.bindings.marshallers.StructMarshallers;
import us.hebi.graalvm.bindings.generator.generators.DocumentationUtil;
import us.hebi.graalvm.bindings.generator.generators.GeneratorUtil;
import us.hebi.graalvm.bindings.generator.generators.IndentingTextRenderer;
import us.hebi.graalvm.bindings.model.*;

import java.io.IOException;
import java.util.*;
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
        this.callbacks = new MatlabCallbackGenerator(lib, this::getCToMex);
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
        if (param.getDataType() == DataType.CALLBACK) {
            return TypeMap.toExternalC(param, false); // the gateway declares the callback typedefs itself
        }
        if (param.getDataType() == DataType.STRUCT) {
            return param.getTypeName(); // layout-compatible local struct generated into the MEX gateway
        }
        return (param.getIsConst() ? "const " : "") + TypeMap.toExternalC(param.getDataType());
    }

    /**
     * The statement (without semicolon) that turns argument prhs[index] into a C value named
     * after the parameter. A receiver has no name and converts to an inline expression.
     */
    private String getMexToC(TypeDescription param, int index) {
        String name = param.getParamName();
        String type = getMexCType(param);
        String arg = "prhs[" + index + "]";
        return switch (param.getDataType()) {
            case CALLBACK -> "const mxArray* " + name + "Handle = getFunctionHandle(" + arg + ", \"" + name + "\")";

            case OBJECT_HANDLE, VOID_POINTER ->
                    (param.hasParamName() ? type + " " + name + " = " : "") + "getPtr<" + type + ">(" + arg + ")";

            case ENUM, BOOL, INT8, INT16, INT32, INT64,
                 FLOAT32, FLOAT64, SIZE_T, CHAR16 -> type + " " + name + " = getScalar<" + type + ">(" + arg + ", \"" + name + "\")";

            // RAII views over the MATLAB array, valid for the duration of the call
            case FLOAT64_POINTER -> "MatlabArray<double, mxDOUBLE_CLASS> " + name + "(" + arg + ")";
            case FLOAT32_POINTER -> "MatlabArray<float, mxSINGLE_CLASS> " + name + "(" + arg + ")";
            case INT64_POINTER -> "MatlabArray<long long, mxINT64_CLASS> " + name + "(" + arg + ")"; // the C face says long long*, and int64_t is long on Linux
            case INT32_POINTER -> "MatlabArray<int32_t, mxINT32_CLASS> " + name + "(" + arg + ")";
            case INT16_POINTER -> "MatlabArray<int16_t, mxINT16_CLASS> " + name + "(" + arg + ")";
            case UINT8_POINTER -> "MatlabArray<uint8_t, mxUINT8_CLASS> " + name + "(" + arg + ")";
            case CHAR_POINTER -> "MatlabString " + name + "(" + arg + ")";
            case CHAR_POINTER_POINTER -> "MatlabStringArray " + name + "(" + arg + ")";

            case STRUCT -> getMexMarshaller(param)
                    .map(marshaller -> marshaller.read(arg, name))
                    .orElseGet(() -> type + " " + name + " = get" + type + "(" + arg + ")");

            default -> throw new IllegalStateException("Unsupported input type: " + param.getDataType());
        };
    }

    String getCToMex(TypeDescription type) {
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

            // User structs (copied into a 1x1 MATLAB struct)
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
        // Find a collision-free hash seed
        List<String> symbols = GeneratorUtil.getAllCMethodSymbols(lib);
        long prime = 0x01000193L;
        long seed = findCollisionFreeSeed(symbols, 0x811c9dc5L, prime);
        Function<String, String> hash = cname -> String.valueOf(fnv1a_32_ascii(cname, seed, prime));

        var cases = withLibVars().indent(2);
        for (ClassDescription clazz : lib.getClasses()) {
            cases.addLine().addLine("// " + getMatlabClassName(clazz));
            for (MethodDescription method : clazz.getMethods()) {
                if (callbacks.getMatlabExclusionReason(method).isPresent()) continue;
                var dispatch = new DispatchCase(method.getCName(), method.getReturnType(), isThrowing(method));
                if (!method.getIsStatic()) {
                    dispatch.addInstance(clazz.getType());
                }
                for (var param : method.getParameters()) {
                    dispatch.addParameter(method, param);
                }
                dispatch.write(cases, hash);
            }

            // Generated plumbing that the m-files call
            if (clazz.getInstantiable()) {
                if (clazz.hasCDestructorName()) {
                    var voidType = TypeDescription.newInstance().setDataType(DataType.VOID);
                    new DispatchCase(clazz.getCDestructorName(), voidType, false)
                            .addInstance(clazz.getType())
                            .write(cases, hash);
                }
                if (clazz.hasToParentCastMethod()) {
                    var cast = clazz.getToParentCastMethod();
                    new DispatchCase(cast.getCName(), cast.getReturnType(), false)
                            .addInstance(clazz.getType())
                            .write(cases, hash);
                }
            }
        }

        // Generate
        return withLibVars()
                .set("HASH_SEED", String.format("0x%08X", seed))
                .set("HASH_PRIME", String.format("0x%08X", prime))
                .set("CALLBACK_TYPEDEFS", callbacks.createCallbackTypedefs())
                .set("CALLBACK_SUPPORT", callbacks.createCallbackSupport())
                .set("CALLBACK_SCOPES", callbacks.createCallbackScopes())
                .set("STRUCT_HELPERS", createStructHelpers())
                .set("STRUCT_RETURN_HELPERS", createStructReturnHelpers())
                .set("SWITCH_CASES", cases.render())
                .addResourceContent(getClass(), "matlab/libName_native.cpp")
                .render();
    }

    /**
     * One case of the dispatch switch: the argument conversions, the call, and the result.
     * Arguments follow the function name, so the first one is prhs[1].
     */
    private final class DispatchCase {

        DispatchCase(String cName, TypeDescription returnType, boolean throwing) {
            this.cName = cName;
            this.returnType = returnType;
            this.throwing = throwing;
            if (throwing) {
                types.add("ErrorInfo*");
                args.add("&_error");
            }
        }

        /**
         * The receiver, converted inline
         */
        DispatchCase addInstance(TypeDescription type) {
            types.add(getMexCType(type));
            args.add(getMexToC(type, nextIndex++));
            return this;
        }

        /**
         * A declared parameter, converted into a local ahead of the call
         */
        DispatchCase addParameter(MethodDescription method, TypeDescription param) {
            types.add(getMexCType(param));
            conversions.add(getMexToC(param, nextIndex++) + ";");
            if (param.getDataType() == DataType.CALLBACK) {
                String scope = MatlabCallbackGenerator.getCallbackScopeName(param.getTypeName())
                               + "<" + MatlabCallbackGenerator.getCallbackSite(method, param) + ">";
                callbackArgs.add(new CallbackArg(param.getParamName(), scope));
                args.add(param.getParamName() + ".function()");
            } else {
                args.add(param.getParamName());
            }
            return this;
        }

        void write(IndentingTextRenderer cases, Function<String, String> hash) {
            boolean returnsValue = returnType.getDataType() != DataType.VOID;
            var out = cases.snapshot()
                    .set("CNAME", cName)
                    .set("HASH", hash.apply(cName))
                    .set("RETURN_TYPE", getMexCType(returnType))
                    .set("RETURN_VALUE", "_value")
                    .set("ARG_COUNT", nextIndex - FIRST_INDEX)
                    .setArgs("TYPES", types)
                    .setArgs("ARGS", args);

            out.beginControlFlow("case /* hash32_fnv1a(\"%%CNAME%%\") */ %%HASH%%: {");
            out.addLine("static auto %%CNAME%% = libLoader.getFunc<%%RETURN_TYPE%%(*)(%%TYPES%%)>(\"%%CNAME%%\");");
            out.addLine("checkArgCount(nrhs, %%ARG_COUNT%%, \"%%CNAME%%\");");

            // The error struct is filled by the call and raises before any output is built
            out.addLineIf(throwing, "ErrorInfo _error = {};");
            for (var conversion : conversions) {
                out.addLine(conversion);
            }

            if (callbackArgs.isEmpty()) {
                out.addLine((returnsValue ? "%%RETURN_TYPE%% %%RETURN_VALUE%% = " : "") + "%%CNAME%%(%%ARGS%%);");
            } else {
                // mexErrMsgIdAndTxt longjmps past destructors, so nothing in the block may raise
                out.addLineIf(returnsValue, "%%RETURN_TYPE%% %%RETURN_VALUE%%;");
                for (var callback : callbackArgs) {
                    out.addLine("CallbackOutcome " + callback.name() + "Outcome;");
                }
                out.beginControlFlow("{");
                for (var callback : callbackArgs) {
                    out.addLine(callback.scopeType() + " " + callback.name() + "(" + callback.name() + "Handle);");
                }
                out.addLine((returnsValue ? "%%RETURN_VALUE%% = " : "") + "%%CNAME%%(%%ARGS%%);");
                for (var callback : callbackArgs) {
                    out.addLine(callback.name() + "Outcome = " + callback.name() + ".outcome();");
                }
                out.endControlFlow("}");
                for (var callback : callbackArgs) {
                    out.addLine("checkCallbackError(" + callback.name() + "Outcome);");
                }
            }

            // Checked after the callback error, which is the more likely root cause
            out.addLineIf(throwing, "checkError(_error);");
            out.addLineIf(returnsValue, () -> "if (nlhs > 0) plhs[0] = " + getCToMex(returnType) + ";");
            out.addLine("break;");
            out.endControlFlow("}");
        }

        private final String cName;
        private final TypeDescription returnType;
        private final boolean throwing;
        private final List<String> types = new ArrayList<>();
        private final List<String> args = new ArrayList<>();
        private final List<String> conversions = new ArrayList<>();
        private final List<CallbackArg> callbackArgs = new ArrayList<>();
        private int nextIndex = FIRST_INDEX;

        private static final int FIRST_INDEX = 1;

    }

    /**
     * A callback parameter of a dispatched call and the scope type of its site
     */
    private record CallbackArg(String name, String scopeType) {
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
                content.addLine(TypeMap.toStructFieldC(field.getDataType(), field.getIsConst()) + " " + field.getLowerUnderscoreName() + ";");
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
                        : "getStructField<" + TypeMap.toStructFieldC(field.getDataType(), false)
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
                    .addLine("const char* fields[] = {" + String.join(", ", names) + "};")
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
            default -> "{" + String.join(", ", options) + "}";
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
            content.add(DocumentationUtil.generateMatlabdoc(apiClass.getType()));
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
        var exclusionReason = callbacks.getMatlabExclusionReason(method);
        if (exclusionReason.isPresent()) {
            addUnsupportedStub(apiClass, method, exclusionReason.get(), content);
            return;
        }

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
        content.setArgs("EXTERNAL_ARGS", params.externalInput);
        content.setArgs("INTERNAL_ARGS", params.internalInput);
        content.set("RETURN_TYPE", getFullyQualifiedClassName(method.getReturnType()));

        // MATLAB requires an explicit superclass constructor call before 'this' can be assigned
        content.set("SUPER_CTOR", GeneratorUtil.isConstructor(method) && apiClass.hasParent()
                ? "this@%%PARENT_NAME%%(%%DISPATCH%%(%%CAST%%, ptr_));\n"
                : "");

        // Build the internal function
        content.add("function ").add(wrapOutputAssignment(params.externalOutput)).addLine("%%FUNC_NAME%%(%%EXTERNAL_ARGS%%)").indent();
        if (method.hasDocumentation()) {
            content.addLine(DocumentationUtil.generateMatlabdoc(method));
        }

        // Anything that has to happen before the call
        var overload = method.getMatlabOverload();
        overload.tryGetBody().map(String::stripTrailing).ifPresent(content::addLine);
        content.add(params.preCallActions.render());
        overload.tryGetPostBody().map(String::stripTrailing).ifPresent(content::addLine);

        // The actual C call
        content.add(wrapOutputAssignment(params.internalOutput))
                .addLine("%%DISPATCH%%(%%INTERNAL_ARGS%%);");

        // Anything that has to happen after the call
        content.add(params.postCallActions.render());
        overload.tryGetPostCall().map(String::stripTrailing).ifPresent(content::addLine);

        content.endControlFlow("end\n");

    }

    /**
     * The m-file stub for a method that {@link MatlabCallbackGenerator#getMatlabExclusionReason}
     * rejected. It takes varargin so that any call reaches the error rather than a count mismatch.
     */
    private void addUnsupportedStub(ClassDescription apiClass, MethodDescription method, String reason,
                                    IndentingTextRenderer content) {
        boolean isConstructor = GeneratorUtil.isConstructor(method);
        content.set("FUNC_NAME", isConstructor ? getMatlabClassName(apiClass) : method.getName());
        content.set("OUTPUT", isConstructor ? "this" : "varargout");
        content.set("REASON", reason);

        var help = new StringBuilder();
        if (method.hasDocumentation()) {
            help.append(method.getDocumentation().trim()).append("\n\n");
        }
        help.append("Not supported from MATLAB: %%REASON%%.");

        content.beginControlFlow("function %%OUTPUT%% = %%FUNC_NAME%%(varargin)");
        content.addLine(DocumentationUtil.wrapInMatlabDoc(help.toString()));
        content.addLine("error('%%LIB_NAME%%:NotSupported', \"'%%FUNC_NAME%%' is not supported from MATLAB: %%REASON%%.\");");
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
     * The default as MATLAB sees it. Defaults are stored zero based, so an index default shifts
     * here and {@link #getIndexInputConversion} subtracts unconditionally, converting the emitted
     * default instead would decrement it twice.
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
        String name = param.getParamName();
        return name + " = " + name + " - 1;\n";
    }

    static String wrapOutputAssignment(List<String> list) {
        return switch (list.size()) {
            case 0 -> "";
            case 1 -> "~".equalsIgnoreCase(list.get(0)) ? "" : list.get(0) + " = ";
            default -> "[" + String.join(", ", list) + "] = ";
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
        final IndentingTextRenderer preCallActions = new IndentingTextRenderer();
        final IndentingTextRenderer postCallActions = new IndentingTextRenderer();

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

                    postCallActions.add("""
                            if isempty(ptr_)
                                error('Failed to create %%RETURN_TYPE%% in %%CLASS_NAME%%.%%FUNC_NAME%%');
                            end
                            """);
                    if (GeneratorUtil.isConstructor(method)) {
                        externalOutput.add("this");
                        postCallActions.addLine("%%SUPER_CTOR%%this.%%REF_FIELD%% = ptr_;");
                        if (needsVarargin) {
                            postCallActions.add("""
                                    if numel(varargin) > 0
                                        this.set(varargin{:});
                                    end
                                    """);
                        }

                    } else {
                        externalOutput.add("obj");
                        postCallActions.addLine(needsVarargin
                                ? "obj = %%RETURN_TYPE%%(ptr_, varargin{:});"
                                : "obj = %%RETURN_TYPE%%(ptr_);");
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
                        postCallActions.addLine(result + " = " + result + " + 1;");
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

                List<String> names = new ArrayList<>();
                for (var p : method.getParameters()) {
                    names.add(p.getParamName());
                }

                // MATLAB doesn't have native tuples, so we need to handle both cells and arrays
                preCallActions.set("PROP", prop)
                        .set("COUNT", count)
                        .setArgs("NAMES", names)
                        .add("""
                                if numel(%%PROP%%) ~= %%COUNT%%
                                    error('Property "%%PROP%%" requires %%COUNT%% elements');
                                end
                                if iscell(%%PROP%%)
                                    [%%NAMES%%] = %%PROP%%{:};
                                else
                                """).indent();
                for (int i = 0; i < names.size(); i++) {
                    preCallActions.addLine(names.get(i) + " = %%PROP%%(" + (i + 1) + ");");
                }
                preCallActions.unindent().addLine("end");

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
                        preCallActions.set("ARG_INDEX", argsOffset + i)
                                .set("NAME", param.getParamName())
                                .set("DEFAULT", getMatlabDefaultValue(param))
                                .add("""
                                        if nargin < %%ARG_INDEX%%
                                            %%NAME%% = %%DEFAULT%%;
                                        end
                                        """);
                    }

                } else {

                    // Here it gets tricky since we need to get the optional arguments from varargin
                    externalInput.add("varargin");
                    for (int i = handledArgsIx; i < method.getParameters().length(); i++) {
                        var param = method.getParameters().get(i);
                        checkArgument(param.hasDefaultValue(), "There can be no required after optional parameters");
                        preCallActions.set("NAME", param.getParamName())
                                .set("DEFAULT", getMatlabDefaultValue(param))
                                .add("""
                                        %%NAME%% = %%DEFAULT%%;
                                        if ~isempty(varargin) && ~ischar(varargin{1}) && ~isstring(varargin{1})
                                            %%NAME%% = varargin{1};
                                            varargin(1) = [];
                                        end
                                        """);
                    }

                }

            }

            // MATLAB counts from one. Index parameters convert here, which is after any
            // default has been applied and before a user supplied postBody runs. An overload
            // that replaces the signature or the call site owns the conversion itself.
            if (!overload.hasParameters() && !overload.hasCall()) {
                for (var param : method.getParameters()) {
                    if (param.getIsIndex()) {
                        preCallActions.add(getIndexInputConversion(param));
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
                if (callbacks.getMatlabExclusionReason(method).isPresent()) continue;

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
                 UINT8_POINTER, INT16_POINTER, INT32_POINTER, INT64_POINTER,
                 FLOAT32_POINTER, FLOAT64_POINTER -> List.of("numeric");
            case CHAR_POINTER -> List.of("char");
            case CHAR_POINTER_POINTER -> List.of("cell");
            case CALLBACK -> List.of("function_handle");
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
        return String.join(", ", choices);
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

    private final MatlabCallbackGenerator callbacks;

}
