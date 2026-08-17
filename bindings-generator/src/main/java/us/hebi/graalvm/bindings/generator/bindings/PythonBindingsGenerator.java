package us.hebi.graalvm.bindings.generator.bindings;

import com.google.common.base.CaseFormat;
import us.hebi.graalvm.bindings.generator.bindings.features.FeatureGenerator;
import us.hebi.graalvm.bindings.generator.bindings.marshallers.StructMarshallers;
import us.hebi.graalvm.bindings.generator.generators.GeneratorUtil;
import us.hebi.graalvm.bindings.generator.generators.IndentingTextRenderer;
import us.hebi.graalvm.bindings.model.*;

import java.io.IOException;
import java.util.*;

import static us.hebi.graalvm.bindings.generator.generators.DocumentationUtil.*;
import static us.hebi.quickbuf.ProtoUtil.*;

/**
 * @author Florian Enner
 * @since 16 Jul 2024
 */
public class PythonBindingsGenerator extends BindingGenerator {


    public PythonBindingsGenerator(LibraryDescription lib) {
        super(lib);
    }

    @Override
    public void writeTo(Filer filer) throws IOException {
        // For now we only need a single Python file that can just live in the main binding directory
        filer.createFile("", "__init__.py", file -> file.write("from ." + lib.getName() + " import *"));
        filer.createFile("", lib.getName() + ".py", file -> file.write(createModuleSource()));
    }

    private String createModuleSource() {
        var apiClasses = getApiClasses();
        var out = new IndentingTextRenderer()
                .set("LIB_FIELD", "lib" + lib.getName())
                .set("LIB_NAME", lib.getName())
                .set("LIB_VERSION", lib.getVersion())
                .set("LIB_BUILD_NUMBER", lib.getBuild());

        writeLibLoader(out);
        writeCStructDefinitions(out, apiClasses);
        writeCallbackTypes(out);
        writeCFunctionSignatures(out, apiClasses);
        writeStructWrappers(out);
        writeEnumWrappers(out);
        writeClassWrappers(out, apiClasses);

        for (FeatureGenerator feature : features) {
            out.add(feature.getPythonWrapper());
        }
        return out.render();
    }

    private void writeLibLoader(IndentingTextRenderer out) {
        out.set("DOWNLOAD_BLOCK", createDownloadBlock());
        out.setIf("STRUCT_IMPORT", hasDataclassStructs(), "\nfrom dataclasses import dataclass"); // in next line to leave libs without structs unchanged
        out.add("""
                from __future__ import annotations
                from ctypes import *%%STRUCT_IMPORT%%
                from enum import IntEnum
                import os
                import platform
                from typing import Sequence, Tuple, Any, Callable

                # Load the shared library relative to this file
                def load_library():
                    lib_name = '%%LIB_NAME%%'
                    version = '%%LIB_VERSION%%'
                    build_number = '%%LIB_BUILD_NUMBER%%'

                    architecture = platform.architecture()[0]  # gets '64bit' or '32bit'
                    os_name = platform.system()  # gets 'Windows', 'Linux', 'Darwin'

                    # Determine the current platform
                    if os_name == 'Windows' and architecture == '64bit':
                        lib_platform = 'win_amd64'
                        lib_file = lib_name + '.dll'
                        cache_home = os.getenv('LOCALAPPDATA')
                    elif os_name == 'Linux' and architecture == '64bit':
                        lib_platform = 'linux_amd64'
                        lib_file = 'lib' + lib_name + '.so'
                        cache_home = os.getenv('XDG_CACHE_HOME')
                        if not cache_home:
                            cache_home = os.path.join(os.getenv('HOME'), '.cache')
                    elif os_name == 'Darwin' and architecture == '64bit':  # Darwin is the underlying system for macOS.
                        if platform.machine() in ['arm64', 'aarch64']:  # Apple Silicon
                            lib_platform = 'osx_arm64'
                        else:  # Intel Mac
                            lib_platform = 'osx_amd64'
                        lib_file = 'lib' + lib_name + '.dylib'
                        cache_home = os.path.join(os.getenv('HOME'), 'Library', 'Caches')
                    else:
                        raise Exception('Unsupported system: ' + os_name + ' ' + platform.machine())

                    # Prioritize locally bundled lib binaries
                    this_file_dir = os.path.dirname(os.path.realpath(__file__))
                    lib_path = os.path.join(this_file_dir, 'lib', lib_platform, lib_file)

                    # Fall back to shared downloaded version in local cache
                    if not os.path.exists(lib_path):
                        download_dir = os.path.join(cache_home, lib_name, f'{version}-build{build_number}')
                        lib_path = os.path.join(download_dir, lib_name, 'lib', lib_platform, lib_file)
                %%DOWNLOAD_BLOCK%%
                    # Sanity check due to loadlibrary errors being harder to parse
                    if not os.path.exists(lib_path):
                        raise Exception(f'Required binary was not found: {lib_path}')

                    # Load the library
                    return cdll.LoadLibrary(lib_path)

                %%LIB_FIELD%% = load_library()

                # Optional lazy-loaded numpy dependency for bulk methods
                _numpy = None
                def _require_numpy():
                    global _numpy
                    if _numpy is None:
                        try:
                            import numpy as np
                            _numpy = np
                        except ImportError:
                            raise ImportError("This method requires 'numpy'. Please run: pip install numpy")
                    return _numpy

                # API definitions
                """);
    }

    /**
     * The on-demand download of a release archive. Libraries that do not publish
     * their binaries leave the url empty and get a loader that only looks locally.
     */
    private String createDownloadBlock() {
        if (!lib.hasDownloadUrl()) {
            return "";
        }
        return new IndentingTextRenderer()
                .set("DOWNLOAD_URL", lib.getDownloadUrl())
                .indent(2)
                .add("""

                        # If necessary, start a new download it into a shared dir. All files are
                        # kept so we can share a single download for all languages.
                        if not os.path.exists(lib_path):
                            import urllib.request
                            import zipfile

                            # Build download url
                            dl_name = f"{lib_name}-{version}-{lib_platform}.zip"
                            url = f"%%DOWNLOAD_URL%%/{dl_name}"

                            # Download zip file
                            print(f"Downloading {url}")
                            os.makedirs(download_dir, exist_ok=True)
                            zip_path = os.path.join(download_dir, dl_name)

                            # urllib may fail on some platforms due to missing SSL certificates (e.g. macOS brew),
                            # so we provide a curl fallback that should be available on most systems.
                            try:
                                urllib.request.urlretrieve(
                                    url,
                                    zip_path,
                                    lambda count, block_size, total_size: print(
                                        f"\\rDownloading... {int(count * block_size * 100 / total_size)}% ", end="")
                                )
                                print('')
                            except Exception as e:
                                print(f"\\nurllib download failed ({e}), retrying with curl...")
                                import subprocess
                                subprocess.run(["curl", "-L", "-f", "-o", zip_path, url], check=True)

                            # Extract the entire archive
                            print(f'Extracting to {download_dir}')
                            with zipfile.ZipFile(zip_path, 'r') as zip_ref:
                                zip_ref.extractall(download_dir)

                            print(f'Removing temporary files')
                            os.remove(zip_path)
                        """)
                .render();
    }

    private void writeCStructDefinitions(IndentingTextRenderer out, List<ClassDescription> apiClasses) {
        out.add("""
                class _ErrorInfo(Structure):
                    # Filled by every method that can throw. The strings stay valid only until
                    # this thread's next library call, so they get copied into the exception.
                    _fields_ = [("message", c_char_p), ("id", c_char_p), ("code", c_int32)]

                class Error(RuntimeError):
                    # Failure reported by the library. The id names the kind of failure, as in
                    # "mylib:invalidArgument", and is empty when the library did not set one.
                    def __init__(self, message, id=""):
                        super().__init__(f"[{id}] {message}" if id else message)
                        self.id = id

                def _checked(error_, result_):
                    # Wraps the call so that the check reads as one expression. Python evaluates
                    # the arguments first, so the struct is filled by the time this runs.
                    if error_.code != 0:
                        message = error_.message.decode("utf-8") if error_.message else "unknown error"
                        raise Error(message, error_.id.decode("utf-8") if error_.id else "")
                    return result_

                """);
        for (var struct : lib.getStructs()) {
            List<String> fields = new ArrayList<>();
            for (var field : struct.getFields()) {
                fields.add("(\"" + field.getLowerUnderscoreName() + "\", " + TypeMap.toStructFieldCPython(field.getDataType()) + ")");
            }
            out.set("STRUCT_NAME", struct.getName())
                    .setArgs("FIELDS", fields)
                    .add("""
                            class _%%STRUCT_NAME%%(Structure):
                                # Layout of the by-value struct in the C ABI, mirroring %%STRUCT_NAME%%
                                _fields_ = [%%FIELDS%%]

                            """);
        }
        for (ClassDescription apiClass : apiClasses) {
            out.set("CLASS_NAME", apiClass.getType().getTypeName())
                    .set("CPYTHON_TYPE", getCPythonType(apiClass.getType()))
                    .add("""
                            class struct_%%CLASS_NAME%%_(Structure):
                                pass
                            %%CPYTHON_TYPE%% = POINTER(struct_%%CLASS_NAME%%_)

                            """);
        }
    }

    /**
     * The ctypes prototype a callable gets wrapped in. Struct views arrive by pointer.
     */
    private void writeCallbackTypes(IndentingTextRenderer out) {
        for (var callback : lib.getCallbacks()) {
            var types = new ArrayList<String>();
            types.add(TypeMap.toCPython(callback.getReturnType()));
            for (var parameter : callback.getParameters()) {
                types.add(parameter.getDataType() == DataType.STRUCT
                        ? "POINTER(" + TypeMap.toCPython(parameter) + ")" : TypeMap.toCPython(parameter));
            }
            out.addLine(callback.getName() + " = CFUNCTYPE(" + String.join(", ", types) + ")");
        }
    }

    private void writeCFunctionSignatures(IndentingTextRenderer out, List<ClassDescription> apiClasses) {
        for (ClassDescription apiClass : apiClasses) {
            out.addLine().addLine("# ==== " + apiClass.getType().getTypeName() + " ====");
            out.set("CPYTHON_TYPE", getCPythonType(apiClass.getType()));

            for (MethodDescription method : apiClass.getMethods()) {
                List<String> params = new ArrayList<>();
                if (isThrowing(method)) {
                    params.add("POINTER(_ErrorInfo)");
                }
                if (!method.getIsStatic()) {
                    params.add(getCPythonType(apiClass.getType()));
                }
                for (TypeDescription parameter : method.getParameters()) {
                    params.add(getCPythonType(parameter));
                }
                out.set("CENTRY_NAME", method.getCName())
                        .setArgs("ARG_TYPES", params)
                        .set("RETURN_TYPE", getCPythonType(method.getReturnType()))
                        .add("""
                                %%LIB_FIELD%%.%%CENTRY_NAME%%.argtypes = [%%ARG_TYPES%%]
                                %%LIB_FIELD%%.%%CENTRY_NAME%%.restype = %%RETURN_TYPE%%
                                """);
            }

            if (apiClass.getInstantiable() && !apiClass.hasParent()) {
                out.set("C_DESTRUCTOR", apiClass.getCDestructorName())
                        .add("""
                                %%LIB_FIELD%%.%%C_DESTRUCTOR%%.argtypes = [%%CPYTHON_TYPE%%]
                                %%LIB_FIELD%%.%%C_DESTRUCTOR%%.restype = None
                                """);
            }

            if (apiClass.hasParent()) {
                out.set("CAST", apiClass.getToParentCastMethod().getCName())
                        .set("PARENT_TYPE", getCPythonType(apiClass.getParent()))
                        .add("""
                                %%LIB_FIELD%%.%%CAST%%.argtypes = [%%CPYTHON_TYPE%%]
                                %%LIB_FIELD%%.%%CAST%%.restype = %%PARENT_TYPE%%
                                """);
            }
        }
    }

    /**
     * The public face of a user struct. Users build a dataclass and the call site packs it
     * into the ctypes mirror, so nothing in the API asks for ctypes types.
     */
    private void writeStructWrappers(IndentingTextRenderer out) {
        if (!hasDataclassStructs()) return;

        out.addLine().addLine("# ==== Structs ====");
        for (var struct : lib.getStructs()) {
            // A marshalled struct has no public face, users see the Python type it maps to
            if (StructMarshallers.resolve(struct).flatMap(StructMarshaller::python).isPresent()) continue;
            out.addLine().addLine("@dataclass");
            out.beginControlFlow("class " + struct.getName() + ":");
            for (var field : struct.getFields()) {
                // Pointer fields have no lifetime story here, only the layout mirror carries them
                if (TypeMap.isPointerType(field.getDataType())) continue;
                out.addLine(field.getLowerUnderscoreName() + ": " + TypeMap.toStructFieldPythonHint(field.getDataType()));
            }
            out.unindent();
        }
        out.addLine();
    }

    /**
     * @return true if any struct is emitted as a dataclass, which is what needs the dataclass import
     */
    private boolean hasDataclassStructs() {
        for (var struct : lib.getStructs()) {
            if (StructMarshallers.resolve(struct).flatMap(StructMarshaller::python).isEmpty()) return true;
        }
        return false;
    }

    private void writeEnumWrappers(IndentingTextRenderer out) {
        if (lib.getEnums().length() == 0) return;

        out.addLine().addLine("# ==== Enum Wrappers ====");
        for (EnumDescription generatedEnum : lib.getEnums()) {
            out.beginControlFlow("class " + generatedEnum.getName() + "(IntEnum):");
            for (EnumValueDescription value : generatedEnum.getValues()) {
                String pep8 = value.getName().toUpperCase(Locale.ENGLISH);
                if (!pep8.equals(value.getName())) {
                    pep8 = CaseFormat.UPPER_CAMEL.to(CaseFormat.UPPER_UNDERSCORE, value.getName());
                }
                out.addLine(pep8 + " = " + value.getCValue());
            }
            out.unindent().addLine();
        }
    }

    private void writeClassWrappers(IndentingTextRenderer out, List<ClassDescription> apiClasses) {
        out.addLine().add("# ==== Class Wrappers ====");
        for (ClassDescription apiClass : apiClasses) {
            writeClassWrapper(new ClassScope(apiClass, out));
        }
    }

    private void writeClassWrapper(ClassScope scope) {
        var apiClass = scope.apiClass;
        var out = scope.out;
        out.addLine();
        out.beginControlFlow(apiClass.hasParent()
                ? "class %%CLASS_NAME%%(" + apiClass.getParent().getTypeName() + "):"
                : "class %%CLASS_NAME%%:");

        if (apiClass.getType().hasComment()) {
            out.add(generatePydoc(apiClass.getType())).addLine().addLine();
        }

        // Fixed slots (fields) to prevent dynamic fields and dictionary overhead
        if (apiClass.getInstantiable()) {
            out.addLine("__slots__ = ('%%REF_FIELD%%',)").addLine();
        } else if (apiClass.hasChildren() || apiClass.hasParent()) {
            out.addLine("__slots__ = ()").addLine(); // dummy to prevent __dict__ creation
        }

        writeConstructor(scope);

        // Methods and properties
        Set<String> getters = new HashSet<>();
        for (MethodDescription method : apiClass.getMethods()) {
            if (GeneratorUtil.isConstructor(method)) continue;

            if (GeneratorUtil.isGetter(method)) {
                getters.add(method.getPropertyName());
            } else if (GeneratorUtil.isSetter(method)) {
                boolean needsDummyGetter = getters.add(method.getPropertyName());
                if (needsDummyGetter) {
                    out.set("PROP", getPropertyName(method)).add("""
                            @property
                            def %%PROP%%(self):
                                raise AttributeError("Property '%%PROP%%' is write-only")

                            """);
                }
            }
            writeMethod(new MethodScope(scope, method));
        }
        out.unindent();
    }

    private void writeConstructor(ClassScope scope) {
        var apiClass = scope.apiClass;
        var out = scope.out;
        if (!apiClass.getInstantiable()) {
            out.add("""
                    def __new__(cls, *args, **kwargs):
                        raise TypeError('Class is not instantiable')

                    """);
            return;
        }

        // Check for a defined constructor first - it gets its own wrapper method
        var constructor = GeneratorUtil.getConstructor(apiClass);
        if (constructor.isPresent()) {
            // A defined constructor means that users instantiate this class by themselves. Currently
            // this means that we do not have a raw ptr argument and thus can't support being called
            // from child constructors
            checkArgument(!apiClass.hasChildren(), "Constructors are currently not supported parent classes");
            // Note: the base initialization is part of the generated __init__ body (see getReturnTemplate)
            writeMethod(new MethodScope(scope, constructor.get()));
        } else {
            // Not defining a constructor means that this class can't be instantiated by users
            // and that we only need to support an 'internal' constructor for generating wrappers
            out.setIf("SUPER_INIT", apiClass.hasParent(), "\n    super().__init__(%%LIB_FIELD%%.%%CAST%%(%%REF_FIELD%%))");
            out.add("""
                    def __init__(self, %%REF_FIELD%%: %%CLASS_PTR%%):
                        if not isinstance(%%REF_FIELD%%, %%CLASS_PTR%%):
                            raise TypeError(f'Expected %%CLASS_PTR%%, got {type(%%REF_FIELD%%).__name__}')
                        self.%%REF_FIELD%% = %%REF_FIELD%%%%SUPER_INIT%%

                    """);
        }

        // Always add a destructor
        if (!apiClass.hasParent()) {
            out.add("""
                    def __del__(self):
                        if self.%%REF_FIELD%% is not None:
                            %%LIB_FIELD%%.%%DESTR%%(self.%%REF_FIELD%%)
                        self.%%REF_FIELD%% = None

                    """);
        } else {
            out.add("""
                    def __del__(self):
                        super().__del__()
                        self.%%REF_FIELD%% = None

                    """);
        }

        // Create a __repr__ method for text display, e.g., Axis(min={self.min}, max={self.max})
        List<String> getProp = new ArrayList<>();
        for (var getter : GeneratorUtil.getGetters(lib, apiClass.getType())) {
            String prop = getPropertyName(getter);
            getProp.add(prop + "={self." + prop + "!r}");
        }
        out.setArgs("PROPGET", getProp).add("""
                def __repr__(self):
                    return f"{self.__class__.__name__}(%%PROPGET%%)"

                """);
    }

    private static String getPropertyName(MethodDescription method) {
        return CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, method.getPropertyName());
    }

    private void writeMethod(MethodScope m) {
        var method = m.method;
        var out = m.out;
        var params = new ParameterLists(lib, method);

        // Decorators
        out.addLineIf(method.getIsStatic() && !method.getIsConstructor(), "@classmethod");
        String funcName;
        String propName = getPropertyName(method);
        String returnHint = " -> " + TypeMap.toPythonTypeHint(method.getReturnType());
        if (GeneratorUtil.isSetter(method)) {
            out.addLine("@" + propName + ".setter");
            funcName = propName;
            returnHint = "";
        } else if (GeneratorUtil.isGetter(method)) {
            out.addLine("@property");
            funcName = propName;
        } else if (method.getIsConstructor()) {
            funcName = "__init__";
            returnHint = " -> None";
        } else {
            funcName = method.getLowerUnderscoreName();
        }

        // An overload replaces the entire parameter list, so the receiver (self / cls) has to be prepended here
        var overload = method.getPythonOverload();
        var receiver = getReceiverName(method);
        var parameters = overload.tryGetParameters()
                .map(string -> receiver + ", " + string)
                .orElseGet(() -> String.join(", ", params.externalInput));
        var callArgs = overload.tryGetCall()
                .map(string -> method.getIsStatic() ? string : "self.%%REF_FIELD%%, " + string)
                .orElseGet(() -> String.join(", ", params.internalInput));

        // A throwing method fills a local struct and wraps the call in the check, which keeps
        // every return template a single expression
        boolean throwing = isThrowing(method);
        if (throwing) {
            callArgs = callArgs.isEmpty() ? "byref(error_)" : "byref(error_), " + callArgs;
        }
        String call = "%%LIB_FIELD%%.%%CNAME%%(" + callArgs + ")";
        out.set("METHOD", funcName)
                .set("PARAMS", parameters)
                .set("HINT", returnHint)
                .set("CALL", throwing ? "_checked(error_, " + call + ")" : call);

        // The overload snippets, the conversions and the return template
        var body = overload.tryGetBody().map(String::stripTrailing).orElse("");
        var conversions = params.inputConversions.render().stripTrailing();
        var postBody = overload.tryGetPostBody().map(String::stripTrailing).orElse("");
        var returnTemplate = getReturnTemplate(m.apiClass, method);
        boolean usesNumpy = isUsingNumpy(body, conversions, postBody, returnTemplate);

        out.beginControlFlow("def %%METHOD%%(%%PARAMS%%)%%HINT%%:");
        if (method.hasDocumentation()) {
            out.addLine(escapeBackslashes(generatePydoc(method)));
        }
        out.addLineIf(usesNumpy, "np = _require_numpy()");
        out.addLineIf(!body.isEmpty(), body);
        out.addLineIf(!conversions.isEmpty(), conversions);
        out.addLineIf(!postBody.isEmpty(), postBody);
        out.addLineIf(throwing, "error_ = _ErrorInfo()");
        out.addLine(returnTemplate);
        out.unindent().addLine();
    }

    private boolean isUsingNumpy(String... snippets) {
        for (var snippet : snippets) {
            if (snippet.contains("np.")) {
                return true;
            }
        }
        return false;
    }

    /**
     * The statements that turn the C result into the Python return, ending on the call for a
     * void method. Not yet rendered: the tokens belong to the method scope.
     */
    private String getReturnTemplate(ClassDescription apiClass, MethodDescription desc) {
        final boolean isConstructor = desc.getIsConstructor();
        final var props = getConstructionProperties(lib, desc);
        String template = switch (desc.getReturnType().getDataType()) {
            case OBJECT_HANDLE -> {
                String string = """
                        ptr_ = %%CALL%%
                        if not ptr_:
                            raise RuntimeError('Failed to create %%RETURN_TYPE%% in %%CLASS_NAME%%::%%METHOD%%')
                        """;
                if (isConstructor) {
                    string += "self.%%REF_FIELD%% = ptr_\n";
                    if (apiClass.hasParent()) {
                        // The inherited part has to be set up before any properties get applied because
                        // inherited setters access the parent pointer
                        string += "super().__init__(%%LIB_FIELD%%.%%CAST%%(ptr_))\n";
                    }
                    string += getPropertySetBlock(props, "self", "");
                } else if (!props.isEmpty()) {
                    string += "obj_ = %%RETURN_TYPE%%(ptr_)\n"
                              + getPropertySetBlock(props, "obj_", "")
                              + "return obj_";
                } else {
                    string += "return %%RETURN_TYPE%%(ptr_)\n";
                }
                yield string;
            }
            case CHAR_POINTER -> """
                    ptr_ = %%CALL%%
                    return ptr_.decode("utf-8") if ptr_ is not None else ""
                    """;
            // The mirror carries the C layout, so the public dataclass gets unpacked field by field
            case STRUCT -> getPythonMarshaller(desc.getReturnType())
                    .map(marshaller -> marshaller.unpack(STRUCT_LOCAL_NAME))
                    .orElseGet(() -> STRUCT_LOCAL_NAME + " = %%CALL%%\n"
                                     + "return %%RETURN_TYPE%%(" + getStructUnpackArgs(desc.getReturnType(), STRUCT_LOCAL_NAME) + ")\n");
            case ENUM -> "return %%RETURN_TYPE%%(%%CALL%%)";
            case BOOL -> "return %%CALL%% != 0";
            case VOID -> "%%CALL%%";
            default -> "return %%CALL%%";
        };
        return template.stripTrailing().replace("%%RETURN_TYPE%%", desc.getReturnType().getTypeName());
    }

    /**
     * Constructor arguments that turn the ctypes mirror into the public dataclass. The layout
     * keeps a boolean field as an int32, so the public face converts it back.
     */
    private String getStructUnpackArgs(TypeDescription type, String local) {
        List<String> args = new ArrayList<>();
        for (var field : getStructDescription(lib, type).getFields()) {
            if (TypeMap.isPointerType(field.getDataType())) continue;
            String value = local + "." + field.getLowerUnderscoreName();
            args.add(field.getDataType() == DataType.BOOL ? "bool(" + value + ")" : value);
        }
        return String.join(", ", args);
    }

    private static Optional<StructMarshaller.PyStructMarshaller> getPythonMarshaller(TypeDescription type) {
        if (type.getDataType() != DataType.STRUCT) return Optional.empty();
        return StructMarshallers.resolve(type).flatMap(StructMarshaller::python);
    }

    private static StructDescription getStructDescription(LibraryDescription lib, TypeDescription type) {
        for (var struct : lib.getStructs()) {
            if (struct.getName().equals(type.getTypeName())) {
                return struct;
            }
        }
        throw new IllegalArgumentException("No struct found for type " + type.getTypeName());
    }

    /**
     * A callback parameter is hinted as the Callable the wrapper accepts. ctypes hands a void*
     * to it as an address or None and a struct view as a pointer, so both show up as Any.
     */
    private static String getPythonTypeHint(LibraryDescription lib, TypeDescription type) {
        if (type.getDataType() != DataType.CALLBACK) {
            return TypeMap.toPythonTypeHint(type);
        }
        for (var callback : lib.getCallbacks()) {
            if (!callback.getName().equals(type.getTypeName())) continue;
            var args = new ArrayList<String>();
            for (var parameter : callback.getParameters()) {
                args.add(switch (parameter.getDataType()) {
                    case VOID_POINTER, STRUCT -> "Any";
                    default -> TypeMap.toPythonTypeHint(parameter);
                });
            }
            return "Callable[[" + String.join(", ", args) + "], " + TypeMap.toPythonTypeHint(callback.getReturnType()) + "]";
        }
        return TypeMap.toPythonTypeHint(type);
    }

    private static String getCPythonType(TypeDescription typeDescription) {
        return switch (typeDescription.getDataType()) {
            case OBJECT_HANDLE -> typeDescription.getTypeName() + "Ptr";
            default -> TypeMap.toCPython(typeDescription);
        };
    }

    static List<MethodDescription> getConstructionProperties(LibraryDescription lib, MethodDescription method) {
        // Property accessors and non-object handles can't have any properties
        if (GeneratorUtil.isAccessor(method) || method.getReturnType().getDataType() != DataType.OBJECT_HANDLE) {
            return Collections.emptyList();
        }
        var props = GeneratorUtil.getSetters(lib, method.getReturnType());
        if (!props.isEmpty()) {
            // Filter all properties with the same name as a positional argument
            Set<String> posParams = new HashSet<>();
            method.getParameters().forEach(p -> posParams.add(p.getLowerUnderscoreParamName()));
            props = props.stream()
                    .filter(p -> !posParams.contains(getPropertyName(p)))
                    .toList();
        }
        return props;
    }

    static String getSetterTypeHint(MethodDescription setter) {
        // Case 1: overloaded argument -> we need to parse
        if (setter.getPythonOverload().hasParameters()) {
            String raw = setter.getPythonOverload().getParameters();
            raw = raw.replaceFirst("^self\\s*,?\\s*", "").trim(); // Remove 'self' and whitespace
            if (raw.contains(":")) {
                // Split by colon, then split the right side by '=' or ',' to isolate the type
                String afterColon = raw.split(":")[1].trim();
                return afterColon.split("[=,]")[0].trim();
            }
            return "Any";
        }

        // Case 2: multiple arguments -> generate combined tuple
        if (setter.getParameters().length() > 1) {
            List<String> hints = new ArrayList<>();
            for (var parameter : setter.getParameters()) {
                hints.add(TypeMap.toPythonTypeHint(parameter));
            }
            return "Tuple[" + String.join(", ", hints) + "]";
        }

        // Default case: -> single input
        return TypeMap.toPythonTypeHint(setter.getParameters().get(0));
    }

    static List<String> getPropertyInputParams(List<MethodDescription> properties) {
        if (properties.isEmpty()) return Collections.emptyList();
        List<String> params = new ArrayList<>();
        params.add("*");
        for (var prop : properties) {
            params.add(getPropertyName(prop) + ": " + getSetterTypeHint(prop) + " = None");
        }
        return params;
    }

    static String getPropertySetBlock(List<MethodDescription> properties, String objName, String indent) {
        if (properties.isEmpty()) return "";
        StringBuilder block = new StringBuilder();
        for (var prop : properties) {
            var p = getPropertyName(prop);
            block.append(indent)
                    .append("if ").append(p).append(" is not None: ")
                    .append(objName).append(".").append(p).append(" = ").append(p).append("\n");
        }
        return block.toString();
    }

    static String getReceiverName(MethodDescription method) {
        return method.getIsConstructor() || !method.getIsStatic() ? "self" : "cls";
    }

    /** Name of the local holding the ctypes mirror a struct return arrives in */
    private static final String STRUCT_LOCAL_NAME = "struct_";

    /**
     * The module renderer with one class's names set and the class body indented
     */
    private final class ClassScope {

        ClassScope(ClassDescription apiClass, IndentingTextRenderer module) {
            this.apiClass = apiClass;
            this.out = module.snapshot()
                    .set("CLASS_NAME", apiClass.getType().getTypeName())
                    .set("CLASS_PTR", getCPythonType(apiClass.getType()))
                    .set("REF_FIELD", apiClass.hasParent() ? "ref_" + apiClass.getType().getTypeName() : "ptr");
            if (apiClass.getInstantiable()) {
                out.set("DESTR", apiClass.getCDestructorName());
            }
            if (apiClass.hasParent()) {
                out.set("CAST", apiClass.getToParentCastMethod().getCName());
            }
        }

        final ClassDescription apiClass;
        final IndentingTextRenderer out;

    }

    /**
     * The class renderer with one method's names set
     */
    private final class MethodScope {

        MethodScope(ClassScope scope, MethodDescription method) {
            this.apiClass = scope.apiClass;
            this.method = method;
            this.out = scope.out.snapshot()
                    .set("CNAME", method.getCName());
        }

        final ClassDescription apiClass;
        final MethodDescription method;
        final IndentingTextRenderer out;

    }

    /**
     * Builds the external (Python signature) and internal (C call) parameter lists,
     * plus any input conversion code needed.
     */
    static class ParameterLists {
        final List<String> externalInput = new ArrayList<>();
        final List<String> internalInput = new ArrayList<>();
        final IndentingTextRenderer inputConversions = new IndentingTextRenderer();

        ParameterLists(LibraryDescription lib, MethodDescription desc) {
            // First parameter: self or cls
            if (desc.getIsStatic()) {
                externalInput.add(getReceiverName(desc));
            } else {
                externalInput.add("self");
                internalInput.add("self.%%REF_FIELD%%");
            }

            // Setters can only have one argument, so multi-argument setters get packed into a tuple
            boolean isMultiArgsSetter = GeneratorUtil.isSetter(desc) && desc.getParameters().length() > 1;
            if (isMultiArgsSetter && !desc.hasPythonOverload()) {
                List<String> names = new ArrayList<>();
                for (var parameter : desc.getParameters()) {
                    names.add(parameter.getLowerUnderscoreParamName());
                }
                externalInput.add(desc.getPropertyName() + ": " + getSetterTypeHint(desc));
                inputConversions.addLine(String.join(", ", names) + " = " + desc.getPropertyName());
            } else {
                // Standard parameters -> one for each
                for (var parameter : desc.getParameters()) {
                    String input = parameter.getLowerUnderscoreParamName() + ": " + getPythonTypeHint(lib, parameter);
                    if (parameter.hasDefaultValue()) {
                        input += " = " + convertCDefaultParamString(parameter);
                    }
                    externalInput.add(input);
                }
            }

            // Conversions and C call
            for (var parameter : desc.getParameters()) {
                // Convert from input to C type
                addInputConversion(lib, parameter, inputConversions);

                // Call the C method
                if (parameter.getDataType() == DataType.OBJECT_HANDLE) {
                    internalInput.add(parameter.getLowerUnderscoreParamName() + ".%%REF_FIELD%%");
                } else {
                    internalInput.add(parameter.getLowerUnderscoreParamName());
                }
            }

            // Optional keyword-only construction properties
            var props = getConstructionProperties(lib, desc);
            if (!props.isEmpty()) {
                externalInput.addAll(getPropertyInputParams(props));
            }
        }

        private static void addInputConversion(LibraryDescription lib, TypeDescription desc, IndentingTextRenderer out) {
            String pyType = getCPythonType(desc);
            String name = desc.getLowerUnderscoreParamName();
            out.set("NAME", name).set("CTYPE", pyType);

            var marshaller = getPythonMarshaller(desc);
            if (marshaller.isPresent()) {
                out.add(marshaller.get().pack(name));

            } else if (desc.getDataType() == DataType.STRUCT) {
                // The dataclass is the public face, so the mirror gets packed field by field.
                // The mirror is constructed positionally, so a field the dataclass does not
                // carry still needs its slot.
                List<String> args = new ArrayList<>();
                for (var field : getStructDescription(lib, desc).getFields()) {
                    if (TypeMap.isPointerType(field.getDataType())) {
                        args.add("None");
                        continue;
                    }
                    String value = name + "." + field.getLowerUnderscoreName();
                    args.add(field.getDataType() == DataType.BOOL ? "int(" + value + ")" : value);
                }
                out.setArgs("ARGS", args).add("""
                        if not isinstance(%%NAME%%, %%CTYPE%%):
                            %%NAME%% = %%CTYPE%%(%%ARGS%%)
                        """);

            } else if (desc.getDataType() == DataType.CALLBACK) {
                // Any callable is accepted, a prebuilt prototype passes straight through. The
                // wrapper is a local, so it lives exactly as long as the call.
                out.add("""
                        if %%NAME%% is not None and not isinstance(%%NAME%%, %%CTYPE%%):
                            %%NAME%% = %%CTYPE%%(%%NAME%%)
                        """);

            } else if (pyType.equals("c_char_p")) {
                out.add("""
                        if %%NAME%% is not None and not isinstance(%%NAME%%, c_char_p):
                            %%NAME%% = c_char_p(%%NAME%%.encode('utf-8') if isinstance(%%NAME%%, str) else %%NAME%%)
                        """);

            } else if (pyType.equals("POINTER(c_char_p)")) {
                out.add("""
                        if %%NAME%% is not None and not isinstance(%%NAME%%, POINTER(c_char_p)):
                            encoded_list = [s.encode('utf-8') if isinstance(s, str) else s for s in %%NAME%%]
                            %%NAME%% = (c_char_p * len(encoded_list))(*encoded_list)
                        """);

            } else if (pyType.startsWith("POINTER(c_")) {
                String baseType = pyType.substring("POINTER(".length(), pyType.length() - 1);
                out.set("BASE", baseType).set("NUMPY", getNumpyType(baseType)).add("""
                        if %%NAME%% is not None and not isinstance(%%NAME%%, POINTER(%%BASE%%)):
                            %%NAME%% = np.ascontiguousarray(%%NAME%%, dtype=np.%%NUMPY%%).ctypes.data_as(POINTER(%%BASE%%))
                        """);
            }
        }

        private static String getNumpyType(String baseType) {
            return switch (baseType) {
                case "c_double" -> "float64";
                case "c_float" -> "float32";
                case "c_int" -> "int32";
                case "c_long" -> "int64";
                case "c_short" -> "int16";
                case "c_char" -> "int8";
                case "c_bool" -> "bool";
                case "c_longlong" -> "int64";
                case "c_uint" -> "uint32";
                case "c_ulong" -> "uint64";
                case "c_ushort" -> "uint16";
                case "c_ubyte", "c_uint8" -> "uint8";
                default -> throw new IllegalStateException("Unknown numpy type for base: " + baseType);
            };
        }
    }

    private static String convertCDefaultParamString(TypeDescription param) {
        checkArgument(param.hasDefaultValue(), "Missing default value for c_default_param");
        var string = param.getDefaultValue();
        return switch (param.getDataType()) {
            case BOOL -> Boolean.parseBoolean(string) ? "True" : "False";
            default -> string;
        };
    }

}
