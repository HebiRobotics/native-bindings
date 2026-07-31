package us.hebi.graalvm.bindings.generator.generators;

import com.google.auto.common.BasicAnnotationProcessor;
import com.google.auto.service.AutoService;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import us.hebi.graalvm.bindings.annotations.CLibClass;
import us.hebi.graalvm.bindings.generator.annotations.AnnotatedCLibClass;
import us.hebi.graalvm.bindings.generator.annotations.EnvProcessor;
import us.hebi.graalvm.bindings.generator.bindings.CBindingsGenerator;
import us.hebi.graalvm.bindings.generator.bindings.CppBindingsGenerator;
import us.hebi.graalvm.bindings.generator.bindings.MatlabBindingsGenerator;
import us.hebi.graalvm.bindings.generator.bindings.PythonBindingsGenerator;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedOptions;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
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
import java.util.List;
import java.util.Set;

import static javax.tools.Diagnostic.Kind.*;

/**
 * @author Florian Enner < florian @ hebirobotics.com >
 * @since 04 Nov 2015
 */
@AutoService(Processor.class)
@SupportedSourceVersion(SourceVersion.RELEASE_17)
@SupportedOptions({
        EnvProcessor.OPTION_OUTPUT_DIR,
        EnvProcessor.OPTION_LIB_NAME,
        EnvProcessor.OPTION_LIB_VERSION,
        EnvProcessor.OPTION_LIB_BUILD,
        EnvProcessor.OPTION_NAMESPACE,
        EnvProcessor.OPTION_C_PREFIX,
        EnvProcessor.OPTION_ENABLE_GRAPHICS
})
public class NativeAnnotationProcessor extends BasicAnnotationProcessor {

    @Override
    protected Iterable<? extends Step> steps() {
        return List.of(new Step() {
            @Override
            public Set<String> annotations() {
                return ImmutableSet.of(CLibClass.class.getCanonicalName());
            }

            @Override
            public Set<? extends Element> process(ImmutableSetMultimap<String, Element> elementsByAnnotation) {
                for (Element element : elementsByAnnotation.get(CLibClass.class.getCanonicalName())) {
                    try {
                        processElement(castToTypeElement(element), processingEnv);
                    } catch (Throwable t) {
                        StringWriter sw = new StringWriter();
                        t.printStackTrace(new PrintWriter(sw));
                        processingEnv.getMessager().printMessage(ERROR, sw.toString());
                    }
                }
                return Collections.emptySet();
            }
        });
    }

    private static TypeElement castToTypeElement(Element element) {
        if (element instanceof TypeElement typeElement && element.getKind() == ElementKind.CLASS) {
            return typeElement;
        }
        throw new ProcessingException(element, "Input can only be class type");
    }

    private void processElement(TypeElement type, ProcessingEnvironment environment) {
        hasProcessedElements = true;
        processingEnv.getMessager().printMessage(NOTE, "Processing " + type.getSimpleName());
        if (type.getAnnotation(CLibClass.class) == null) {
            return;
        }

        // Ignore beta APIs. TODO: add a compiler flag to enable beta symbols
        var apiClass = new AnnotatedCLibClass(type, processingEnv);
        if (apiClass.shouldIgnore()) {
            return;
        }

        // Process this class
        for (AbstractGenerator generator : generators) {
            generator.process(apiClass, processingEnv);
        }

        // TODO:
        //  Allow inner classes. It gets stuck in an infinite loop without error.
        //  I haven't found the exact reason, but my best guess is poor namespace
        //  resolution

        // Process inner classes
        /*var nestedClasses = type.getEnclosedElements().stream()
                .filter(enclosed -> enclosed.getKind() == ElementKind.CLASS)
                .filter(enclosed -> enclosed.getAnnotation(CApiClass.class) != null)
                .map(GeneratorUtil::castToTypeElement)
                .map(t -> new AnnotatedNativeClass(t, processingEnv))
                .toList();

        for (AnnotatedNativeClass nested : nestedClasses) {
            for (AbstractGenerator generator : generators) {
                generator.process(nested, processingEnv);
            }
        }*/

    }

    protected void postRound(RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            if (!hasProcessedElements) return;

            for (AbstractGenerator generator : generators) {
                generator.finish();
            }

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

    private Writer createFile(String dir, String name) throws IOException {
        String propName = EnvProcessor.OPTION_OUTPUT_DIR;
        String outputDir = processingEnv.getOptions().get(propName);
        if (outputDir == null) {
            outputDir = System.getProperty(propName);
        }
        if (outputDir != null) {
            var path = Paths.get(outputDir, "bindings", dir, name).normalize();
            Files.createDirectories(path.getParent());
            return Files.newBufferedWriter(path,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
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

    final LibraryDescriptionGenerator apiDescription = new LibraryDescriptionGenerator();
    final CEnumGenerator enumGenerator = new CEnumGenerator();
    final List<AbstractGenerator> generators = Arrays.asList(
            new CEntryPointGenerator(),
            enumGenerator,
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
