package us.hebi.graalvm.bindings.generator.bindings.features;

import us.hebi.graalvm.bindings.generator.annotations.AnnotatedCLibClass;
import us.hebi.graalvm.bindings.generator.annotations.EnvProcessor;
import us.hebi.graalvm.bindings.generator.generators.IndentingTextRenderer;
import us.hebi.graalvm.bindings.model.LibraryDescription;

/**
 * @author Florian Enner
 * @since 17 Jun 2025
 */
public class LibraryInfoFeature implements FeatureGenerator {

    @Override
    public String getCHeader(boolean isCpp) {
        if (isCpp) {
            return render("""
                    void %%GET_LIBRARY_VERSION%%(int* major, int* minor, int* patch, int* build);
                    """);
        } else {
            // The header answers its own version, so this needs internal linkage: the shim
            // carries a copy of the same function, and a consumer linking the static archive
            // would otherwise collide with it. __inline covers MSVC's legacy C mode, which
            // does not know the C99 keyword.
            return render("""
                    %%DLL_PUBLIC%%void %%GET_LIBRARY_VERSION%%(int* major, int* minor, int* patch, int* build);
                    static __inline void %%GET_HEADER_VERSION%%(int* major, int* minor, int* patch, int* build) {
                        *major = %%VERSION_MAJOR%%;
                        *minor = %%VERSION_MINOR%%;
                        *patch = %%VERSION_PATCH%%;
                        *build = %%BUILD_NUMBER%%;
                    }
                    """);
        }

    }

    @Override
    public String getCSource() {
        return render("""
                void %%GET_LIBRARY_VERSION%%(int* major, int* minor, int* patch, int* build) {
                    *major = %%VERSION_MAJOR%%;
                    *minor = %%VERSION_MINOR%%;
                    *patch = %%VERSION_PATCH%%;
                    *build = %%BUILD_NUMBER%%;
                }
                """);
    }

    @Override
    public String getObjCSource() {
        return "";
    }

    @Override
    public String getCppWrapper(boolean useDynamicLookup) {
        return renderer()
                .setIf("METHOD_LOOKUP", useDynamicLookup, "\n  static auto %%GET_LIBRARY_VERSION%% = DynamicLookup::instance().getFunc<void(*)(int*, int*, int*, int*)>(\"%%GET_LIBRARY_VERSION%%\");")
                .set("IS_AVAILABLE", useDynamicLookup ? "DynamicLookup::instance().isLoaded()" : "true")
                .add("""

                // Library Information
                namespace lib {

                struct Version {
                  Version(int major, int minor, int patch, int build) : major_(major), minor_(minor), patch_(patch), build_(build) {}
                  int major_{};
                  int minor_{};
                  int patch_{};
                  int build_{};
                };

                inline Version getHeaderVersion() {
                  return {%%VERSION_MAJOR%%, %%VERSION_MINOR%%, %%VERSION_PATCH%%, %%BUILD_NUMBER%%};
                }

                inline Version getLibraryVersion() {
                  Version v(0,0,0,0);%%METHOD_LOOKUP%%
                  %%GET_LIBRARY_VERSION%%(&v.major_, &v.minor_, &v.patch_, &v.build_);
                  return v;
                }

                inline bool isAvailable() { return %%IS_AVAILABLE%%; }

                } // namespace lib

                """)
                .render();
    }

    public String getVersionCompatibilityCheckCpp() {
        return render("""
                  // Check version compatibility. Note that we haven't finished the constructor, so we can't
                  // just use the API method as it would cause a recursive call stack.
                  lib::Version header_version = lib::getHeaderVersion();
                  lib::Version lib_version(-1,0,0,0);
                  auto %%GET_LIBRARY_VERSION%% = getFunc<void(*)(int*, int*, int*, int*)>("%%GET_LIBRARY_VERSION%%");
                  %%GET_LIBRARY_VERSION%%(&lib_version.major_, &lib_version.minor_, &lib_version.patch_, &lib_version.build_);

                  bool is_compatible = (lib_version.major_ == header_version.major_ &&
                                        lib_version.minor_ >= header_version.minor_ &&
                                        (lib_version.minor_ > header_version.minor_ || lib_version.patch_ >= header_version.patch_));
                  if (!is_compatible) {
                    std::cerr << "Incompatible %%CPP_NAMESPACE%% library version found!\\n";
                #ifdef WIN32
                    if (!::FreeLibrary((HMODULE)lib_))
                      std::cerr << "Error closing %%CPP_NAMESPACE%% library!\\n";
                #else
                    if (dlclose(lib_) != 0)
                      std::cerr << "Error closing %%CPP_NAMESPACE%% library!\\n";
                #endif
                    lib_ = nullptr;
                  }
                """);
    }

    @Override
    public String getPythonWrapper() {
        return "";
    }

    private String render(String template) {
        return renderer().add(template).render();
    }

    /**
     * The version and the two C symbols. Tokens of the surrounding file pass through.
     */
    private IndentingTextRenderer renderer() {
        String noClass = "";
        return new IndentingTextRenderer()
                .set("VERSION_MAJOR", version.getMajor())
                .set("VERSION_MINOR", version.getMinor())
                .set("VERSION_PATCH", version.getPatch())
                .set("BUILD_NUMBER", buildNumber)
                .set("GET_LIBRARY_VERSION", AnnotatedCLibClass.getCEntryName(true, prefix, noClass, "getLibraryVersion"))
                .set("GET_HEADER_VERSION", AnnotatedCLibClass.getCEntryName(true, prefix, noClass, "getHeaderVersion"));
    }

    public LibraryInfoFeature(LibraryDescription desc) {
        prefix = desc.getCPrefix();
        version = new SemanticVersion(desc.getVersion());
        buildNumber = desc.getBuild();
    }

    public LibraryInfoFeature(EnvProcessor.GeneratorOptions options) {
        prefix = options.getCPrefix();
        version = new SemanticVersion(options.getLibVersion());
        buildNumber = options.getLibBuild();
    }

    final String prefix;
    final SemanticVersion version;
    final String buildNumber;

    static class SemanticVersion {
        public SemanticVersion(String version) {
            try {
                String[] parts = version.split("\\.");
                major = Integer.parseInt(parts[0]);
                minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
                patch = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
            } catch (NullPointerException | NumberFormatException ex) {
                throw new IllegalArgumentException("Version must be a semantic version string, e.g., 'major.minor'. Found: " + version);
            }
        }

        public int getMajor() {
            return major;
        }

        public int getMinor() {
            return minor;
        }

        public int getPatch() {
            return patch;
        }

        final int major;
        final int minor;
        final int patch;
    }

}
