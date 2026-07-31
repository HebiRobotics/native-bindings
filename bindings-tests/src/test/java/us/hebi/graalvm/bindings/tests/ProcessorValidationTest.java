package us.hebi.graalvm.bindings.tests;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import us.hebi.graalvm.bindings.generator.generators.NativeAnnotationProcessor;

import javax.tools.JavaFileObject;

import static com.google.testing.compile.CompilationSubject.*;

/**
 * Drives the annotation processor directly over small snippets to check that misuse
 * of the annotations is rejected at compile time rather than producing broken bindings.
 * <p>
 * These are the cases that are impossible to cover from {@code src/main/java}: the API
 * there has to compile, so it can only ever demonstrate the happy path.
 *
 * @author Florian Enner
 */
public class ProcessorValidationTest {

    @Rule
    public final TemporaryFolder outputDir = new TemporaryFolder();

    @Test
    public void acceptsAMinimalClass() {
        assertThat(compile(source("Ok", """
                @CLibClass("Ok")
                public class Ok {
                    @CLibMethod(flags = Noexcept)
                    public static int value() { return 1; }
                }
                """))).succeeded();
    }

    @Test
    public void rejectsConstOnStaticMethod() {
        assertThat(compile(source("ConstStatic", """
                @CLibClass("ConstStatic")
                public class ConstStatic {
                    @CLibMethod(flags = Const)
                    public static int value() { return 1; }
                }
                """))).hadErrorContaining("only instance methods may be marked const");
    }

    @Test
    public void rejectsConstWhenInstancingIsDisabled() {
        assertThat(compile(source("NoInstancing", """
                @CLibClass(value = "NoInstancing", enableInstancing = false)
                public class NoInstancing {
                    @CLibMethod(flags = Const)
                    public int value() { return 1; }
                }
                """))).hadErrorContaining("only instance methods may be marked const");
    }

    @Test
    public void rejectsNoexceptOnThrowingMethod() {
        assertThat(compile(source("Throwing", """
                @CLibClass("Throwing")
                public class Throwing {
                    @CLibMethod(flags = Noexcept)
                    public static int value() throws Exception { return 1; }
                }
                """))).hadErrorContaining("throwing methods may not be marked noexcept");
    }

    @Test
    public void rejectsNonStaticLastErrorProvider() {
        assertThat(compile(source("InstanceProvider", """
                @CLibClass("InstanceProvider")
                public class InstanceProvider {
                    @CLibLastErrorProvider
                    @CLibMethod(flags = Noexcept)
                    public String getLastError() { return ""; }
                }
                """))).hadErrorContaining("Error provider must be a static no-args method");
    }

    @Test
    public void rejectsLastErrorProviderWithArguments() {
        assertThat(compile(source("ArgProvider", """
                @CLibClass("ArgProvider")
                public class ArgProvider {
                    @CLibLastErrorProvider
                    @CLibMethod(flags = Noexcept)
                    public static String getLastError(boolean withStackTrace) { return ""; }
                }
                """))).hadErrorContaining("Error provider must be a static no-args method");
    }

    @Test
    public void rejectsLastErrorProviderWithWrongReturnType() {
        assertThat(compile(source("IntProvider", """
                @CLibClass("IntProvider")
                public class IntProvider {
                    @CLibLastErrorProvider
                    @CLibMethod(flags = Noexcept)
                    public static int getLastError() { return 0; }
                }
                """))).hadErrorContaining("Error provider must be a static no-args method");
    }

    @Test
    public void rejectsDoubleSpanReturn() {
        assertThat(compile(source("SpanReturn", """
                import us.hebi.graalvm.bindings.runtime.types.DoubleSpan;
                @CLibClass("SpanReturn")
                public class SpanReturn {
                    @CLibMethod(flags = Noexcept)
                    public static DoubleSpan values() { return null; }
                }
                """))).hadErrorContaining("Span returns must be declared as double[]");
    }

    @Test
    public void rejectsDoubleArrayParameter() {
        assertThat(compile(source("ArrayParam", """
                @CLibClass("ArrayParam")
                public class ArrayParam {
                    @CLibMethod(flags = Noexcept)
                    public static double sum(double[] values) { return 0; }
                }
                """))).hadErrorContaining("Span parameters must be declared as DoubleSpan");
    }

    private Compilation compile(JavaFileObject source) {
        return Compiler.javac()
                // A new instance per compilation: BasicAnnotationProcessor keeps deferred
                // elements between rounds and is not safe to reuse across compilations.
                .withProcessors(new NativeAnnotationProcessor())
                .withOptions(
                        "-Abindings.outputDir=" + outputDir.getRoot().getAbsolutePath(),
                        "-Abindings.libName=validation",
                        "-Abindings.namespace=validation")
                .compile(source);
    }

    private static JavaFileObject source(String name, String body) {
        return JavaFileObjects.forSourceString("validation." + name, String.join("\n",
                "package validation;",
                "import us.hebi.graalvm.bindings.annotations.*;",
                "import static us.hebi.graalvm.bindings.annotations.MethodFlag.*;",
                body));
    }

}
