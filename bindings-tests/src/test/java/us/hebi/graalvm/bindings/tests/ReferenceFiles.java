package us.hebi.graalvm.bindings.tests;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.Assert.*;

/**
 * Compares a directory of generated files against a checked-in reference copy.
 * <p>
 * Run with {@code -Dbindings.updateReference=true} to rewrite the reference copy instead
 * of asserting. Reviewing the resulting git diff is the intended way to see what a change
 * to the generator did to its output.
 * <p>
 * Line endings are normalised to LF on both sides so that the same reference files work
 * on Windows and Linux. Binary files are not supported and none are expected.
 *
 * @author Florian Enner
 */
final class ReferenceFiles {

    static boolean isUpdateMode() {
        return Boolean.getBoolean("bindings.updateReference");
    }

    /**
     * @param actualDir   directory the generator just wrote
     * @param referenceDir directory of checked-in reference files
     */
    static void assertMatches(Path actualDir, Path referenceDir) throws IOException {
        assertTrue("Generated directory does not exist, did the annotation processor run? " + actualDir,
                Files.isDirectory(actualDir));

        var actual = readTree(actualDir);
        if (isUpdateMode()) {
            update(referenceDir, actual);
            return;
        }

        assertTrue("Reference directory does not exist. Create it with -Dbindings.updateReference=true: " + referenceDir,
                Files.isDirectory(referenceDir));
        var expected = readTree(referenceDir);

        var missing = new ArrayList<>(expected.keySet());
        missing.removeAll(actual.keySet());
        var unexpected = new ArrayList<>(actual.keySet());
        unexpected.removeAll(expected.keySet());

        if (!missing.isEmpty() || !unexpected.isEmpty()) {
            fail(String.join("\n",
                    "The set of generated files changed.",
                    "  no longer generated: " + missing,
                    "  newly generated:     " + unexpected,
                    "",
                    "If this is intended, re-run with -Dbindings.updateReference=true and review the diff."));
        }

        var changed = new ArrayList<String>();
        for (var entry : expected.entrySet()) {
            if (!entry.getValue().equals(actual.get(entry.getKey()))) {
                changed.add(entry.getKey());
            }
        }

        if (!changed.isEmpty()) {
            String first = changed.get(0);
            fail(String.join("\n",
                    "Generated content differs from the reference files in " + changed.size() + " file(s):",
                    "  " + changed,
                    "",
                    firstDifference(first, expected.get(first), actual.get(first)),
                    "",
                    "If this is intended, re-run with -Dbindings.updateReference=true and review the diff."));
        }
    }

    private static void update(Path referenceDir, TreeMap<String, String> actual) throws IOException {
        if (Files.isDirectory(referenceDir)) {
            deleteRecursively(referenceDir);
        }
        for (var entry : actual.entrySet()) {
            Path target = referenceDir.resolve(entry.getKey());
            Files.createDirectories(target.getParent());
            Files.writeString(target, entry.getValue(), StandardCharsets.UTF_8);
        }
        System.out.println("Wrote " + actual.size() + " reference file(s) to " + referenceDir);
    }

    /**
     * @return relative slash-separated path to normalised file content, ordered for stable reporting
     */
    private static TreeMap<String, String> readTree(Path root) throws IOException {
        var files = new TreeMap<String, String>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile)
                    .filter(ReferenceFiles::isComparable)
                    .forEach(path -> {
                        String key = root.relativize(path).toString().replace('\\', '/');
                        files.put(key, normalise(readString(path)));
                    });
        }
        return files;
    }

    /**
     * The native-image build drops object files and import libraries into the same tree.
     * They are not generator output and must not end up in the reference set.
     */
    private static boolean isComparable(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String extension = dot < 0 ? "" : name.substring(dot + 1).toLowerCase();
        return !BINARY_EXTENSIONS.contains(extension);
    }

    private static String readString(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + path, e);
        }
    }

    private static String normalise(String content) {
        // Strip the BOM some tools prepend, then unify line endings and the trailing newline
        String text = content.startsWith("﻿") ? content.substring(1) : content;
        return text.replace("\r\n", "\n").stripTrailing() + "\n";
    }

    private static String firstDifference(String name, String expected, String actual) {
        List<String> expectedLines = expected.lines().toList();
        List<String> actualLines = actual.lines().toList();
        int max = Math.max(expectedLines.size(), actualLines.size());
        for (int i = 0; i < max; i++) {
            String e = i < expectedLines.size() ? expectedLines.get(i) : "<end of file>";
            String a = i < actualLines.size() ? actualLines.get(i) : "<end of file>";
            if (!e.equals(a)) {
                return String.join("\n",
                        "First difference in " + name + " at line " + (i + 1) + ":",
                        "  expected: " + e,
                        "  actual:   " + a);
            }
        }
        return "Files differ only in trailing whitespace: " + name;
    }

    private static void deleteRecursively(Path root) throws IOException {
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    throw new UncheckedIOException("Failed to delete " + path, e);
                }
            });
        }
    }

    private static final Set<String> BINARY_EXTENSIONS = Set.of(
            "obj", "o", "lib", "a", "dll", "so", "dylib", "exe", "pdb", "zip", "class",
            "mexw64", "mexa64", "mexmaci64", "pyc", "png", "jpg", "gif", "ico"
    );

    private ReferenceFiles() {
    }

}
