package us.hebi.graalvm.bindings.generator.generators;

/**
 * Lightweight named-placeholder string template using %%KEY%% syntax.
 *
 * @author Florian Enner
 * @since 10 Mar 2026
 */
public class TemplateString {

    private String template;

    private TemplateString(String template) {
        this.template = template;
    }

    public static TemplateString of(String template) {
        return new TemplateString(template);
    }

    public TemplateString set(String key, String value) {
        // uses String.replace (literal, not regex) for safety and performance
        template = template.replace("%%" + key + "%%", value);
        return this;
    }

    public TemplateString set(String key, int value) {
        return set(key, Integer.toString(value));
    }

    public String render() {
        return template;
    }

    @Override
    public String toString() {
        return template;
    }
}
