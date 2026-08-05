package us.hebi.graalvm.bindings.generator.bindings;

import com.google.common.base.Joiner;
import org.stringtemplate.v4.ST;
import us.hebi.graalvm.bindings.generator.bindings.features.CocoaFeature;
import us.hebi.graalvm.bindings.generator.bindings.features.FeatureGenerator;
import us.hebi.graalvm.bindings.generator.bindings.features.LibraryInfoFeature;
import us.hebi.graalvm.bindings.generator.generators.ProtoSortUtil;
import us.hebi.graalvm.bindings.model.*;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static com.google.common.base.Preconditions.*;

/**
 * @author Florian Enner
 * @since 16 Jul 2024
 */
public abstract class BindingGenerator {

    protected BindingGenerator(LibraryDescription lib) {
        // Make sure classes are sorted properly
        ProtoSortUtil.sortClassDescriptions(lib.getClasses());
        this.lib = lib;
        this.features = List.of(
                new LibraryInfoFeature(lib),
                new CocoaFeature(lib)
        );
    }

    protected ST loadTemplateWithLibVars(String template) {
        return loadTemplate(template)
                .add("projectName", lib.getName().toUpperCase(Locale.ENGLISH))
                .add("libName", lib.getName())
                .add("libVersion", lib.getVersion())
                .add("buildNumber", lib.getBuild())
                .add("downloadUrl", lib.tryGetDownloadUrl().orElse(null));
    }

    protected String loadContent(String fileName) {
        try (var resource = getClass().getResourceAsStream(fileName)) {
            checkNotNull(resource, "Resource not found: %s", fileName);
            return new String(resource.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected ST loadTemplate(String template) {
        return new ST(loadContent(template));
    }

    protected Stream<ClassDescription> getApiClassesAsStream() {
        return StreamSupport.stream(lib.getClasses().spliterator(), false);
    }

    protected List<ClassDescription> getApiClasses() {
        return getApiClassesAsStream().toList();
    }

    protected void forEachType(Consumer<TypeDescription> consumer) {
        for (ClassDescription apiClass : lib.getClasses()) {
            consumer.accept(apiClass.getType());
            for (MethodDescription method : apiClass.getMethods()) {
                consumer.accept(method.getReturnType());
                for (TypeDescription parameter : method.getParameters()) {
                    consumer.accept(parameter);
                }
            }
        }
    }

    public static boolean isThrowing(MethodDescription method) {
        return !hasFlag(method, MethodFlag.Noexcept);
    }

    public static boolean hasFlag(MethodDescription method, MethodFlag flag) {
        for (MethodFlag current : method.getFlags()) {
            if (current == flag) return true;
        }
        return false;
    }

    @FunctionalInterface
    public interface Filer {
        Writer createWriter(String directory, String fileName) throws IOException;

        default void createFile(String directory, String fileName, ContentSource source) throws IOException {
            try (var writer = createWriter(directory, fileName)) {
                source.writeTo(writer);
            }
        }

    }

    @FunctionalInterface
    public interface ContentSource {
        public void writeTo(Writer writer) throws IOException;
    }

    public abstract void writeTo(Filer filer) throws IOException;

    protected final LibraryDescription lib;
    protected final List<FeatureGenerator> features;

    protected boolean hasStaticMethods(ClassDescription desc) {
        for (MethodDescription method : desc.getMethods()) {
            if (method.getIsStatic()) {
                return true;
            }
        }
        return false;
    }

    protected boolean hasInstanceMethods(ClassDescription desc) {
        for (MethodDescription method : desc.getMethods()) {
            if (!method.getIsStatic()) {
                return true;
            }
        }
        return false;
    }

    public List<MethodDescription> getMethods(ClassDescription description, Predicate<MethodDescription> filter) {
        var list = new ArrayList<MethodDescription>(description.getMethods().length());
        for (var method : description.getMethods()) {
            if (filter == null || filter.test(method)) {
                list.add(method);
            }
        }
        return list;
    }

    static final Joiner joiner = Joiner.on(", ");

}
