package us.hebi.graalvm.bindings.tests.api.generated;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Objects;
import org.graalvm.nativeimage.c.CContext;

/**
 * Provides the struct definition that the GraalVM {@code @CStruct} layout probe
 * compiles against. The header ships as a classpath resource and gets extracted
 * to a temporary directory at image build time, so that consuming projects need
 * no build wiring.
 */
public final class TestCallbacks_Progress$$Directives implements CContext.Directives {
    private static final String HEADER_NAME = "TestCallbacks_Progress.h";

    @Override
    public List<String> getHeaderFiles() {
        // Forward slashes on purpose: backslashes are escape characters in a C include
        return List.of("\"" + extractHeader().replace('\\', '/') + "\"");
    }

    private static String extractHeader() {
        String resource = "/us/hebi/graalvm/bindings/tests/api/generated/" + HEADER_NAME;
        try (InputStream input = TestCallbacks_Progress$$Directives.class.getResourceAsStream(resource)) {
            Objects.requireNonNull(input, "Missing header resource: " + resource);
            Path header = Files.createTempDirectory("bindings_include").resolve(HEADER_NAME);
            Files.copy(input, header, StandardCopyOption.REPLACE_EXISTING);
            return header.toAbsolutePath().toString();
        } catch (IOException ioe) {
            throw new UncheckedIOException("Failed to extract " + HEADER_NAME, ioe);
        }
    }
}
