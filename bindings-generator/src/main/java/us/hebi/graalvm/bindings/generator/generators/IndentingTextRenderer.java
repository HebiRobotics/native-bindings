
package us.hebi.graalvm.bindings.generator.generators;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;

import static com.google.common.base.Preconditions.*;

/**
 * A StringBuilder-like utility that automatically prepends a configurable
 * indent to every appended line, and supports %%KEY%% variable substitution.
 *
 * @author Florian Enner
 * @since 16 Mar 2026
 */
public class IndentingTextRenderer {

    /**
     * Wraps an already indented block for %%KEY%% substitution only.
     */
    public static IndentingTextRenderer of(String content) {
        return new IndentingTextRenderer().add(content);
    }

    public IndentingTextRenderer indent() {
        return indentBy(1);
    }

    public IndentingTextRenderer unindent() {
        return indentBy(-1);
    }

    public IndentingTextRenderer beginControlFlow(String text) {
        return addLine(text).indent();
    }

    public IndentingTextRenderer endControlFlow(String text) {
        return unindent().addLine(text);
    }

    public IndentingTextRenderer indent(int level) {
        this.indentLevel = Math.max(0, level);
        return this;
    }

    /**
     * Increases the indent level by {@code levels}.
     */
    public IndentingTextRenderer indentBy(int levels) {
        this.indentLevel += levels;
        return this;
    }

    /**
     * Decreases the indent level by {@code levels}.
     */
    public IndentingTextRenderer unindentBy(int levels) {
        this.indentLevel -= levels;
        return this;
    }

    /**
     * Sets (or replaces) the value for {@code %%key%%} substitution.
     */
    public IndentingTextRenderer set(String key, String value) {
        variables.put("%%" + key + "%%", value);
        return this;
    }

    public IndentingTextRenderer set(String key, int value) {
        return set(key, Integer.toString(value));
    }

    /**
     * Sets the value to the comma separated arguments
     */
    public IndentingTextRenderer setArgs(String key, Iterable<String> args) {
        return set(key, String.join(", ", args));
    }

    /**
     * Sets the value if the condition holds, and to an empty string otherwise
     */
    public IndentingTextRenderer setIf(String key, boolean condition, String value) {
        return set(key, condition ? value : "");
    }

    public IndentingTextRenderer remove(String key) {
        variables.remove("%%" + key + "%%");
        return this;
    }

    /**
     * Removes all variables.
     */
    public IndentingTextRenderer clearVariables() {
        variables.clear();
        return this;
    }

    // =========================================================================
    // Append
    // =========================================================================

    public IndentingTextRenderer add(int number) {
        buffer.append(number);
        return this;
    }

    public IndentingTextRenderer addLine() {
        add("\n");
        return this;
    }

    public IndentingTextRenderer addResourceContent(Class<?> root, String fileName) {
        try (var resource = root.getResourceAsStream(fileName)) {
            checkNotNull(resource, "Resource not found: %s", fileName);
            return add(new String(resource.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public IndentingTextRenderer addLine(String text) {
        return add(text).addLine();
    }

    public IndentingTextRenderer addLineIf(boolean condition, String text) {
        return condition ? addLine(text) : this;
    }

    /**
     * Appends the comma separated arguments
     */
    public IndentingTextRenderer addArgs(Iterable<String> args) {
        return add(String.join(", ", args));
    }

    public IndentingTextRenderer add(String text) {
        if (text == null || text.isEmpty()) {
            return this;
        }

        text = applyVariables(text);
        final int len = text.length();

        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);
            if (c == '\n') {
                // don't leave unnecessary spaces after
                stripTrailingSpaces();
                buffer.append(c);
            } else {
                // Indent after newlines
                if (testLastChar(n -> n == '\n')) {
                    addIndentSpaces();
                }
                buffer.append(c);
            }
        }

        return this;
    }

    private void addIndentSpaces() {
        for (int level = 0; level < indentLevel; level++) {
            buffer.append(indentString);
        }
    }

    /**
     * Removes the last character if it is a {@code '\n'}.
     */
    public IndentingTextRenderer stripTrailingWhitespace() {
        while (removeLastCharIf(Character::isWhitespace)) ;
        return this;
    }

    public IndentingTextRenderer stripTrailingSpaces() {
        while (removeLastCharIf(c -> c == ' ' || c == '\t')) ;
        return this;
    }

    private boolean testLastChar(Predicate<Character> condition) {
        if (buffer.isEmpty()) return false;
        return condition.test(buffer.charAt(buffer.length() - 1));
    }

    private boolean removeLastCharIf(Predicate<Character> condition) {
        if (testLastChar(condition)) {
            buffer.setLength(buffer.length() - 1);
            return true;
        }
        return false;
    }

    public String render() {
        // do multiple iterations in case we have circular references
        String string = buffer.toString();
        while (needsReplacement(string)) {
            string = applyVariables(string);
        }
        return string;
    }

    private boolean needsReplacement(String text) {
        for (String key : variables.keySet()) {
            if (text.contains(key)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return render();
    }

    private String applyVariables(String text) {
        if (!text.contains("%%")) return text;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            if (text.contains(entry.getKey())) {
                text = text.replace(entry.getKey(), entry.getValue());
            }
        }
        return text;
    }

    /**
     * @return a new instance with the same buffer, but separate state variables
     */
    public IndentingTextRenderer snapshot() {
        return new IndentingTextRenderer(this);
    }

    private IndentingTextRenderer(IndentingTextRenderer base) {
        buffer = base.buffer;
        indentLevel = base.indentLevel;
        indentString = base.indentString;
        variables.putAll(base.variables);
    }

    public IndentingTextRenderer() {
        buffer = new StringBuilder();
    }

    public IndentingTextRenderer setIndentString(String indentString) {
        this.indentString = indentString;
        return this;
    }

    private final StringBuilder buffer;
    private final Map<String, String> variables = new LinkedHashMap<>();
    private int indentLevel = 0;

    private String indentString = "    ";

}
