package us.hebi.graalvm.bindings.generator.generators;

import com.google.auto.common.BasicAnnotationProcessor;
import com.google.auto.service.AutoService;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import us.hebi.graalvm.bindings.annotations.CLibCallback;
import us.hebi.graalvm.bindings.annotations.CLibClass;
import us.hebi.graalvm.bindings.generator.annotations.AnnotatedCLibCallback;
import us.hebi.graalvm.bindings.generator.annotations.AnnotatedCLibClass;
import us.hebi.graalvm.bindings.generator.annotations.AnnotationUtil;
import us.hebi.graalvm.bindings.generator.annotations.EnvProcessor;
import us.hebi.graalvm.bindings.generator.bindings.CBindingsGenerator;
import us.hebi.graalvm.bindings.generator.bindings.CppBindingsGenerator;
import us.hebi.graalvm.bindings.generator.bindings.MatlabBindingsGenerator;
import us.hebi.graalvm.bindings.generator.bindings.PythonBindingsGenerator;
import us.hebi.graalvm.bindings.generator.bindings.marshallers.StructMarshallers;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static javax.tools.Diagnostic.Kind.*;

/**
 * @author Florian Enner < florian @ hebirobotics.com >
 * @since 04 Nov 2015
 */
@AutoService(Processor.class)
@SupportedOptions({
        EnvProcessor.OPTION_OUTPUT_DIR,
        EnvProcessor.OPTION_LIB_NAME,
        EnvProcessor.OPTION_LIB_VERSION,
        EnvProcessor.OPTION_LIB_BUILD,
        EnvProcessor.OPTION_DOWNLOAD_URL,
        EnvProcessor.OPTION_NAMESPACE,
        EnvProcessor.OPTION_C_PREFIX,
        EnvProcessor.OPTION_ENABLE_GRAPHICS
})
public class NativeAnnotationProcessor extends BasicAnnotationProcessor {

    @Override
    public SourceVersion getSupportedSourceVersion() {
        // no version-specific behavior, so support whatever the running compiler does
        return SourceVersion.latestSupported();
    }

    @Override
    protected Iterable<? extends Step> steps() {
        // Runs once per processor instance, i.e. once per compilation, so a shared JVM
        // (IDE daemon, compile-testing) never sees a previous run's marshallers
        StructMarshallers.clearRegistrations();
        checkLibraryCPrefix();
        return List.of(new Step() {
            @Override
            public Set<String> annotations() {
                return ImmutableSet.of(CLibClass.class.getCanonicalName(), CLibCallback.class.getCanonicalName());
            }

            @Override
            public Set<? extends Element> process(ImmutableSetMultimap<String, Element> elementsByAnnotation) {
                for (Element element : elementsByAnnotation.get(CLibCallback.class.getCanonicalName())) {
                    reportErrors(() -> checkIsCallbackType(element));
                }
                for (Element element : elementsByAnnotation.get(CLibClass.class.getCanonicalName())) {
                    reportErrors(() -> processElement(castToTypeElement(element), processingEnv));
                }
                return Collections.emptySet();
            }
        });
    }

    /**
     * The library prefix leads every non-namespaced C name, and it defaults to the namespace
     * with the dots replaced. There is no annotated element to point at, so this is reported
     * the way a missing option is.
     */
    private void checkLibraryCPrefix() {
        String cPrefix = new EnvProcessor.GeneratorOptions(processingEnv).getCPrefix();
        if (!AnnotationUtil.isCIdentifier(cPrefix)) {
            processingEnv.getMessager().printMessage(ERROR, String.format(
                    "-A%s: '%s' is not a valid C identifier",
                    EnvProcessor.OPTION_C_PREFIX, cPrefix));
        }
    }

    private void reportErrors(Runnable task) {
        try {
            task.run();
        } catch (Throwable t) {
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            processingEnv.getMessager().printMessage(ERROR, sw.toString());
        }
    }

    /**
     * The annotation only carries metadata for a callback that discovery finds on its own,
     * so putting it anywhere else silently does nothing.
     */
    private static void checkIsCallbackType(Element element) {
        if (!(element instanceof TypeElement type) || !AnnotatedCLibCallback.isCallbackType(type)) {
            throw new ProcessingException(element, "@CLibCallback requires an interface extending CFunctionPointer: %s", element);
        }
    }

    private static TypeElement castToTypeElement(Element element) {
        if (element instanceof TypeElement typeElement && element.getKind() == ElementKind.CLASS) {
            if (!isTopLevelOrStaticNested(typeElement)) {
                throw new ProcessingException(element, "@CLibClass requires a top-level or static nested class: %s",
                        typeElement.getQualifiedName());
            }
            return typeElement;
        }
        throw new ProcessingException(element, "Input can only be class type");
    }

    private static boolean isTopLevelOrStaticNested(TypeElement type) {
        return switch (type.getNestingKind()) {
            case TOP_LEVEL -> true;
            case MEMBER -> type.getModifiers().contains(Modifier.STATIC);
            case LOCAL, ANONYMOUS -> false;
        };
    }

    private void processElement(TypeElement type, ProcessingEnvironment environment) {
        processingEnv.getMessager().printMessage(NOTE, "Processing " + type.getSimpleName());
        if (type.getAnnotation(CLibClass.class) == null) {
            return;
        }

        // Ignore beta APIs. TODO: add a compiler flag to enable beta symbols
        var apiClass = new AnnotatedCLibClass(type, processingEnv);
        if (apiClass.shouldIgnore()) {
            return;
        }

        checkNamesAreUnique(apiClass);

        // Process this class
        for (AbstractGenerator generator : generators) {
            generator.process(apiClass, processingEnv);
        }

        // Set the flag after processing so that the postRound skips finish() and avoids
        // masking the error with a second exception.
        hasProcessedElements = true;
    }

    protected void postRound(RoundEnvironment roundEnv) {
        // Struct artifacts are generated from the raw round rather than from the step: a view
        // naming its own generated directives class in @CContext keeps the whole @CLibClass
        // deferred by auto-common's validation until the class exists. See CLibStructGenerator.
        structGenerator.processRound(roundEnv, processingEnv);

        if (roundEnv.processingOver()) {
            if (!hasProcessedElements) return;

            for (AbstractGenerator generator : generators) {
                generator.finish();
            }

            // A struct error leaves a dangling reference in the description, and the language
            // generators would crash on it and bury the reported diagnostic.
            if (apiDescription.hasReportedErrors()) return;

            try {
                apiDescription.writeTo(this::createFile);
                new CBindingsGenerator(apiDescription.getDescription()).writeTo(this::createFile);
                new PythonBindingsGenerator(apiDescription.getDescription()).writeTo(this::createFile);
                new CppBindingsGenerator(apiDescription.getDescription()).writeTo(this::createFile);
                new MatlabBindingsGenerator(apiDescription.getDescription()).writeTo(this::createFile);
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }

        }
    }

    /**
     * The annotation value names the type in every target language, so two declarations
     * sharing one produce duplicate C symbols, wrapper files that overwrite each other and
     * an unstable sort in the descriptor. Unlike the generated Java names, these are the
     * author's own and no mangling can keep them apart, so the only fix is to reject them.
     * Without this check the first symptom is a swallowed FilerException from
     * {@link AbstractGenerator}, reported as a bare "Attempt to recreate a file". Names taken
     * by a different kind are rejected in {@code LibraryDescriptionGenerator}, which is where
     * all four kinds are known.
     */
    private void checkNamesAreUnique(AnnotatedCLibClass apiClass) {
        checkNameIsUnique(classNames, "@CLibClass", apiClass.getName(), apiClass.getElement());
        for (var nestedEnum : apiClass.getNestedEnums()) {
            checkNameIsUnique(enumNames, "@CLibEnum", nestedEnum.getName(), nestedEnum.getElement());
        }
        for (var nestedStruct : apiClass.getNestedStructs()) {
            checkNameIsUnique(structNames, "@CLibStruct", nestedStruct.getName(), nestedStruct.getElement());
        }
    }

    private static void checkNameIsUnique(Map<String, String> takenNames, String annotation, String value, TypeElement element) {
        String declaredBy = element.getQualifiedName().toString();
        String previous = takenNames.putIfAbsent(value, declaredBy);
        if (previous != null && !previous.equals(declaredBy)) {
            throw new ProcessingException(element, "Duplicate %s value '%s', already declared by %s",
                    annotation, value, previous);
        }
    }

    private Writer createFile(String dir, String name) throws IOException {
        String propName = EnvProcessor.OPTION_OUTPUT_DIR;
        String outputDir = processingEnv.getOptions().get(propName);
        if (outputDir == null) {
            outputDir = System.getProperty(propName);
        }
        if (outputDir != null) {
            var path = Paths.get(outputDir, "bindings", dir, name).normalize();
            Files.createDirectories(path.getParent());
            var writer = Files.newBufferedWriter(path,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
            // native-image executes the generated compiler wrapper directly, and the default
            // 0644 would fail with 'Permission denied'. No-op on Windows.
            if (name.endsWith(".sh")) {
                path.toFile().setExecutable(true, false);
            }
            return writer;
        } else {
            processingEnv.getMessager().printMessage(ERROR, "Missing compiler option: -A" + propName);
        }
        return processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT, getBindingsDir(dir), name).openWriter();
    }

    public static String getBindingsDir(String dir) {
        if (Strings.isNullOrEmpty(dir))
            return "bindings";
        return "bindings." + dir;
    }

    private final Map<String, String> classNames = new HashMap<>();
    private final Map<String, String> enumNames = new HashMap<>();
    private final Map<String, String> structNames = new HashMap<>();

    final CEnumGenerator enumGenerator = new CEnumGenerator();
    final CLibStructGenerator structGenerator = new CLibStructGenerator();
    final LibraryDescriptionGenerator apiDescription = new LibraryDescriptionGenerator(structGenerator);
    final List<AbstractGenerator> generators = Arrays.asList(
            new CEntryPointGenerator(),
            enumGenerator,
            structGenerator,
            apiDescription
    );

    private ProcessingEnvironment getEnv() {
        if (processingEnv == null) {
            throw new IllegalStateException("Processing environment not initialized yet");
        }
        return processingEnv;
    }

    boolean hasProcessedElements = false;

}
