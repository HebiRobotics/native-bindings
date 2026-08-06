package us.hebi.graalvm.bindings.generator.generators;

import us.hebi.graalvm.bindings.model.MethodDescription;
import us.hebi.graalvm.bindings.model.TypeDescription;

import static us.hebi.graalvm.bindings.generator.bindings.BindingGenerator.*;

/**
 * @author Florian Enner
 * @since 06 Aug 2025
 */
public class DocumentationUtil {

    public static String generateMatlabdoc(String indent, TypeDescription type) {
        return wrapInMatlabDoc(indent, type.getComment().trim()) + "\n";
    }

    public static String generateMatlabdoc(String indent, MethodDescription methodDesc) {
        StringBuilder out = new StringBuilder(methodDesc.getDocumentation().trim());

        if (methodDesc.getParameters().length() > 0) {
            out.append("\n\nInputs:");
            for (TypeDescription parameter : methodDesc.getParameters()) {
                out.append("\n").append(INDENT).append(parameter.getParamName());
                if (parameter.hasComment()) {
                    out.append(" - ").append(parameter.getComment());
                }
            }
        }

        if (hasReturnComment(methodDesc)) {
            out.append("\n\nOutputs:");
            out.append("\n").append(INDENT).append(methodDesc.getReturnType().getComment());
        }

        if (isThrowing(methodDesc)) {
            out.append("\n\nThrows:");
            out.append("\n").append(INDENT).append("Error " + methodDesc.tryGetThrowsOn().orElse(DEFAULT_THROWS_ON));
        }

        return wrapInMatlabDoc(indent, out.toString());
    }

    private static String wrapInMatlabDoc(String indent, String comment) {
        // First line uses 1 space, next lines use 3 spaces
        return indent + "% " + comment.trim().replaceAll("\n", indent + "\n%   ");
    }

    public static String generatePydoc(String indent, TypeDescription type) {
        return wrapInPydoc(indent, type.getComment().trim());
    }

    public static String generatePydoc(String indent, MethodDescription methodDesc) {
        StringBuilder out = new StringBuilder();
        out.append(methodDesc.getDocumentation().trim());

        // Parameter section
        if (methodDesc.getParameters().length() > 0) {
            out.append("\n\nArgs:");
            for (TypeDescription param : methodDesc.getParameters()) {
                String name = param.getLowerUnderscoreParamName();
                out.append("\n").append(INDENT).append(name).append(": ").append(param.getComment());
            }
        }

        if (hasReturnComment(methodDesc)) {
            out.append("\n\nReturns:");
            out.append("\n").append(INDENT).append(methodDesc.getReturnType().getComment());
        }

        if (isThrowing(methodDesc)) {
            out.append("\n\nRaises:");
            out.append("\n").append(INDENT).append("Exception: " + methodDesc.tryGetThrowsOn().orElse(DEFAULT_THROWS_ON));
        }

        return wrapInPydoc(indent, out.toString());
    }

    public static String wrapInPydoc(String indent, String comment) {
        StringBuilder out = new StringBuilder();
        out.append(indent).append(TRIPPLE_QUOTES);

        comment = comment.trim();
        if (comment.contains("\n")) {
            comment = comment.replaceAll("\n", "\n" + indent);
            out.append(comment).append("\n").append(indent);
        } else {
            out.append(comment);
        }

        out.append(TRIPPLE_QUOTES);
        return out.toString();
    }

    private static final String TRIPPLE_QUOTES = "\"\"\"";
    private static final String INDENT = "    ";

    public static String wrapInDoxygen(String indent, String comment) {
        return finishDoxygenBlock(indent, startDoxygenBlock(indent, comment.trim()));
    }

    public static String generateDoxygen(String indent, TypeDescription type) {
        return finishDoxygenBlock(indent, startDoxygenBlock(indent, type.getComment().trim()));
    }

    public static String generateDoxygen(String indent, MethodDescription methodDesc) {
        var out = startDoxygenBlock(indent, methodDesc.getDocumentation().trim());
        if (methodDesc.getParameters().length() > 0 || hasReturnComment(methodDesc)) {
            out.append("\n *");
            for (TypeDescription parameter : methodDesc.getParameters()) {
                out.append("\n * @param ").append(parameter.getParamName()).append(" ").append(parameter.getComment());
            }
            if (hasReturnComment(methodDesc)) {
                out.append("\n * @return ").append(methodDesc.getReturnType().getComment());
            }
        }
        if (isThrowing(methodDesc)) {
            out.append("\n * @throw " + methodDesc.tryGetThrowsOn().orElse(DEFAULT_THROWS_ON));
        }
        return finishDoxygenBlock(indent, out);
    }

    /**
     * A comment on the return value, today only set through a {@code @CLibComment}
     * on the {@code @CLibOut} parameter that the faces present as the return.
     */
    private static boolean hasReturnComment(MethodDescription methodDesc) {
        return methodDesc.hasReturnType() && methodDesc.getReturnType().hasComment();
    }

    /**
     * We need to escape some '\' in comments to avoid omitting them when e.g. being used in
     * a replaceAll replacement.
     *
     * @param input string
     * @return escaped string
     */
    public static String escapeBackslashes(String input) {
        if (input.length() > 0 && input.contains("\\")) {
            return input.replaceAll("\\\\", "\\\\\\\\");
        }
        return input;
    }

    private static StringBuilder startDoxygenBlock(String indent, String comment) {
        StringBuilder out = new StringBuilder();
        var isBrief = !comment.contains("\n");
        comment = comment.trim();
        out.append(indent).append("/**");

        if (isBrief) {
            out.append("\n * @brief ").append(comment);
        } else {
            out.append("\n * @details ").append("\n * ");
            out.append(comment.replaceAll("\n", "\n * "));
        }
        return out;
    }

    private static String finishDoxygenBlock(String indent, StringBuilder out) {
        out.append("\n */");
        return "\n" + out.toString().replaceAll("\n", "\n" + indent) + "\n";
    }

    private static final String DEFAULT_THROWS_ON = "on internal errors";

}
