package us.hebi.graalvm.bindings.generator.generators;

import us.hebi.graalvm.bindings.model.DataType;
import us.hebi.graalvm.bindings.model.MethodDescription;
import us.hebi.graalvm.bindings.model.TypeDescription;

import static us.hebi.graalvm.bindings.generator.bindings.BindingGenerator.*;

/**
 * @author Florian Enner
 * @since 06 Aug 2025
 */
public class DocumentationUtil {

    public static String generateMatlabdoc(TypeDescription type) {
        return wrapInMatlabDoc(type.getComment().trim()) + "\n";
    }

    public static String generateMatlabdoc(MethodDescription methodDesc) {
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

        return wrapInMatlabDoc(out.toString());
    }

    public static String wrapInMatlabDoc(String comment) {
        // First line uses 1 space, next lines use 3 spaces
        return "% " + comment.trim().replaceAll("\n", "\n%   ");
    }

    public static String generatePydoc(TypeDescription type) {
        return wrapInPydoc(type.getComment().trim());
    }

    public static String generatePydoc(MethodDescription methodDesc) {
        StringBuilder out = new StringBuilder();
        out.append(methodDesc.getDocumentation().trim());

        // Parameter section
        if (methodDesc.getParameters().length() > 0) {
            out.append("\n\nArgs:");
            for (TypeDescription param : methodDesc.getParameters()) {
                String name = param.getLowerUnderscoreParamName();
                out.append("\n").append(INDENT).append(name).append(": ").append(param.getComment());
                if (param.getDataType() == DataType.CALLBACK) {
                    if (param.hasComment()) out.append(" ");
                    out.append("Any callable is accepted, and a plain one is wrapped into a fresh");
                    out.append("\n").append(INDENT).append(INDENT);
                    out.append(param.getTypeName()).append(" per call. Build the prototype once for a hot loop.");
                }
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

        return wrapInPydoc(out.toString());
    }

    /**
     * A one-line docstring, or a multi-line one with the closing quotes on their own line
     */
    public static String wrapInPydoc(String comment) {
        comment = comment.trim();
        return comment.contains("\n")
                ? TRIPPLE_QUOTES + comment + "\n" + TRIPPLE_QUOTES
                : TRIPPLE_QUOTES + comment + TRIPPLE_QUOTES;
    }

    private static final String TRIPPLE_QUOTES = "\"\"\"";
    private static final String INDENT = "    ";

    /**
     * Plain line comments, wrapped the way the comment was authored. Used where a definition
     * carries documentation but no doxygen block, i.e. the C and C++ struct typedefs.
     */
    public static String generateLineComments(String comment) {
        var out = new StringBuilder();
        for (String line : comment.trim().split("\n")) {
            line = line.stripTrailing();
            out.append(line.isEmpty() ? "//" : "// " + line).append("\n");
        }
        return out.toString();
    }

    public static String wrapInDoxygen(String comment) {
        return finishDoxygenBlock(startDoxygenBlock(comment.trim()));
    }

    public static String generateDoxygen(TypeDescription type) {
        return finishDoxygenBlock(startDoxygenBlock(type.getComment().trim()));
    }

    public static String generateDoxygen(MethodDescription methodDesc) {
        var out = startDoxygenBlock(methodDesc.getDocumentation().trim());
        if (methodDesc.getParameters().length() > 0 || hasReturnComment(methodDesc)) {
            out.append("\n *");
            for (TypeDescription parameter : methodDesc.getParameters()) {
                out.append("\n * @param ").append(parameter.getParamName());
                if (parameter.hasComment()) {
                    out.append(" ").append(parameter.getComment());
                }
            }
            if (hasReturnComment(methodDesc)) {
                out.append("\n * @return ").append(methodDesc.getReturnType().getComment());
            }
        }
        if (isThrowing(methodDesc)) {
            out.append("\n * @throw " + methodDesc.tryGetThrowsOn().orElse(DEFAULT_THROWS_ON));
        }
        return finishDoxygenBlock(out);
    }

    /**
     * A comment on the return value, today only set through a {@code @CLibComment}
     * on the {@code @CLibOut} parameter that the faces present as the return.
     */
    private static boolean hasReturnComment(MethodDescription methodDesc) {
        return methodDesc.hasReturnType() && methodDesc.getReturnType().hasComment();
    }

    /**
     * Doubles backslashes so a comment survives inside a Python string literal
     */
    public static String escapeBackslashes(String input) {
        if (input.length() > 0 && input.contains("\\")) {
            return input.replaceAll("\\\\", "\\\\\\\\");
        }
        return input;
    }

    private static StringBuilder startDoxygenBlock(String comment) {
        StringBuilder out = new StringBuilder();
        var isBrief = !comment.contains("\n");
        comment = comment.trim();
        out.append("/**");

        if (isBrief) {
            out.append("\n * @brief ").append(comment);
        } else {
            out.append("\n * @details");
            for (String line : comment.split("\n")) {
                out.append(line.isEmpty() ? "\n *" : "\n * " + line);
            }
        }
        return out;
    }

    private static String finishDoxygenBlock(StringBuilder out) {
        return out.append("\n */\n").toString();
    }

    private static final String DEFAULT_THROWS_ON = "on internal errors";

}
