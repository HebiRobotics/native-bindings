package us.hebi.graalvm.bindings.generator.bindings;

import com.google.common.base.CaseFormat;
import us.hebi.graalvm.bindings.generator.bindings.features.FeatureGenerator;
import us.hebi.graalvm.bindings.generator.generators.GeneratorUtil;
import us.hebi.graalvm.bindings.generator.generators.TemplateString;
import us.hebi.graalvm.bindings.model.*;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;

import static us.hebi.graalvm.bindings.generator.generators.DocumentationUtil.*;
import static us.hebi.quickbuf.ProtoUtil.*;

/**
 * @author Florian Enner
 * @since 16 Jul 2024
 */
public class PythonBindingsGenerator extends BindingGenerator {

    private final String libField;

    public PythonBindingsGenerator(LibraryDescription lib) {
        super(lib);
        this.libField = "lib" + lib.getName();
    }

    @Override
    public void writeTo(Filer filer) throws IOException {
        // For now we only need a single Python file that can just live in the main binding directory
        filer.createFile("", "__init__.py", file -> file.write("from ." + lib.getName() + " import *"));
        filer.createFile("", lib.getName() + ".py", file -> file.write(createLibLoaderSource()));
    }

    private String createLibLoaderSource() {
        var apiClasses = getApiClasses();
        var content = new StringBuilder();

        appendLibLoader(content);
        appendCStructDefinitions(content, apiClasses);
        appendTypedefs(content);
        appendCFunctionSignatures(content, apiClasses);
        appendEnumWrappers(content);
        appendClassWrappers(content, apiClasses);

        for (FeatureGenerator feature : features) {
            content.append(TemplateString.of(feature.getPythonWrapper())
                    .set("LIB_FIELD", libField)
                    .render());
        }

        return content.toString();
    }

    private void appendLibLoader(StringBuilder content) {
        content.append(TemplateString.of("""
                        from __future__ import annotations
                        from ctypes import *
                        from enum import IntEnum
                        import os
                        import platform
                        from typing import Sequence, Tuple, Any

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

                                # If necessary, start a new download it into a shared dir. All files are
                                # kept so we can share a single download for all languages.
                                if not os.path.exists(lib_path):
                                    import urllib.request
                                    import zipfile

                                    # Build download url
                                    dl_name = f"{lib_name}-{version}-{lib_platform}.zip"
                                    url = f"https://files.hebi.us/download/{lib_name}/snapshot/{build_number}/{dl_name}"

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
                        """)
                .set("LIB_NAME", lib.getName())
                .set("LIB_VERSION", lib.getVersion())
                .set("LIB_BUILD_NUMBER", lib.getBuild())
                .set("LIB_FIELD", libField)
                .render());
    }

    private void appendCStructDefinitions(StringBuilder content, List<ClassDescription> apiClasses) {
        for (ClassDescription apiClass : apiClasses) {
            content.append(TemplateString.of("""
                            class struct_%%CLASS_NAME%%_(Structure):
                                pass
                            %%CPYTHON_TYPE%% = POINTER(struct_%%CLASS_NAME%%_)

                            """)
                    .set("CLASS_NAME", apiClass.getType().getTypeName())
                    .set("CPYTHON_TYPE", getCPythonType(apiClass.getType()))
                    .render());
        }
    }

    private void appendTypedefs(StringBuilder content) {
        for (var type : lib.getTypedefs()) {
            content.append(type.getPythonDefinition()).append("\n");
        }
    }

    private void appendCFunctionSignatures(StringBuilder content, List<ClassDescription> apiClasses) {
        for (ClassDescription apiClass : apiClasses) {
            content.append("\n# ==== ").append(apiClass.getType().getTypeName()).append(" ====\n");

            List<String> params = new ArrayList<>();
            for (MethodDescription method : apiClass.getMethods()) {
                params.clear();
                if (!method.getIsStatic()) {
                    params.add(getCPythonType(apiClass.getType()));
                }
                for (TypeDescription parameter : method.getParameters()) {
                    params.add(getCPythonType(parameter));
                }

                content.append(TemplateString.of("""
                                %%LIB_FIELD%%.%%CENTRY_NAME%%.argtypes = [%%ARG_TYPES%%]
                                %%LIB_FIELD%%.%%CENTRY_NAME%%.restype = %%RETURN_TYPE%%
                                """)
                        .set("LIB_FIELD", libField)
                        .set("CENTRY_NAME", method.getCName())
                        .set("ARG_TYPES", joiner.join(params))
                        .set("RETURN_TYPE", getCPythonType(method.getReturnType()))
                        .render());
            }

            if (apiClass.getInstantiable() && !apiClass.hasParent()) {
                content.append(TemplateString.of("""
                                %%LIB_FIELD%%.%%C_DESTRUCTOR%%.argtypes = [%%CPYTHON_TYPE%%]
                                %%LIB_FIELD%%.%%C_DESTRUCTOR%%.restype = None
                                """)
                        .set("LIB_FIELD", libField)
                        .set("C_DESTRUCTOR", apiClass.getCDestructorName())
                        .set("CPYTHON_TYPE", getCPythonType(apiClass.getType()))
                        .render());
            }

            if (apiClass.hasParent()) {
                content.append(TemplateString.of("""
                                %%LIB_FIELD%%.%%CAST%%.argtypes = [%%CPYTHON_TYPE%%]
                                %%LIB_FIELD%%.%%CAST%%.restype = %%PARENT_TYPE%%
                                """)
                        .set("LIB_FIELD", libField)
                        .set("CPYTHON_TYPE", getCPythonType(apiClass.getType()))
                        .set("PARENT_TYPE", getCPythonType(apiClass.getParent()))
                        .set("CAST", apiClass.getToParentCastMethod().getCName())
                        .render());
            }
        }
    }

    private void appendEnumWrappers(StringBuilder content) {
        if (lib.getEnums().length() == 0) return;

        content.append("\n# ==== Enum Wrappers ====\n");
        for (EnumDescription generatedEnum : lib.getEnums()) {
            content.append("class %s(IntEnum):\n".formatted(generatedEnum.getName()));

            for (EnumValueDescription value : generatedEnum.getValues()) {
                String pep8 = value.getName().toUpperCase(Locale.ENGLISH);
                if (!pep8.equals(value.getName())) {
                    pep8 = CaseFormat.UPPER_CAMEL.to(CaseFormat.UPPER_UNDERSCORE, value.getName());
                }
                content.append("    %s = %d\n".formatted(pep8, value.getCValue()));
            }

            content.append("\n");
        }
    }

    private void appendClassWrappers(StringBuilder content, List<ClassDescription> apiClasses) {
        content.append("\n# ==== Class Wrappers ====");
        for (ClassDescription apiClass : apiClasses) {
            content.append(createClassWrapperSource(apiClass));
        }
    }

    private String createClassWrapperSource(ClassDescription apiClass) {
        String className = apiClass.getType().getTypeName();
        String refField = !apiClass.hasParent() ? "ptr" : "ref_" + className;
        String parentSuffix = apiClass.hasParent() ? "(" + apiClass.getParent().getTypeName() + ")" : "";

        var content = new StringBuilder();
        content.append("\n");
        content.append("class ").append(className).append(parentSuffix).append(":\n");

        if (apiClass.getType().hasComment()) {
            content.append(generatePydoc("    ", apiClass.getType())).append("\n\n");
        }

        // Fixed slots (fields) to prevent dynamic fields and dictionary overhead
        if (apiClass.getInstantiable()) {
            content.append("    __slots__ = ('%s',)\n\n".formatted(refField));
        } else if (apiClass.hasChildren() || apiClass.hasParent()) {
            content.append("    __slots__ = ()\n\n"); // dummy to prevent __dict__ creation
        }

        // Constructor / init
        content.append(buildConstructor(apiClass, className, refField));

        // Methods and properties
        Set<String> getters = new HashSet<>();
        for (MethodDescription method : apiClass.getMethods()) {
            if (GeneratorUtil.isConstructor(method)) continue;

            if (GeneratorUtil.isGetter(method)) {
                getters.add(method.getPropertyName());
            } else if (GeneratorUtil.isSetter(method)) {
                boolean needsDummyGetter = getters.add(method.getPropertyName());
                if (needsDummyGetter) {
                    String propName = getPropertyName(method);
                    content.append(TemplateString.of("""
                                @property
                                def %%PROP%%(self):
                                    raise AttributeError("Property '%%PROP%%' is write-only")

                            """).set("PROP", propName).render());
                }
            }

            content.append(getWrapperMethod(apiClass, method, refField));
        }

        return TemplateString.of(content.toString())
                .set("LIB_FIELD", libField)
                .set("REF_FIELD", refField)
                .set("CLASS_NAME", className)
                .render();
    }

    private String buildConstructor(ClassDescription apiClass, String className, String refField) {
        if (!apiClass.getInstantiable()) {
            return """
                        def __new__(cls, *args, **kwargs):
                            raise TypeError('Class is not instantiable')

                    """;
        }

        // Check for a defined constructor first - it gets its own wrapper method
        final String initMethod;
        var constructor = GeneratorUtil.getConstructor(apiClass);
        if (constructor.isPresent()) {
            // A defined constructor means that users instantiate this class by themselves. Currently
            // this means that we do not have a raw ptr argument and thus can't support being called
            // from child constructors
            checkArgument(!apiClass.hasChildren(), "Constructors are currently not supported parent classes");
            List<MethodDescription> props = getConstructionProperties(lib, constructor.get());
            initMethod = getWrapperMethod(apiClass, constructor.get(), refField)
                         + "%%SUPER_INIT%%";
        } else {
            // Not defining a constructor means that this class can't be instantiated by users
            // and that we only need to support an 'internal' constructor for generating wrappers
            initMethod = """
                        def __init__(self, %%REF%%: %%CLASS_PTR%%):
                            if not isinstance(%%REF%%, %%CLASS_PTR%%):
                                raise TypeError(f'Expected %%CLASS_PTR%%, got {type(%%REF%%).__name__}')
                            self.%%REF%% = %%REF%%%%SUPER_INIT%%

                    """;
        }
        final String superInit = apiClass.hasParent() ? "\n        super().__init__(%%LIB%%.%%CAST%%(%%REF%%))" : "";

        // Always add a destructor
        final var delMethod = !apiClass.hasParent() ? """
                    def __del__(self):
                        if self.%%REF%% is not None:
                            %%LIB%%.%%DESTR%%(self.%%REF%%)
                        self.%%REF%% = None

                """ : """
                    def __del__(self):
                        super().__del__()
                        self.%%REF%% = None

                """;

        // Create a __repr__ method for text display, e.g., Axis(min={self.min}, max={self.max})
        List<String> getProp = new ArrayList<>();
        for (var getter : GeneratorUtil.getGetters(lib, apiClass.getType())) {
            String prop = getPropertyName(getter);
            getProp.add(prop + "={self." + prop + "!r}");
        }
        final var reprMethod = """
                    def __repr__(self):
                        return f"{self.__class__.__name__}(%%PROPGET%%)"

                """
                .replace("%%PROPGET%%", String.join(", ", getProp));

        return TemplateString.of(initMethod + delMethod + reprMethod)
                .set("SUPER_INIT", superInit)
                .set("DESTR", apiClass.getCDestructorName())
                .set("CLASS_PTR", getCPythonType(apiClass.getType()))
                .set("CAST", apiClass.getToParentCastMethod().getCName())
                .set("LIB", libField)
                .set("REF", refField)
                .render();
    }

    private static String getPropertyName(MethodDescription method) {
        return CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, method.getPropertyName());
    }

    private String getWrapperMethod(ClassDescription apiClass, MethodDescription method, String refField) {
        var paramLists = new ParameterLists(lib, method);

        var content = new StringBuilder();
        Function<String, String> indent = str -> str.replace("\n", "\n        ");

        // Decorators
        if (method.getIsStatic() && !method.getIsConstructor()) {
            content.append("    @classmethod\n");
        }

        // Documentation
        String docs = method.hasDocumentation()
                ? escapeBackslashes(indent.apply("\n" + generatePydoc("", method)))
                : "";

        String funcName;
        String propName = getPropertyName(method);
        String returnHint = " -> " + TypeMap.toPythonTypeHint(method.getReturnType());

        if (GeneratorUtil.isSetter(method)) {
            content.append("    @").append(propName).append(".setter\n");
            funcName = propName;
            returnHint = "";
        } else if (GeneratorUtil.isGetter(method)) {
            content.append("    @property\n");
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
                .map(indent)
                .orElseGet(() -> joiner.join(paramLists.externalInput));
        var callArgs = overload.tryGetCall()
                .map(string -> method.getIsStatic() ? string : "self." + refField + ", " + string)
                .map(indent)
                .orElseGet(() -> joiner.join(paramLists.internalInput));
        var body = overload.tryGetBody().map(indent).orElse("");
        var conversions = indent.apply(paramLists.inputConversions);
        var postBody = overload.tryGetPostBody().map(indent).orElse("");
        var numpy = isUsingNumpy(body, conversions, postBody) ? indent.apply("np = _require_numpy()\n") : "";

        // C call and type conversion
        var returnTemplate = indent.apply(getReturnTemplate(apiClass, method)
                .set("CALL", "%%LIB_FIELD%%.%%CNAME%%(%%CALL_ARGS%%)")
                .render());

        // Build the full method body
        content.append(TemplateString.of("""
                            def %%METHOD%%(%%PARAMS%%)%%HINT%%:%%DOCS%%
                                %%NUMPY%%%%BODY%%%%CONVERSIONS%%%%POST_BODY%%%%RETURN%%

                        """)
                .set("RETURN", returnTemplate)

                .set("METHOD", funcName)
                .set("PARAMS", parameters)
                .set("HINT", returnHint)
                .set("DOCS", docs)

                .set("NUMPY", numpy)
                .set("BODY", body)
                .set("CONVERSIONS", conversions)
                .set("POST_BODY", postBody)

                .set("LIB_FIELD", libField)
                .set("CNAME", method.getCName())
                .set("CALL_ARGS", callArgs)
                .render());

        return content.toString();
    }

    private boolean isUsingNumpy(String... snippets) {
        for (var snippet : snippets) {
            if (snippet.contains("np.")) {
                return true;
            }
        }
        return false;
    }

    private TemplateString getReturnTemplate(ClassDescription apiClass, MethodDescription desc) {
        final boolean isConstructor = desc.getIsConstructor();
        final var props = getConstructionProperties(lib, desc);
        String string = switch (desc.getReturnType().getDataType()) {
            case OBJECT_HANDLE -> {
                String template = """
                        ptr_ = %%CALL%%
                        if not ptr_:
                            raise RuntimeError(f'Failed to create %%RETURN_TYPE%% in %%CLASS_NAME%%::%%METHOD%%%%REASON%%')
                        """;
                if (isConstructor) {
                    template += "self.%%REF_FIELD%% = ptr_\n"
                                + getPropertySetBlock(props, "self", "");
                } else if (!props.isEmpty()) {
                    template += "obj_ = %%RETURN_TYPE%%(ptr_)\n"
                                + getPropertySetBlock(props, "obj_", "")
                                + "return obj_";
                } else {
                    template += "return %%RETURN_TYPE%%(ptr_)\n";
                }
                yield template;
            }
            case VOID_WITH_ERROR_CODE -> """
                    status_ = %%CALL%%
                    if status_ != 0:
                        raise RuntimeError(f'Encountered error in %%CLASS_NAME%%::%%METHOD%%%%REASON%%')
                    """;
            case CHAR_POINTER -> """
                    ptr_ = %%CALL%%
                    return ptr_.decode("utf-8") if ptr_ is not None else ""
                    """;
            case ENUM -> "return %%RETURN_TYPE%%(%%CALL%%)";
            case BOOL -> "return %%CALL%% != 0";
            case VOID -> "%%CALL%%";
            default -> "return %%CALL%%";
        };

        return TemplateString.of(string.stripTrailing())
                .set("REASON", getErrorReason(apiClass, desc))
                .set("RETURN_TYPE", desc.getReturnType().getTypeName());
    }

    private String getErrorReason(ClassDescription apiClass, MethodDescription desc) {
        var cErrorMethod = getLastErrorMethod(apiClass);
        if (cErrorMethod == null) return "";

        return " ({%%LIB_FIELD%%.%%C_ERROR_METHOD%%().decode(\"utf-8\")})"
                .replace("%%C_ERROR_METHOD%%", cErrorMethod);
    }

    private static String getCPythonType(TypeDescription typeDescription) {
        return switch (typeDescription.getDataType()) {
            case OBJECT_HANDLE -> typeDescription.getTypeName() + "Ptr";
            case TYPEDEF -> typeDescription.getTypeName();
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

    /**
     * Builds the external (Python signature) and internal (C call) parameter lists,
     * plus any input conversion code needed.
     */
    static class ParameterLists {
        final List<String> externalInput = new ArrayList<>();
        final List<String> internalInput = new ArrayList<>();
        final String inputConversions;

        ParameterLists(LibraryDescription lib, MethodDescription desc) {
            boolean isConstructor = desc.getIsConstructor();
            var conversions = new StringBuilder();

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
                conversions.append(String.join(", ", names)).append(" = ").append(desc.getPropertyName()).append("\n");
            } else {
                // Standard parameters -> one for each
                for (var parameter : desc.getParameters()) {
                    String input = parameter.getLowerUnderscoreParamName() + ": " + TypeMap.toPythonTypeHint(parameter);
                    if (parameter.hasDefaultValue()) {
                        input += " = " + convertCDefaultParamString(parameter);
                    }
                    externalInput.add(input);
                }
            }

            // Conversions and C call
            for (var parameter : desc.getParameters()) {
                // Convert from input to C type
                appendInputConversion(parameter, conversions);

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

            this.inputConversions = conversions.toString();
        }

        private static void appendInputConversion(TypeDescription desc, StringBuilder out) {
            String pyType = getCPythonType(desc);
            String name = desc.getLowerUnderscoreParamName();

            if (pyType.equals("c_char_p")) {
                out.append(TemplateString.of("""
                        if %%NAME%% is not None and not isinstance(%%NAME%%, c_char_p):
                            %%NAME%% = c_char_p(%%NAME%%.encode('utf-8') if isinstance(%%NAME%%, str) else %%NAME%%)
                        """).set("NAME", name).render());

            } else if (pyType.equals("POINTER(c_char_p)")) {
                out.append(TemplateString.of("""
                        if %%NAME%% is not None and not isinstance(%%NAME%%, POINTER(c_char_p)):
                            encoded_list = [s.encode('utf-8') if isinstance(s, str) else s for s in %%NAME%%]
                            %%NAME%% = (c_char_p * len(encoded_list))(*encoded_list)
                        """).set("NAME", name).render());

            } else if (pyType.startsWith("POINTER(c_")) {
                String baseType = pyType.substring("POINTER(".length(), pyType.length() - 1);
                out.append(TemplateString.of("""
                                if %%NAME%% is not None and not isinstance(%%NAME%%, POINTER(%%BASE%%)):
                                    %%NAME%% = np.ascontiguousarray(%%NAME%%, dtype=np.%%NUMPY%%).ctypes.data_as(POINTER(%%BASE%%))
                                """)
                        .set("NAME", name)
                        .set("BASE", baseType)
                        .set("NUMPY", getNumpyType(baseType))
                        .render());
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
                case "c_ubyte" -> "uint8";
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
