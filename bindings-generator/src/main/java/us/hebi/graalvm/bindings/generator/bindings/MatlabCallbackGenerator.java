package us.hebi.graalvm.bindings.generator.bindings;

import us.hebi.graalvm.bindings.generator.generators.IndentingTextRenderer;
import us.hebi.graalvm.bindings.model.*;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Emits the callback mechanism of the MEX gateway: the registry every MATLAB-visible callback lives
 * in and the trampolines the library is handed. A callback is live for exactly the call that
 * passed it. The dispatch itself stays in {@link MatlabBindingsGenerator} and asks this class for
 * the names.
 * <p>
 * TODO:
 *  callbacks in MATLAB are a huge mess. We don't have much of a use case for them yet, so
 *  this is Claude's attempt to solve it (with some guidance). However, the result is spotty
 *  and the code needs some serious review.
 *
 * @author Florian Enner
 * @since 09 Aug 2026
 */
class MatlabCallbackGenerator {

    /**
     * @param cToMex the gateway's C to mxArray conversion, which serves every data type and
     *               therefore stays with the generator
     */
    MatlabCallbackGenerator(LibraryDescription lib, Function<TypeDescription, String> cToMex) {
        this.lib = lib;
        this.cToMex = cToMex;
    }

    /**
     * The gateway does not include the public C header, so it repeats the callback typedefs the
     * same way it repeats the error struct, spelled against its own struct copies. The token they
     * fill sits at the end of the struct helpers, which is where those copies are declared, so a
     * library without callbacks renders the template unchanged.
     */
    String createCallbackTypedefs() {
        if (lib.getCallbacks().length() == 0) {
            return "";
        }
        var out = new IndentingTextRenderer().add("\n");
        for (var callback : lib.getCallbacks()) {
            out.add("\n").add(CBindingsGenerator.getCCallbackTypedef(callback, MatlabCallbackGenerator::getMexCallbackType));
        }
        return out.render();
    }

    /**
     * The gateway declares layout-compatible copies of the user structs under the bare type name,
     * so a signature naming one may not use the prefixed name of the public C header.
     */
    private static String getMexCallbackType(TypeDescription type) {
        return type.getDataType() == DataType.STRUCT
                ? type.getTypeName() : CBindingsGenerator.getQualifiedCType(type);
    }

    /**
     * The registry that every MATLAB-visible callback lives in. The token it fills sits at the end
     * of an existing line.
     */
    String createCallbackSupport() {
        var callbacks = getVisibleCallbacks();
        if (callbacks.isEmpty()) {
            return "";
        }
        var out = new IndentingTextRenderer().add("""
                
                
                // The MEX file is loaded by MATLAB, so static initialization runs on the one thread that
                // may enter the Matrix API. Any other thread has to skip rather than corrupt it.
                static const std::thread::id matlabThread = std::this_thread::get_id();
                
                inline void checkFunctionHandle(const mxArray* arg) {
                    if (!mxIsClass(arg, "function_handle")) {
                        mexErrMsgIdAndTxt("%%LIB_NAME%%:TypeError", "Expected a function handle.");
                    }
                }
                
                // A library calls a plain function pointer and passes back only what its own signature
                // declares, so the gateway hands out one distinct function per record and never reads
                // anything that crossed the ABI. A record lives for the length of the one call that
                // registered it.
                struct CallbackRecord {
                    const mxArray* handle;    // the function handle to invoke
                    mxArray* error;           // what a failed invocation trapped, raised after the call
                    std::atomic<int> dropped; // invocations that never reached MATLAB
                    bool inUse;
                };
                
                // Bounds how many callbacks of one type may be live at once, which is the nesting depth
                // of callbacks re-entering the library. A slot costs one record.
                static const size_t MAX_CALLBACKS = 16;
                
                struct CallbackRegistry {
                    CallbackRecord records[MAX_CALLBACKS];
                };
                
                """);

        for (var callback : callbacks) {
            out.addLine("static CallbackRegistry " + getCallbackRegistryName(callback) + ";");
        }
        out.add("""
                
                // The index selects which of the type's trampolines the library is handed, so a nested
                // or repeated registration only needs a slot of its own rather than a lookup key.
                inline size_t acquireCallbackSlot(CallbackRegistry& registry, const mxArray* arg) {
                    checkFunctionHandle(arg);
                    for (size_t i = 0; i < MAX_CALLBACKS; i++) {
                        CallbackRecord& record = registry.records[i];
                        if (record.inUse) {
                            continue;
                        }
                        // std::atomic is not copyable, so the record is filled field by field
                        record.handle = arg;
                        record.error = nullptr;
                        record.dropped.store(0, std::memory_order_relaxed);
                        record.inUse = true;
                        return i;
                    }
                    mexErrMsgIdAndTxt("%%LIB_NAME%%:TooManyCallbacks",
                                      "More than %d callbacks of this type are live at once.", (int) MAX_CALLBACKS);
                    return 0; // not reached, mexErrMsgIdAndTxt longjmps to MATLAB's prompt
                }
                
                // What a record produced, copied out before its slot is released. The trapped
                // error is heap allocated by mexCallMATLABWithTrap and MATLAB owns it until the end of
                // the mexFunction, so it stays valid once the record is gone.
                struct CallbackOutcome {
                    mxArray* error; // what a failed invocation trapped
                    int dropped;    // invocations that never reached MATLAB
                };
                
                // RAII registration of the function handle one call passes. The record borrows the
                // caller's prhs, which stays alive for exactly as long as the call does.
                // mexErrMsgIdAndTxt longjmps to MATLAB's prompt without running C++ destructors, so no
                // raise may happen while the slot is registered. The caller copies the outcome out and
                // raises after the scope has closed.
                struct MatlabCallback {
                    CallbackRegistry& registry;
                    const size_t index;
                    MatlabCallback(CallbackRegistry& registry, const mxArray* arg)
                            : registry(registry), index(acquireCallbackSlot(registry, arg)) {}
                    ~MatlabCallback() { registry.records[index].inUse = false; }
                    MatlabCallback(const MatlabCallback&) = delete;
                    // The count is read after the library call has joined its threads, so relaxed is enough
                    CallbackOutcome outcome() const {
                        const CallbackRecord& record = registry.records[index];
                        return {record.error, record.dropped.load(std::memory_order_relaxed)};
                    }
                };
                
                // Raises what a trampoline trapped, once the library has unwound its own frames and the
                // record has been released. Throwing the MException keeps the original identifier and
                // message, and it longjmps like any other error. rethrow is the wrong verb here, it
                // rejects an exception that was never caught in MATLAB code. A trapped error wins over
                // the skip count, it is the more specific failure.
                inline void checkCallbackError(const CallbackOutcome& outcome) {
                    if (outcome.error != nullptr) {
                        mxArray* error = outcome.error;
                        mexCallMATLAB(0, nullptr, 1, &error, "throw");
                    }
                    if (outcome.dropped != 0) {
                        mexErrMsgIdAndTxt("%%LIB_NAME%%:NotSupported",
                                          "%d callback invocation(s) fired on a thread that cannot reach MATLAB and were skipped.", outcome.dropped);
                    }
                }

                // A result of the wrong shape cannot be raised from inside the library's frames any more
                // than a failing feval can, so the check produces the same trapped MException by letting
                // MATLAB's own error() format it. Returns nullptr when the result is usable.
                inline mxArray* checkCallbackResult(const mxArray* result, const char* callback) {
                    if (isNumericScalar(result)) {
                        return nullptr;
                    }
                    mxArray* args[3] = {mxCreateString("%%LIB_NAME%%:TypeError"),
                                        mxCreateString("Callback '%s' must return a numeric scalar."),
                                        mxCreateString(callback)};
                    mxArray* error = mexCallMATLABWithTrap(0, nullptr, 3, args, "error");
                    for (mxArray* arg : args) mxDestroyArray(arg);
                    return error;
                }""");
        return out.render();
    }

    /**
     * One thunk template and one table of instantiations per callback typedef that a MATLAB-visible
     * method takes. The token they fill sits at the end of an existing line, and they come after
     * the createMex helpers they call.
     */
    String createCallbackTrampolines() {
        var visible = getVisibleCallbacks();
        var out = new IndentingTextRenderer();
        for (var callback : lib.getCallbacks()) {
            if (!visible.contains(callback.getName())) continue;
            var returnType = callback.getReturnType();
            boolean returnsValue = !TypeMap.isVoid(returnType.getDataType());
            var cReturnType = CBindingsGenerator.getQualifiedCType(returnType);
            if (returnsValue && !isScalar(returnType.getDataType())) {
                throw new IllegalStateException("Callback '" + callback.getName() + "' returns a value that the MEX trampoline cannot hand back to the library");
            }
            var params = new ArrayList<String>();
            var mexArgs = new ArrayList<String>();
            for (var parameter : callback.getParameters()) {
                boolean isStruct = parameter.getDataType() == DataType.STRUCT;
                params.add(CBindingsGenerator.getCallbackParameterType(parameter, MatlabCallbackGenerator::getMexCallbackType)
                           + " " + parameter.getParamName());
                mexArgs.add(cToMex.apply(parameter).replace("%%RETURN_VALUE%%",
                        isStruct ? "*" + parameter.getParamName() : parameter.getParamName()));
            }

            // feval takes the handle as its first argument. A void callback asks for no output, so
            // the trapped error is the only thing that comes back, and a skipped invocation has
            // nothing to return. A value callback asks for one, and hands back a value-initialized
            // result wherever the handle was not reached, its error is raised after the call anyway.
            var content = out.snapshot()
                    .set("CALLBACK", callback.getName())
                    .set("NAME", getCallbackTrampolineName(callback.getName()))
                    .set("TYPEDEF", callback.getCPrefix() + callback.getName())
                    .set("REGISTRY", getCallbackRegistryName(callback.getName()))
                    .set("TABLE", getCallbackTableName(callback.getName()))
                    .set("MAKE_TABLE", "make" + callback.getName() + "Table")
                    .set("RETURN_TYPE", cReturnType)
                    .set("PARAMS", BindingGenerator.joiner.join(params))
                    .set("ARG_COUNT", String.valueOf(mexArgs.size() + 1))
                    .set("MEX_ARGS", mexArgs.isEmpty() ? "" : ", " + BindingGenerator.joiner.join(mexArgs))
                    .set("SKIP", returnsValue ? "return " + cReturnType + "{};" : "return;");
            content.add("""
                    
                    
                    // Invokes the function handle registered in slot N for %%CALLBACK%%. Which of these
                    // the library was handed is the whole channel, so nothing arriving back from it is
                    // dereferenced. An error is trapped rather than raised, because a longjmp from here
                    // would tear down the library's own frames. One mxArray per argument per call is
                    // inherent to MATLAB.
                    template <size_t N>
                    static %%RETURN_TYPE%% %%NAME%%(%%PARAMS%%) {
                        CallbackRecord& record = %%REGISTRY%%.records[N];
                        // The thread comes first, because everything below reaches into MATLAB. The
                        // count is read after the library call has joined its threads, so relaxed is
                        // enough, and the caller sees it as an error or a warning afterwards.
                        if (std::this_thread::get_id() != matlabThread) {
                            record.dropped.fetch_add(1, std::memory_order_relaxed);
                            %%SKIP%%
                        }
                        // A released slot means the library kept the pointer past the call that passed
                        // it, which is not supported, and a slot whose earlier invocation failed has that
                        // error waiting to be raised. Neither reaches MATLAB.
                        if (!record.inUse || record.error != nullptr) {
                            %%SKIP%%
                        }
                        mxArray* args[%%ARG_COUNT%%] = {const_cast<mxArray*>(record.handle)%%MEX_ARGS%%};
                    """).indent();
            if (returnsValue) {
                content.add("""
                        %%RETURN_TYPE%% value{};
                        mxArray* result = nullptr;
                        mxArray* error = mexCallMATLABWithTrap(1, &result, %%ARG_COUNT%%, args, "feval");
                        if (error == nullptr) {
                            error = checkCallbackResult(result, "%%CALLBACK%%");
                            if (error == nullptr) {
                                value = static_cast<%%RETURN_TYPE%%>(mxGetScalar(result));
                            }
                            mxDestroyArray(result);
                        }
                        record.error = error;
                        """);
            } else {
                content.add("""
                        mxArray* error = mexCallMATLABWithTrap(0, nullptr, %%ARG_COUNT%%, args, "feval");
                        record.error = error;
                        """);
            }
            for (int i = 0; i < mexArgs.size(); i++) {
                content.addLine("mxDestroyArray(args[" + (i + 1) + "]);");
            }
            if (returnsValue) {
                content.addLine("return value;");
            }
            content.unindent().add("""
                    }
                    
                    // Each entry is a separate instantiation, so the index cannot be a runtime value and
                    // the table has to be expanded from a pack rather than filled in a loop.
                    template <size_t... Is>
                    static std::array<%%TYPEDEF%%, sizeof...(Is)> %%MAKE_TABLE%%(std::index_sequence<Is...>) {
                        return {{&%%NAME%%<Is>...}};
                    }
                    static const auto %%TABLE%% = %%MAKE_TABLE%%(std::make_index_sequence<MAX_CALLBACKS>{});""");
        }
        return out.render();
    }

    /**
     * Callback typedefs that a MATLAB-visible method takes
     */
    private Set<String> getVisibleCallbacks() {
        var names = new LinkedHashSet<String>();
        for (var apiClass : lib.getClasses()) {
            for (var method : apiClass.getMethods()) {
                if (getMatlabExclusionReason(method).isPresent()) continue;
                for (var param : method.getParameters()) {
                    if (param.getDataType() == DataType.CALLBACK) names.add(param.getTypeName());
                }
            }
        }
        return names;
    }

    private static String getCallbackTrampolineName(String callbackName) {
        return "invoke" + callbackName;
    }

    static String getCallbackRegistryName(String callbackName) {
        return toLowerCamel(callbackName) + "Registry";
    }

    private static String getCallbackTableName(String callbackName) {
        return toLowerCamel(callbackName) + "Table";
    }

    static String getCallbackTableEntry(String callbackName, String index) {
        return getCallbackTableName(callbackName) + "[" + index + "]";
    }

    private static String toLowerCamel(String name) {
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

    /**
     * The single decision point for shapes the MATLAB face cannot express. An excluded method
     * still appears in the m-file, as a stub that raises the returned reason, but it reaches
     * neither the gateway nor the auto-complete signatures.
     *
     * @return why the method cannot be called from MATLAB, or empty if it can
     */
    Optional<String> getMatlabExclusionReason(MethodDescription method) {
        for (var param : method.getParameters()) {
            if (param.getDataType() != DataType.CALLBACK) continue;
            // The trampoline asks feval for one output and reads it as a scalar, so anything
            // that is not one has no route back into the library.
            var returnType = getCallback(param).getReturnType().getDataType();
            if (!TypeMap.isVoid(returnType) && !isScalar(returnType)) {
                return Optional.of("its callback returns a value that the gateway cannot hand back");
            }
            // A raw pointer argument carries no length, so the trampoline has nothing to size
            // the mxArray it would have to build. A span parameter would. A void* is rejected
            // here like any other pointer, since MATLAB cannot do anything with an address.
            for (var argument : getCallback(param).getParameters()) {
                if (!isConvertibleToMex(argument.getDataType())) {
                    return Optional.of("its callback takes an argument ('" + argument.getParamName()
                                       + "') that the gateway cannot convert to an mxArray");
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Whether a callback argument can reach feval as an mxArray, which is the subset of
     * {@link MatlabBindingsGenerator#getCToMex} that a trampoline actually converts.
     */
    private static boolean isConvertibleToMex(DataType type) {
        return switch (type) {
            case ENUM, BOOL, INT8, INT16, INT32, INT64, FLOAT32, FLOAT64, SIZE_T, CHAR16,
                 OBJECT_HANDLE, CHAR_POINTER, STRUCT -> true;
            default -> false;
        };
    }

    /**
     * Whether a callback result can come back through mxGetScalar, the one read the
     * trampoline does on the feval output.
     */
    private static boolean isScalar(DataType type) {
        return switch (type) {
            case ENUM, BOOL, INT8, INT16, INT32, INT64, FLOAT32, FLOAT64, SIZE_T, CHAR16 -> true;
            default -> false;
        };
    }

    private CallbackDescription getCallback(TypeDescription param) {
        for (var callback : lib.getCallbacks()) {
            if (callback.getName().equals(param.getTypeName())) return callback;
        }
        throw new IllegalArgumentException("No callback found for type " + param.getTypeName());
    }

    private final LibraryDescription lib;
    private final Function<TypeDescription, String> cToMex;

}
