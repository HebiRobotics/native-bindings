package us.hebi.graalvm.bindings.generator.generators;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * @author Florian Enner
 * @since 05 Aug 2025
 */
public class TokenReplacer implements Function<String, String> {

    public String apply(String input) {
        for (Map.Entry<String, String> token : tokens) {
            input = input.replaceAll(token.getKey(), token.getValue());
        }
        return input;
    }

    public TokenReplacer add(String token, String replacement) {
        tokens.add(Map.entry(token, replacement));
        return this;
    }

    private final List<Map.Entry<String, String>> tokens = new ArrayList<>();

}
