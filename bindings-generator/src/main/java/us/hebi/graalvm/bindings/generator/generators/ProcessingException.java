package us.hebi.graalvm.bindings.generator.generators;

import javax.lang.model.element.Element;

/**
 * @author Hannes Dorfmann
 */
public class ProcessingException extends RuntimeException {

    /**
     * Guava-shaped precondition whose failure reports at the offending element
     */
    public static void check(boolean condition, Element element, String message, Object... args) {
        if (!condition) {
            throw new ProcessingException(element, message, args);
        }
    }

    public static ProcessingException processException(Element element, String msg, Object... args) {
        return new ProcessingException(element, msg, args);
    }

    Element element;

    public ProcessingException(Element element, String msg, Object... args) {
        super(String.format(msg, args));
        this.element = element;
    }

    public Element getElement() {
        return element;
    }

}
