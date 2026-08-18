package us.hebi.graalvm.bindings.generator.bindings.features;

import us.hebi.graalvm.bindings.model.LibraryDescription;

import java.util.Collections;
import java.util.List;

/**
 * @author Florian Enner
 * @since 17 Jun 2025
 */
public interface FeatureGenerator {

    /** The C declarations. For C++ these are the lines of the extern "C" block, indented by the caller */
    String getCHeader(boolean isCpp);

    String getCSource();

    String getObjCSource();

    String getCppWrapper(boolean useDynamicLookup);

    String getPythonWrapper();

    /** The names getPythonWrapper defines that belong in the module's __all__ */
    default List<String> getPythonExports() {
        return Collections.emptyList();
    }

    default List<String> getAppleLibraries() {
        return Collections.emptyList();
    }

    default void saveToApiDescription(LibraryDescription desc) {
    }

}
