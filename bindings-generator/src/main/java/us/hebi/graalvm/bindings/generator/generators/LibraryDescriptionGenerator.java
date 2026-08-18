package us.hebi.graalvm.bindings.generator.generators;

import com.google.common.base.CaseFormat;
import org.graalvm.nativeimage.c.CContext;
import us.hebi.graalvm.bindings.generator.annotations.*;
import us.hebi.graalvm.bindings.generator.bindings.BindingGenerator;
import us.hebi.graalvm.bindings.generator.bindings.features.CocoaFeature;
import us.hebi.graalvm.bindings.generator.bindings.features.LibraryInfoFeature;
import us.hebi.graalvm.bindings.model.*;
import us.hebi.quickbuf.JsonSink;

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static javax.tools.Diagnostic.Kind.*;
import static us.hebi.graalvm.bindings.generator.generators.ProcessingException.*;
import static us.hebi.quickbuf.ProtoUtil.*;

/**
 * @author Florian Enner
 * @since 21 Feb 2024
 */
public class LibraryDescriptionGenerator extends AbstractGenerator {

    LibraryDescriptionGenerator(CLibStructGenerator structGenerator) {
        this.structGenerator = structGenerator;
    }

    @Override
    protected void process(AnnotatedCLibClass apiClass) {
        registerName("class", apiClass.getName(), apiClass.getElement());

        var classDesc = description.getMutableClasses().next()
                .setCDestructorName(apiClass.getDestructorCEntryName())
                .setInstantiable(apiClass.enableInstancing())
                .setType(apiClass.getNativeType().toDescription());

        apiClass.getIsolateName().ifPresent(classDesc::setIsolateName);

        apiClass.getParent().map(AnnotatedClibType::toDescription).ifPresent(parent -> {
            classDesc.setParent(parent);
            classDesc.clearCDestructorName();
        });
        apiClass.getToParentCast().ifPresent(classDesc::setToParentCastMethod);

        for (AnnotatedCLibMethod method : apiClass.getPublicApiMethods()) {
            var methodDesc = classDesc.getMutableMethods().next()
                    .setName(method.getMethodName())
                    .setLowerUnderscoreName(CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, method.getMethodName()))
                    .setCName(method.getCEntryPointName())
                    .setReturnType(method.getReturnTypeDescription());

            collectStructView(method.getReturnType());

            // Only set to non-standard values
            if (method.isStatic()) methodDesc.setIsStatic(true);
            if (method.isConstructor()) methodDesc.setIsConstructor(true);

            method.getPropertyName().ifPresent(methodDesc::setPropertyName);

            for (MethodFlag flag : method.getFlags()) {
                methodDesc.addFlags(flag);
            }

            method.getDocumentation().ifPresent(methodDesc::setDocumentation);
            method.getThrowsOnComment().ifPresent(methodDesc::setThrowsOn);

            // TODO: move verification to a better place
            if (method.isConstructor()) {
                check(method.isStatic(), method.getElement(), "Constructors must be static");
                check(classDesc.getInstantiable(), method.getElement(), "Classes with constructors must have instancing enabled");
                check(Objects.equals(method.getReturnType().getCEntryType(), apiClass.getNativeType().getCEntryType()), method.getElement(), "Constructors must return their containing class type");
            }

            // Custom method overloads
            for (var overload : method.getCppOverloads()) {
                methodDesc.addCppOverloads(overload);
            }
            method.getPythonOverload().ifPresent(methodDesc::setPythonOverload);
            method.getMatlabOverload().ifPresent(methodDesc::setMatlabOverload);
            method.getCppBody().ifPresent(methodDesc::setCppBody);

            for (AnnotatedClibType parameter : method.getParameters()) {
                // Isolates are handled by the C wrapper layer
                if (parameter.isIsolateOrThread()) continue;

                collectStructView(parameter);
                collectCallback(parameter);

                // The out parameter is already recorded as the return type
                if (parameter.isOutParameter()) continue;

                // Only add non-isolates
                methodDesc.getMutableParameters().next()
                        .copyFrom(parameter.toDescription())
                        .setParamName(parameter.getName())
                        .setLowerUnderscoreParamName(CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, parameter.getName()));
            }

        }

        for (AnnotatedCLibEnum nestedEnum : apiClass.getNestedEnums()) {
            registerName("enum", nestedEnum.getName(), nestedEnum.getElement());
            description.addEnums(nestedEnum.toDescription());
        }

        for (AnnotatedCLibStruct nestedStruct : apiClass.getNestedStructs()) {
            registerStruct(nestedStruct);
        }

    }

    /**
     * Discovery by use: a view participates because a signature mentions it, no matter whether
     * it is declared here or comes from a dependency jar. Keyed by qualified name so that
     * {@link #finish()} sees each view once.
     */
    private void collectStructView(AnnotatedClibType type) {
        if (type.getDataType() != DataType.STRUCT) {
            return;
        }
        var element = type.getTypeElement();
        usedStructViews.computeIfAbsent(element.getQualifiedName().toString(),
                name -> new AnnotatedCLibStruct(element, getEnv()));
    }

    /**
     * Discovery by use, like {@link #collectStructView(AnnotatedClibType)}
     */
    private void collectCallback(AnnotatedClibType type) {
        if (type.getDataType() != DataType.CALLBACK) {
            return;
        }
        var element = type.getTypeElement();
        usedCallbacks.computeIfAbsent(element.getQualifiedName().toString(),
                name -> new AnnotatedCLibCallback(element, getEnv()));
    }

    /**
     * Two interfaces of the same simple name would emit the same typedef twice, so the collision
     * is reported here rather than by the C compiler
     */
    private void registerUsedCallbacks() {
        for (var callback : usedCallbacks.values()) {
            registerName("callback", callback.getName(), callback.getElement());
            description.addCallbacks(callback.toDescription());
        }
    }

    /**
     * The four kinds share one namespace: the Python module and the MATLAB package are flat, so
     * two kinds with the same plain name overwrite each other there even when the C prefixes
     * keep them apart. Two declarations of the same kind are rejected earlier.
     */
    private void registerName(String kind, String name, TypeElement element) {
        var qualifiedName = element.getQualifiedName().toString();
        var previous = declaredNames.putIfAbsent(name, new DeclaredName(kind, qualifiedName));
        if (previous != null) {
            error(element, "%s name '%s' collides with %s %s: %s",
                    capitalized(kind), name, previous.kind(), name, qualifiedName);
        }
    }

    private static String capitalized(String kind) {
        return Character.toUpperCase(kind.charAt(0)) + kind.substring(1);
    }

    /**
     * The C header declares every struct the descriptor lists, so a view reaches it here or
     * not at all. A view used by two classes arrives twice and is registered once.
     */
    private void registerStruct(AnnotatedCLibStruct struct) {
        var declaredBy = struct.getElement().getQualifiedName().toString();
        var previous = structNames.putIfAbsent(struct.getName(), declaredBy);
        if (previous == null) {
            registerName("struct", struct.getName(), struct.getElement());
            description.addStructs(struct.toDescription());
        } else if (!previous.equals(declaredBy)) {
            error(struct.getElement(), "Duplicate @CLibStruct value '%s', already declared by %s", struct.getName(), previous);
        }
    }

    /**
     * Runs once every round is over, which is what makes the set of source-declared views
     * complete. A classpath view cannot name a directives class the consumer's build has yet
     * to generate, so its jar ships the directives and the mock alongside it.
     */
    private void registerUsedStructViews() {
        for (var struct : usedStructViews.values()) {
            // Presence is the whole check, the Class value is never read
            if (!structGenerator.isSourceDeclared(struct) && struct.getElement().getAnnotation(CContext.class) == null) {
                error(struct.getElement(), "A precompiled @CLibStruct view must ship its own directives and name them in @CContext: %s",
                        struct.getElement().getQualifiedName());
                continue;
            }
            registerStruct(struct);
        }
    }

    /**
     * postRound does not catch, so a thrown error would escape the processor and bury the
     * message in a javac stack trace.
     */
    private void error(Element element, String message, Object... args) {
        getMessager().printMessage(ERROR, String.format(message, args), element);
        errorReported = true;
    }

    /**
     * A rejected view never made it into the description, so the language generators would
     * crash on the dangling reference and bury the reported diagnostic.
     */
    boolean hasReportedErrors() {
        return errorReported;
    }

    public void finish() {
        var options = getOptions();
        List.of(
                new CocoaFeature(options),
                new LibraryInfoFeature(options)
        ).forEach(feature -> feature.saveToApiDescription(description));

        // Set global properties
        description.setName(options.getLibName());
        description.setVersion(options.getLibVersion());
        description.setBuild(options.getLibBuild());
        options.getDownloadUrl().ifPresent(description::setDownloadUrl);
        for (var namespace : options.getNamespace().split("\\.")) {
            description.getMutableNamespace().next().copyFrom(namespace);
        }
        if (options.enableGraphics()) description.setEnableGraphics(true);
        description.setCPrefix(options.getCPrefix());

        registerUsedStructViews();
        registerUsedCallbacks();

        // Sort classes by name so the output-diffs are predictable
        ProtoSortUtil.sortClassDescriptions(description.getClasses());
        ProtoSortUtil.sortInPlace(description.getEnums(), Comparator.comparing(EnumDescription::getName));
        ProtoSortUtil.sortInPlace(description.getStructs(), Comparator.comparing(StructDescription::getName));
        ProtoSortUtil.sortInPlace(description.getCallbacks(), Comparator.comparing(CallbackDescription::getName));

        // Fill in children information
        List<TypeDescription> children = new ArrayList<>();
        for (ClassDescription parent : description.getClasses()) {
            children.clear();
            for (ClassDescription child : description.getClasses()) {
                if (child.getParent().equals(parent.getType())) {
                    children.add(child.getType());
                }
            }
            children.forEach(parent::addChildren);
        }
    }

    public void writeTo(BindingGenerator.Filer filer) throws IOException {
        var content = JsonSink.newPrettyInstance()
                .writeMessage(description)
                .toString();
        filer.createFile("..", "api.json", writer -> writer.write(content));
    }

    public LibraryDescription getDescription() {
        return description;
    }

    private record DeclaredName(String kind, String qualifiedName) {
    }

    final LibraryDescription description = LibraryDescription.newInstance();

    private final CLibStructGenerator structGenerator;

    private final Map<String, AnnotatedCLibStruct> usedStructViews = new LinkedHashMap<>();
    private final Map<String, AnnotatedCLibCallback> usedCallbacks = new LinkedHashMap<>();
    private final Map<String, String> structNames = new HashMap<>();
    private final Map<String, DeclaredName> declaredNames = new HashMap<>();
    private boolean errorReported = false;

}
