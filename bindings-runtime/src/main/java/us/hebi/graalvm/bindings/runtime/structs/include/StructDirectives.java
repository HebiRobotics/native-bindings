package us.hebi.graalvm.bindings.runtime.structs.include;

import org.graalvm.nativeimage.c.CContext;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Objects;

/**
 * Provides the struct definitions that the GraalVM {@code @CStruct} layout probe
 * compiles against. The header ships as a classpath resource so that consuming
 * projects need no build wiring: it gets extracted to a temporary directory at
 * image build time and referenced by absolute path.
 * <p>
 * Note that we can't auto-generate it because we can't add a dependency on the
 * generator.
 *
 * @author Florian Enner
 * @since 01 Aug 2026
 */
public class StructDirectives implements CContext.Directives {

    @Override
    public List<String> getHeaderFiles() {
        // Forward slashes on purpose: backslashes are escape characters in a C include
        return List.of("\"" + extractHeader().replace('\\', '/') + "\"");
    }

    private static String extractHeader() {
        String resource = "/us/hebi/graalvm/bindings/runtime/structs/include/" + HEADER_NAME;
        try (InputStream input = StructDirectives.class.getResourceAsStream(resource)) {
            Objects.requireNonNull(input, "Missing header resource: " + resource);
            Path header = Files.createTempDirectory("hebi_bindings_include").resolve(HEADER_NAME);
            Files.copy(input, header, StandardCopyOption.REPLACE_EXISTING);
            return header.toAbsolutePath().toString();
        } catch (IOException ioe) {
            throw new UncheckedIOException("Failed to extract " + HEADER_NAME, ioe);
        }
    }

    private static final String HEADER_NAME = "bindings_structs.h";

}
