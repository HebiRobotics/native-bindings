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

    @Test
    public void rejectsNonIntegralIndexParameter() {
        assertThat(compile(source("IndexParam", """
                @CLibClass("IndexParam")
                public class IndexParam {
                    @CLibMethod(flags = Noexcept)
                    public static int at(@CLibIndex double index) { return 0; }
                }
                """))).hadErrorContaining("@CLibIndex may only be used on integral types");
    }

    @Test
    public void rejectsNonIntegralIndexReturn() {
        assertThat(compile(source("IndexReturn", """
                @CLibClass("IndexReturn")
                public class IndexReturn {
                    @CLibMethod(flags = Noexcept)
                    public static @CLibIndex double first() { return 0; }
                }
                """))).hadErrorContaining("@CLibIndex may only be used on integral types");
    }

    @Test
    public void rejectsNonIntegerIndexDefault() {
        assertThat(compile(source("IndexDefault", """
                @CLibClass("IndexDefault")
                public class IndexDefault {
                    @CLibMethod(flags = Noexcept)
                    public static int at(@CLibIndex @CDefault("first") int index) { return 0; }
                }
                """))).hadErrorContaining("@CLibIndex requires an integer @CDefault");
    }

    @Test
    public void acceptsStaticNestedClass() {
        // The generated entry points are compiled as part of this, so a nesting-blind
        // reference to the API class shows up here as "cannot find symbol"
        assertThat(compile(source("Outer", """
                @CLibClass("Outer")
                public class Outer {
                    @CLibMethod(flags = Noexcept)
                    public static int value() { return 1; }

                    @CLibClass("Nested")
                    public static class Nested {
                        @CLibMethod(flags = Noexcept)
                        public static int value() { return 2; }
                    }
                }
                """))).succeeded();
    }

    @Test
    public void rejectsInnerClass() {
        assertThat(compile(source("InnerHolder", """
                public class InnerHolder {
                    @CLibClass("Inner")
                    public class Inner {
                        @CLibMethod(flags = Noexcept)
                        public int value() { return 1; }
                    }
                }
                """))).hadErrorContaining("@CLibClass requires a top-level or static nested class");
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
