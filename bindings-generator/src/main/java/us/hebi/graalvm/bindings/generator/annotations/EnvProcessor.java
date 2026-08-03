package us.hebi.graalvm.bindings.generator.annotations;

import javax.annotation.processing.ProcessingEnvironment;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * @author Florian Enner
 * @since 12 Jun 2025
 */
public interface EnvProcessor {

    ProcessingEnvironment getEnv();

    default ProcessingEnvironment getNonNullEnvironment() {
        var env = getEnv();
        if (env == null) {
            throw new IllegalStateException("The environment is not available. Generator has not processed any items: " + getClass().getSimpleName());
        }
        return env;
    }

    default GeneratorOptions getOptions() {
        return new GeneratorOptions(getNonNullEnvironment());
    }

    public static class GeneratorOptions {

        public GeneratorOptions(ProcessingEnvironment env) {
            this(env.getOptions());
        }

        public GeneratorOptions(Map<String, String> options) {
            this.options = options;
        }

        public String getLibName() {
            return getOptions().getOrDefault(OPTION_LIB_NAME, "lib");
        }

        public String getLibVersion() {
            return getOptions().getOrDefault(OPTION_LIB_VERSION, "0.0.0");
        }

        public String getLibBuild() {
            return getOptions().getOrDefault(OPTION_LIB_BUILD, "0");
        }

        /**
         * Base url of the directory that holds the release archives, e.g.,
         * {@code https://example.com/downloads/1.2.3}. The loaders append
         * {@code <libName>-<libVersion>-<platform>.zip}. Empty means that the
         * generated loaders do not download anything.
         */
        public Optional<String> getDownloadUrl() {
            return Optional.ofNullable(getOptions().get(OPTION_DOWNLOAD_URL))
                    .filter(Predicate.not(String::isBlank));
        }

        public String getNamespace() {
            return getOptions().getOrDefault(OPTION_NAMESPACE, "lib");
        }

        public String getCPrefix() {
            return Optional.ofNullable(getOptions().getOrDefault(OPTION_C_PREFIX, null))
                    .orElseGet(() -> getNamespace().replaceAll("\\.", "_"));
        }

        public boolean enableGraphics() {
            return Boolean.parseBoolean(getOptions().getOrDefault(OPTION_ENABLE_GRAPHICS, "false"));
        }

        public Map<String, String> getOptions() {
            return options;
        }

        final Map<String, String> options;

    }

    /**
     * Prefix for all annotation processor options, e.g., {@code -Abindings.libName=mylib}
     */
    String OPTION_PREFIX = "bindings.";

    String OPTION_OUTPUT_DIR = OPTION_PREFIX + "outputDir";
    String OPTION_LIB_NAME = OPTION_PREFIX + "libName";
    String OPTION_LIB_VERSION = OPTION_PREFIX + "libVersion";
    String OPTION_LIB_BUILD = OPTION_PREFIX + "libBuild";
    String OPTION_DOWNLOAD_URL = OPTION_PREFIX + "downloadUrl";
    String OPTION_NAMESPACE = OPTION_PREFIX + "namespace";
    String OPTION_C_PREFIX = OPTION_PREFIX + "cPrefix";
    String OPTION_ENABLE_GRAPHICS = OPTION_PREFIX + "enableGraphics";

}
