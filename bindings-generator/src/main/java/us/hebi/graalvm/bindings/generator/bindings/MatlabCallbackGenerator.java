package us.hebi.graalvm.bindings.generator.bindings;

import us.hebi.graalvm.bindings.generator.generators.IndentingTextRenderer;
import us.hebi.graalvm.bindings.model.*;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Emits the callback support of the MEX gateway: one RAII scope class per callback typedef whose
 * static invoke() the library is handed. The dispatch itself stays in {@link MatlabBindingsGenerator}.
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
     * @param cToMex the gateway's C to mxArray conversion, shared with the rest of the gateway
     */
    MatlabCallbackGenerator(LibraryDescription lib, Function<TypeDescription, String> cToMex) {
        this.lib = lib;
        this.cToMex = cToMex;
    }

    /**
     * The gateway does not include the public C header, so it repeats the typedefs of the callbacks
     * it can dispatch against its own struct copies. The token sits at the end of the struct helpers.
     */
    String createCallbackTypedefs() {
        var visible = getVisibleCallbacks();
        if (visible.isEmpty()) {
            return "";
        }
        var out = new IndentingTextRenderer().add("\n");
        for (var callback : lib.getCallbacks()) {
            if (!visible.contains(callback.getName())) continue;
            out.add("\n").add(CBindingsGenerator.getCCallbackTypedef(callback, MatlabCallbackGenerator::getMexCallbackType));
        }
        return out.render();
    }

    /**
     * The gateway declares its struct copies under the bare name rather than the prefixed C name
     */
    private static String getMexCallbackType(TypeDescription type) {
        return type.getDataType() == DataType.STRUCT
                ? type.getTypeName() : CBindingsGenerator.getQualifiedCType(type);
    }

    /**
     * The shared callback support plus the call site enum. The token sits at the end of a line.
     */
    String createCallbackSupport() {
        var sites = getCallbackSites();
        if (sites.isEmpty()) {
            return "";
        }
        var out = new IndentingTextRenderer().add("""


                // Only the thread that loaded the MEX file may enter the Matrix API
                static const std::thread::id matlabThread = std::this_thread::get_id();

                // An empty argument passes a null function pointer, like the other language faces
                inline const mxArray* getFunctionHandle(const mxArray* arg, const char* name) {
                    if (mxGetNumberOfElements(arg) == 0) {
                        return nullptr;
                    }
                    if (!mxIsClass(arg, "function_handle")) {
                        mexErrMsgIdAndTxt("%%LIB_NAME%%:TypeError", "Argument '%s' must be a function handle or empty.", name);
                    }
                    return arg;
                }

                // Result of a callback scope, copied out before it closes. MATLAB owns the trapped
                // error until the end of the mexFunction, so it outlives the scope.
                struct CallbackOutcome {
                    mxArray* error; // what a failed invocation trapped
                    int dropped;    // invocations that never reached MATLAB
                };

                // Raises a trapped callback error after the library has unwound. 'throw' keeps the
                // original identifier and message (rethrow rejects errors not caught in MATLAB code).
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

                // One site per callback parameter of every dispatched method. Each site owns the static
                // its scope publishes itself through, so two callbacks of the same type stay apart.
                enum CallbackSite {
                """).indent();
        for (var site : sites) {
            out.addLine(site + ",");
        }
        out.unindent().add("};");
        return out.render();
    }

    /**
     * One scope class per callback typedef a MATLAB-visible method takes. The token sits at the end
     * of a line, after the createMex helpers they call.
     */
    String createCallbackScopes() {
        var visible = getVisibleCallbacks();
        if (visible.isEmpty()) {
            return "";
        }
        // The clean-room mex-sdk has no mxCreateString, so this goes through the gateway's own
        // createMexString, which is why it sits here rather than with the other callback support
        var out = new IndentingTextRenderer().add("""


                // A bad result is trapped like a failing feval and raised after the library returns.
                // Returns nullptr when the result is usable.
                inline mxArray* checkCallbackResult(const mxArray* result, const char* callback) {
                    if (isNumericScalar(result)) {
                        return nullptr;
                    }
                    mxArray* args[3] = {createMexString("%%LIB_NAME%%:TypeError"),
                                        createMexString("Callback '%s' must return a numeric scalar."),
                                        createMexString(callback)};
                    mxArray* error = mexCallMATLABWithTrap(0, nullptr, 3, args, "error");
                    for (mxArray* arg : args) mxDestroyArray(arg);
                    return error;
                }""");
        for (var callback : lib.getCallbacks()) {
            if (!visible.contains(callback.getName())) continue;
            var returnType = callback.getReturnType();
            boolean returnsValue = !TypeMap.isVoid(returnType.getDataType());
            var cReturnType = CBindingsGenerator.getQualifiedCType(returnType);
            if (returnsValue && !isScalar(returnType.getDataType())) {
                throw new IllegalStateException("Callback '" + callback.getName() + "' returns a value that the MEX gateway cannot hand back to the library");
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

            // A void callback asks feval for no output, a value callback for one. A skipped invocation
            // returns a value-initialized result, its error is raised after the call anyway.
            var content = out.snapshot()
                    .set("CALLBACK", callback.getName())
                    .set("SCOPE", getCallbackScopeName(callback.getName()))
                    .set("TYPEDEF", callback.getCPrefix() + callback.getName())
                    .set("RETURN_TYPE", cReturnType)
                    .set("PARAMS", BindingGenerator.joiner.join(params))
                    .set("ARG_COUNT", String.valueOf(mexArgs.size() + 1))
                    .set("MEX_ARGS", mexArgs.isEmpty() ? "" : ", " + BindingGenerator.joiner.join(mexArgs))
                    .set("SKIP", returnsValue ? "return " + cReturnType + "{};" : "return;");
            content.add("""


                    // Wrapper handle for %%CALLBACK%% (alive for one call)
                    template <CallbackSite Site>
                    struct %%SCOPE%% {
                        static %%SCOPE%%* current;
                        const mxArray* const handle;    // borrowed from prhs, or null for a null function pointer
                        %%SCOPE%%* const previous;
                        mxArray* error = nullptr;       // what a failed invocation trapped, raised after the scope closes
                        std::atomic<int> dropped{0};    // invocations that never reached MATLAB

                        explicit %%SCOPE%%(const mxArray* handle) : handle(handle), previous(current) { current = this; }
                        ~%%SCOPE%%() { current = previous; }
                        %%SCOPE%%(const %%SCOPE%%&) = delete;

                        %%TYPEDEF%% function() const { return handle != nullptr ? &invoke : nullptr; }

                        CallbackOutcome outcome() const { return {error, dropped.load(std::memory_order_relaxed)}; }

                        // Errors are trapped rather than raised, a longjmp from here would tear down the
                        // library's frames. One mxArray copy per argument per call is inherent to MATLAB.
                        static %%RETURN_TYPE%% invoke(%%PARAMS%%) {
                            %%SCOPE%%* scope = current;
                            // An unsupported fire after the scope closed means the library kept the pointer past the call
                            if (scope == nullptr || scope->error != nullptr) {
                                %%SKIP%%
                            }
                            if (std::this_thread::get_id() != matlabThread) {
                                scope->dropped.fetch_add(1, std::memory_order_relaxed);
                                %%SKIP%%
                            }
                            mxArray* args[%%ARG_COUNT%%] = {const_cast<mxArray*>(scope->handle)%%MEX_ARGS%%};
                    """).indentBy(2);
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
                        scope->error = error;
                        """);
            } else {
                content.add("""
                        mxArray* error = mexCallMATLABWithTrap(0, nullptr, %%ARG_COUNT%%, args, "feval");
                        scope->error = error;
                        """);
            }
            for (int i = 0; i < mexArgs.size(); i++) {
                content.addLine("mxDestroyArray(args[" + (i + 1) + "]);");
            }
            if (returnsValue) {
                content.addLine("return value;");
            }
            content.unindentBy(2).add("""
                        }
                    };
                    template <CallbackSite Site>
                    %%SCOPE%%<Site>* %%SCOPE%%<Site>::current = nullptr;""");
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

    /**
     * Every callback parameter of every MATLAB-visible method, in dispatch order
     */
    private Set<String> getCallbackSites() {
        var sites = new LinkedHashSet<String>();
        for (var apiClass : lib.getClasses()) {
            for (var method : apiClass.getMethods()) {
                if (getMatlabExclusionReason(method).isPresent()) continue;
                for (var param : method.getParameters()) {
                    if (param.getDataType() == DataType.CALLBACK) sites.add(getCallbackSite(method, param));
                }
            }
        }
        return sites;
    }

    /**
     * The enumerator naming one callback parameter of one method, which selects the scope's static
     */
    static String getCallbackSite(MethodDescription method, TypeDescription param) {
        return method.getCName() + "_" + param.getParamName();
    }

    static String getCallbackScopeName(String callbackName) {
        return callbackName + "Scope";
    }

    /**
     * The single decision point for shapes the MATLAB face cannot express. Excluded methods get an
     * m-file stub that raises the reason and no dispatch case.
     *
     * @return why the method cannot be called from MATLAB, or empty if it can
     */
    Optional<String> getMatlabExclusionReason(MethodDescription method) {
        for (var param : method.getParameters()) {
            if (param.getDataType() != DataType.CALLBACK) continue;
            // feval is asked for one scalar output, anything else has no route back into the library
            var returnType = getCallback(param).getReturnType().getDataType();
            if (!TypeMap.isVoid(returnType) && !isScalar(returnType)) {
                return Optional.of("its callback returns a value that the gateway cannot hand back");
            }
            // A raw pointer carries no length to size an mxArray with, and MATLAB cannot use an address
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
     * The subset of {@link MatlabBindingsGenerator#getCToMex} that a scope actually converts
     */
    private static boolean isConvertibleToMex(DataType type) {
        return switch (type) {
            case ENUM, BOOL, INT8, INT16, INT32, INT64, FLOAT32, FLOAT64, SIZE_T, CHAR16,
                 OBJECT_HANDLE, CHAR_POINTER, STRUCT -> true;
            default -> false;
        };
    }

    /**
     * Whether a callback result can come back through mxGetScalar
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
