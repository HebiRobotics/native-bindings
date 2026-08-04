package us.hebi.graalvm.bindings.tests;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import us.hebi.graalvm.bindings.generator.generators.NativeAnnotationProcessor;

import javax.tools.JavaFileObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static com.google.testing.compile.CompilationSubject.*;
import static org.junit.Assert.*;

/**
 * Drives the processor twice over the same class, once with {@code -Abindings.downloadUrl}
 * and once without, to pin both shapes of the generated loaders.
 * <p>
 * The reference files can only cover one of them, because a library is generated with one
 * set of options at a time. They cover the configured shape, so the shape an external user
 * sees by default, a loader that looks locally and never reaches the network, is asserted
 * here.
 *
 * @author Florian Enner
 */
public class DownloadUrlOptionTest {

    private static final String URL = "https://example.com/download/1.2.3";

    @Rule
    public final TemporaryFolder outputDir = new TemporaryFolder();

    @Test
    public void emitsTheConfiguredUrl() throws IOException {
        assertThat(compile(URL)).succeeded();

        assertContains(read("bindings/mylib.py"), "url = f\"" + URL + "/{dl_name}\"");
        assertContains(read("bindings/mylib.cmake"), "set(MYLIB_DL_URL \"" + URL + "/${MYLIB_DL_NAME}\")");
        assertContains(read("bindings/+mylib/private/locate_binary.m"), "download_binary(downloadDir,");
        assertContains(read("bindings/+mylib/private/download_binary.m"), "url = ['" + URL + "/' dlName];");
    }

    @Test
    public void omitsTheDownloadWhenNoUrlIsConfigured() throws IOException {
        assertThat(compile(null)).succeeded();

        assertContainsNot(read("bindings/mylib.py"), "urllib");
        assertContainsNot(read("bindings/mylib.cmake"), "file(DOWNLOAD");
        assertContainsNot(read("bindings/+mylib/private/locate_binary.m"), "download_binary");
        assertFalse("download_binary.m must not be generated without a url",
                Files.exists(path("bindings/+mylib/private/download_binary.m")));

        // The loaders keep looking in the download cache, a lookup that simply comes up empty
        assertContains(read("bindings/mylib.py"), "cache_home");
        assertContains(read("bindings/+mylib/private/locate_binary.m"), "cacheHome");

        // CMake instead points at the bundled location and fails there, because pointing the
        // link at a cache directory nothing ever fills only moves the error to the linker
        assertContainsNot(read("bindings/mylib.cmake"), "MYLIB_DOWNLOAD_DIR");
        assertContains(read("bindings/mylib.cmake"), "message(FATAL_ERROR");
    }

    private static void assertContains(String content, String expected) {
        assertTrue("Expected to find '" + expected + "' in:\n" + content, content.contains(expected));
    }

    private static void assertContainsNot(String content, String unexpected) {
        assertFalse("Expected no '" + unexpected + "' in:\n" + content, content.contains(unexpected));
    }

    private String read(String relativePath) throws IOException {
        Path path = path(relativePath);
        assertTrue("Generated file does not exist: " + path, Files.exists(path));
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private Path path(String relativePath) {
        return outputDir.getRoot().toPath().resolve(relativePath);
    }

    private Compilation compile(String downloadUrl) {
        List<String> options = new ArrayList<>(List.of(
                "-Abindings.outputDir=" + outputDir.getRoot().getAbsolutePath(),
                "-Abindings.libName=mylib",
                "-Abindings.namespace=mylib"));
        if (downloadUrl != null) {
            options.add("-Abindings.downloadUrl=" + downloadUrl);
        }
        return Compiler.javac()
                // A new instance per compilation, see the note in ProcessorValidationTest
                .withProcessors(new NativeAnnotationProcessor())
                .withOptions(options)
                .compile(source());
    }

    private static JavaFileObject source() {
        return JavaFileObjects.forSourceString("mylib.Ok", String.join("\n",
                "package mylib;",
                "import us.hebi.graalvm.bindings.annotations.*;",
                "import static us.hebi.graalvm.bindings.annotations.MethodFlag.*;",
                "@CLibClass(\"Ok\")",
                "public class Ok {",
                "    @CLibMethod(flags = Noexcept)",
                "    public static int value() { return 1; }",
                "}"));
    }

}
