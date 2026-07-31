package us.hebi.graalvm.bindings.generator.generators;

import javax.lang.model.element.Element;

/**
 * @author Hannes Dorfmann
 */
public class ProcessingException extends RuntimeException {

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
