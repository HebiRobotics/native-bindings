package us.hebi.graalvm.bindings.tests;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Guards every file the annotation processor writes.
 * <p>
 * The processor already ran during {@code default-compile}, so this test only compares
 * what is on disk. That makes it fast enough to run on every push and makes a generator
 * refactoring show up as a reviewable diff rather than a pass/fail.
 * <pre>
 *   mvn verify                                     assert
 *   mvn verify -Dbindings.updateReference=true     rewrite, then review 'git diff'
 * </pre>
 *
 * @author Florian Enner
 */
public class GeneratedBindingsTest {

    /**
     * The C, C++, Python and MATLAB bindings, the build files, and api.json.
     */
    @Test
    public void bindingsMatchReferenceFiles() throws IOException {
        ReferenceFiles.assertMatches(
                requiredDir("bindings.generatedDir"),
                referenceDir().resolve("static"));
    }

    /**
     * The generated {@code $CEntries} and {@code $CEnum} Java sources. These define the
     * actual ABI, so a change here is more significant than a change in the wrappers.
     */
    @Test
    public void entryPointsMatchReferenceFiles() throws IOException {
        ReferenceFiles.assertMatches(
                requiredDir("bindings.generatedSourcesDir"),
                referenceDir().resolve("java"));
    }

    private static Path referenceDir() {
        return requiredDir("bindings.referenceDir");
    }

    private static Path requiredDir(String property) {
        String value = System.getProperty(property);
        if (value == null || value.isEmpty()) {
            throw new IllegalStateException("Missing system property '" + property
                    + "'. This test is configured by the surefire plugin and needs to run through Maven.");
        }
        return Paths.get(value);
    }

}
