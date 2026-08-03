package us.hebi.graalvm.bindings.generator.generators;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;
import us.hebi.graalvm.bindings.generator.annotations.AnnotatedCLibClass;
import us.hebi.graalvm.bindings.generator.annotations.EnvProcessor;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import java.io.IOException;
import java.io.Writer;

import static javax.tools.Diagnostic.Kind.*;

/**
 * @author Florian Enner
 * @since 07 Jan 2024
 */
public abstract class AbstractGenerator implements EnvProcessor {

    public final void process(AnnotatedCLibClass apiClass, ProcessingEnvironment processingEnv) {
        boolean firstRun = this.env == null;
        this.env = processingEnv; // TODO: could the environment ever change?
        if (firstRun) {
            initialize(processingEnv);
        }
        process(apiClass);
    }

    protected void initialize(ProcessingEnvironment env) {
        // first time initialization
    }

    protected abstract void process(AnnotatedCLibClass apiClass);

    public void finish() {
        // write header files
    }

    protected boolean hasProcessedAny() {
        return env != null;
    }

    public Filer getFiler() {
        return getNonNullEnvironment().getFiler();
    }

    public Messager getMessager() {
        return getNonNullEnvironment().getMessager();
    }

    protected void createSourceFile(String packageName, TypeSpec spec) {
        createSourceFile(JavaFile.builder(packageName, spec)
                .skipJavaLangImports(true)
                .indent("    ")
                .build());
    }

    protected void createSourceFile(JavaFile javaFile) {
        try {

            String name = javaFile.typeSpec.name;
            String namespace = javaFile.packageName;

            // Write to source file
            Writer writer = getFiler()
                    .createSourceFile(namespace + "." + name)
                    .openWriter();
            javaFile.writeTo(writer);
            writer.close();

        } catch (IOException e) {
            getMessager().printMessage(ERROR, e.getMessage());
        }
    }

    @Override
    public ProcessingEnvironment getEnv() {
        return env;
    }

    protected ProcessingEnvironment env;

}
