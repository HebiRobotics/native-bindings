package us.hebi.graalvm.bindings.generator.generators;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import org.graalvm.nativeimage.c.CContext;
import us.hebi.graalvm.bindings.annotations.CLibStruct;
import us.hebi.graalvm.bindings.generator.annotations.AnnotatedCLibClass;
import us.hebi.graalvm.bindings.generator.annotations.AnnotatedCLibStruct;
import us.hebi.graalvm.bindings.generator.bindings.TypeMap;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static javax.lang.model.element.Modifier.*;
import static javax.tools.Diagnostic.Kind.*;

/**
 * Emits the per-struct artifacts a {@code @CLibStruct} view needs: the probe header that
 * native-image compiles the {@code @CStruct} layout against, the {@code CContext.Directives}
 * that points at it, and a field-backed mock for running on HotSpot.
 *
 * @author Florian Enner
 * @since 6 Aug 2026
 */
public class CLibStructGenerator extends AbstractGenerator {

    public static ClassName getDirectivesClassName(ClassName viewType) {
        return getGeneratedClassName(viewType, "$$Directives");
    }

    public static ClassName getMockClassName(ClassName viewType) {
        return getGeneratedClassName(viewType, "$$Mock");
    }

    /**
     * The whole nesting path gets joined because the generated types are always top-level:
     * taking only the innermost name lets two views write to the same file.
     */
    private static ClassName getGeneratedClassName(ClassName viewType, String suffix) {
        var packageName = viewType.packageName() + ".generated";
        var simpleName = String.join("_", viewType.simpleNames()) + suffix;
        return ClassName.get(packageName, simpleName);
    }

    /**
     * A view names its own generated directives class in {@code @CContext}, which
     * auto-common's up-front validation reports as an unresolved dependency and defers the
     * whole {@code @CLibClass} on, before any step could generate the class it waits for.
     * Scanning the round directly breaks that cycle: the artifacts exist by round two and
     * the deferred class then re-validates cleanly. Called from {@code postRound}.
     */
    public void processRound(RoundEnvironment roundEnv, ProcessingEnvironment processingEnv) {
        boolean firstRun = this.env == null;
        this.env = processingEnv;
        if (firstRun) {
            initialize(processingEnv);
        }
        for (Element element : roundEnv.getElementsAnnotatedWith(CLibStruct.class)) {
            try {
                process(new AnnotatedCLibStruct(element, processingEnv));
            } catch (ProcessingException e) {
                // The step never runs for a class deferred on the unresolved directives
                // reference, so this is the only place the real error can be named.
                getMessager().printMessage(ERROR, e.getMessage(), e.getElement());
            }
        }
    }

    /**
     * A view declared in this compilation gets its artifacts generated here. One that is only
     * ever used, never declared, comes from a jar and ships its own directives and mock.
     */
    boolean isSourceDeclared(AnnotatedCLibStruct struct) {
        return processedViews.contains(struct.getClassName().canonicalName());
    }

    @Override
    protected void process(AnnotatedCLibClass apiClass) {
        for (AnnotatedCLibStruct nestedStruct : apiClass.getNestedStructs()) {
            process(nestedStruct);
        }
    }

    private void process(AnnotatedCLibStruct struct) {
        // The round scan and the per-class step both see a struct, whichever runs first wins
        if (!processedViews.add(struct.getClassName().canonicalName())) {
            return;
        }
        var viewType = struct.getClassName();
        var directivesClass = getDirectivesClassName(viewType);
        var headerName = String.join("_", viewType.simpleNames()) + ".h";

        createResourceFile(directivesClass.packageName(), headerName, generateProbeHeader(struct, headerName));
        createSourceFile(directivesClass.packageName(), generateDirectives(directivesClass, headerName));
        createSourceFile(directivesClass.packageName(), generateMock(struct, getMockClassName(viewType)));
    }

    /**
     * Members carry the {@code @CField} names rather than the public C spelling, because this
     * is what native-image looks up, and they stay in the model's source order, which is the ABI.
     */
    private static String generateProbeHeader(AnnotatedCLibStruct struct, String headerName) {
        String guard = "BINDINGS_" + headerName.replace('.', '_').toUpperCase();

        var out = new StringBuilder();
        out.append("#ifndef ").append(guard).append("\n");
        out.append("#define ").append(guard).append("\n");
        out.append("""

                /*
                 * Struct definition for the GraalVM @CStruct layout probe. This header is only
                 * used while building the native image and is never included by users. The
                 * generated user-facing headers declare a layout-compatible struct under a
                 * library-prefixed name, which is safe because only the layout crosses the linker.
                 */

                #include <stddef.h>
                #include <stdint.h>

                typedef struct {
                """);

        for (var field : struct.getFields()) {
            out.append("    ").append(TypeMap.toStructFieldC(field.getDataType(), field.isConst())).append(" ").append(field.getName()).append(";\n");
        }

        out.append("} ").append(struct.getCStructName()).append(";\n");
        out.append("\n#endif\n");
        return out.toString();
    }

    private static TypeSpec generateDirectives(ClassName directivesClass, String headerName) {
        String resourceDir = "/" + directivesClass.packageName().replace('.', '/') + "/";

        var getHeaderFiles = MethodSpec.methodBuilder("getHeaderFiles")
                .addAnnotation(Override.class)
                .addModifiers(PUBLIC)
                .returns(ParameterizedTypeName.get(List.class, String.class))
                .addComment("Forward slashes on purpose: backslashes are escape characters in a C include")
                .addStatement("return $T.of($S + extractHeader().replace('\\\\', '/') + $S)", List.class, "\"", "\"")
                .build();

        var extractHeader = MethodSpec.methodBuilder("extractHeader")
                .addModifiers(PRIVATE, STATIC)
                .returns(String.class)
                .addStatement("String resource = $S + $N", resourceDir, HEADER_NAME_FIELD)
                .beginControlFlow("try ($T input = $T.class.getResourceAsStream(resource))", InputStream.class, directivesClass)
                .addStatement("$T.requireNonNull(input, $S + resource)", Objects.class, "Missing header resource: ")
                .addStatement("$T header = $T.createTempDirectory($S).resolve($N)",
                        Path.class, Files.class, TEMP_DIRECTORY_PREFIX, HEADER_NAME_FIELD)
                .addStatement("$T.copy(input, header, $T.REPLACE_EXISTING)", Files.class, StandardCopyOption.class)
                .addStatement("return header.toAbsolutePath().toString()")
                .nextControlFlow("catch ($T ioe)", IOException.class)
                .addStatement("throw new $T($S + $N, ioe)", UncheckedIOException.class, "Failed to extract ", HEADER_NAME_FIELD)
                .endControlFlow()
                .build();

        return TypeSpec.classBuilder(directivesClass.simpleName())
                .addJavadoc("""
                        Provides the struct definition that the GraalVM {@code @CStruct} layout probe
                        compiles against. The header ships as a classpath resource and gets extracted
                        to a temporary directory at image build time, so that consuming projects need
                        no build wiring.
                        """)
                .addModifiers(PUBLIC, FINAL)
                .addSuperinterface(CContext.Directives.class)
                .addMethod(getHeaderFiles)
                .addMethod(extractHeader)
                .addField(FieldSpec.builder(String.class, HEADER_NAME_FIELD, PRIVATE, STATIC, FINAL)
                        .initializer("$S", headerName)
                        .build())
                .build();
    }

    /**
     * Field-backed implementation of the view so that annotated classes can be exercised on
     * HotSpot, where no native struct exists.
     */
    private static TypeSpec generateMock(AnnotatedCLibStruct struct, ClassName mockClass) {
        var mock = TypeSpec.classBuilder(mockClass.simpleName())
                .addJavadoc("""
                        Field-backed {@link $T} so that annotated classes can be exercised on
                        HotSpot without building a native image.
                        """, struct.getClassName())
                .addModifiers(PUBLIC, FINAL)
                .superclass(RuntimeClasses.MockPointerBase)
                .addSuperinterface(struct.getClassName());

        var fieldTypes = new LinkedHashMap<String, TypeName>();
        for (var accessor : struct.getAccessors()) {
            if (accessor.isGetter()) {
                fieldTypes.put(accessor.getFieldName(), accessor.getJavaType());
            }
        }

        for (var accessor : struct.getAccessors()) {
            var method = MethodSpec.methodBuilder(accessor.getMethodName())
                    .addAnnotation(Override.class)
                    .addModifiers(PUBLIC);
            if (accessor.isGetter()) {
                mock.addMethod(method
                        .returns(accessor.getJavaType())
                        .addStatement("return $N", accessor.getFieldName())
                        .build());
            } else {
                mock.addMethod(method
                        .addParameter(accessor.getJavaType(), accessor.getFieldName())
                        .addStatement("this.$N = $N", accessor.getFieldName(), accessor.getFieldName())
                        .build());
            }
        }

        // Declared in the layout order the probe header uses
        for (var field : struct.getFields()) {
            mock.addField(fieldTypes.get(field.getName()), field.getName(), PRIVATE);
        }

        return mock.build();
    }

    /**
     * The header has to sit next to the directives class in the class output, because that is
     * the only place the image build can extract it from. The bindings output directory that
     * the other generators write to is not on the classpath.
     */
    private void createResourceFile(String packageName, String fileName, String content) {
        try (Writer writer = getFiler()
                .createResource(StandardLocation.CLASS_OUTPUT, packageName, fileName)
                .openWriter()) {
            writer.write(content);
        } catch (IOException e) {
            getMessager().printMessage(ERROR, e.getMessage());
        }
    }

    private final Set<String> processedViews = new HashSet<>();

    private static final String HEADER_NAME_FIELD = "HEADER_NAME";
    private static final String TEMP_DIRECTORY_PREFIX = "bindings_include";

}
