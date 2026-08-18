package us.hebi.graalvm.bindings.tests;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import us.hebi.graalvm.bindings.generator.generators.NativeAnnotationProcessor;

import javax.tools.JavaFileObject;
import java.io.IOException;
import java.nio.file.Files;

import static com.google.testing.compile.CompilationSubject.*;
import static org.junit.Assert.*;

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
        var source = source("ConstStatic", """
                @CLibClass("ConstStatic")
                public class ConstStatic {
                    @CLibMethod(flags = Const)
                    public static int value() { return 1; }
                }
                """);
        assertThat(compile(source)).hadErrorContaining("only instance methods may be marked const")
                .inFile(source).onLineContaining("public static int value()");
    }

    @Test
    public void rejectsConstWhenInstancingIsDisabled() {
        var source = source("NoInstancing", """
                @CLibClass(value = "NoInstancing", enableInstancing = false)
                public class NoInstancing {
                    @CLibMethod(flags = Const)
                    public int value() { return 1; }
                }
                """);
        assertThat(compile(source)).hadErrorContaining("only instance methods may be marked const")
                .inFile(source).onLineContaining("public int value()");
    }

    @Test
    public void rejectsNoexceptOnThrowingMethod() {
        var source = source("Throwing", """
                @CLibClass("Throwing")
                public class Throwing {
                    @CLibMethod(flags = Noexcept)
                    public static int value() throws Exception { return 1; }
                }
                """);
        assertThat(compile(source)).hadErrorContaining("throwing methods may not be marked noexcept")
                .inFile(source).onLineContaining("throws Exception");
    }

    @Test
    public void rejectsDoubleSpanReturn() {
        var source = source("SpanReturn", """
                import us.hebi.graalvm.bindings.runtime.structs.DoubleSpan;
                @CLibClass("SpanReturn")
                public class SpanReturn {
                    @CLibMethod(flags = Noexcept)
                    public static DoubleSpan values() { return null; }
                }
                """);
        assertThat(compile(source)).hadErrorContaining("Span returns must be declared as double[]")
                .inFile(source).onLineContaining("public static DoubleSpan values()");
    }

    @Test
    public void rejectsDoubleArrayParameter() {
        var source = source("ArrayParam", """
                @CLibClass("ArrayParam")
                public class ArrayParam {
                    @CLibMethod(flags = Noexcept)
                    public static double sum(double[] values) { return 0; }
                }
                """);
        assertThat(compile(source)).hadErrorContaining("Span parameters must be declared as DoubleSpan")
                .inFile(source).onLineContaining("double sum(double[] values)");
    }

    @Test
    public void rejectsNonIntegralIndexParameter() {
        var source = source("IndexParam", """
                @CLibClass("IndexParam")
                public class IndexParam {
                    @CLibMethod(flags = Noexcept)
                    public static int at(@CLibIndex double index) { return 0; }
                }
                """);
        assertThat(compile(source)).hadErrorContaining("@CLibIndex may only be used on integral types")
                .inFile(source).onLineContaining("int at(@CLibIndex double index)");
    }

    @Test
    public void rejectsNonIntegralIndexReturn() {
        var source = source("IndexReturn", """
                @CLibClass("IndexReturn")
                public class IndexReturn {
                    @CLibMethod(flags = Noexcept)
                    public static @CLibIndex double first() { return 0; }
                }
                """);
        assertThat(compile(source)).hadErrorContaining("@CLibIndex may only be used on integral types")
                .inFile(source).onLineContaining("@CLibIndex double first()");
    }

    @Test
    public void rejectsNonIntegerIndexDefault() {
        var source = source("IndexDefault", """
                @CLibClass("IndexDefault")
                public class IndexDefault {
                    @CLibMethod(flags = Noexcept)
                    public static int at(@CLibIndex @CDefault("first") int index) { return 0; }
                }
                """);
        assertThat(compile(source)).hadErrorContaining("@CLibIndex requires an integer @CDefault")
                .inFile(source).onLineContaining("@CDefault(\"first\")");
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
        var source = source("InnerHolder", """
                public class InnerHolder {
                    @CLibClass("Inner")
                    public class Inner {
                        @CLibMethod(flags = Noexcept)
                        public int value() { return 1; }
                    }
                }
                """);
        assertThat(compile(source)).hadErrorContaining("@CLibClass requires a top-level or static nested class")
                .inFile(source).onLineContaining("public class Inner {");
    }

    @Test
    public void rejectsDuplicateClassName() {
        var source = source("Twins", """
                @CLibClass("Twin")
                public class Twins {
                    @CLibMethod(flags = Noexcept)
                    public static int value() { return 1; }

                    @CLibClass("Twin")
                    public static class Other {
                        @CLibMethod(flags = Noexcept)
                        public static int value() { return 2; }
                    }
                }
                """);
        assertThat(compile(source)).hadErrorContaining("Duplicate @CLibClass value 'Twin'")
                .inFile(source).onLineContaining("public static class Other");
    }

    @Test
    public void rejectsDuplicateEnumName() {
        var source = source("Palettes", """
                @CLibClass("Palettes")
                public class Palettes {
                    @CLibEnum("Color")
                    public enum Color { Red }

                    @CLibMethod(flags = Noexcept)
                    public static Color first() { return Color.Red; }

                    @CLibClass("OtherPalette")
                    public static class Other {
                        @CLibEnum("Color")
                        public enum Shade { Dark }

                        @CLibMethod(flags = Noexcept)
                        public static Shade first() { return Shade.Dark; }
                    }
                }
                """);
        assertThat(compile(source)).hadErrorContaining("Duplicate @CLibEnum value 'Color'")
                .inFile(source).onLineContaining("public enum Shade");
    }

    @Test
    public void rejectsClassNameThatIsNotACIdentifier() {
        var source = source("BadClass", """
                @CLibClass("Bad Name")
                public class BadClass {
                    @CLibMethod(flags = Noexcept)
                    public static int value() { return 1; }
                }
                """);
        assertThat(compile(source)).hadErrorContaining("'Bad Name' is not a valid C identifier")
                .inFile(source).onLineContaining("public class BadClass");
    }

    @Test
    public void rejectsClassPrefixThatIsNotACIdentifier() {
        var source = source("BadPrefix", """
                @CLibClass(value = "BadPrefix", cPrefix = "bad prefix")
                public class BadPrefix {
                    @CLibMethod(flags = Noexcept)
                    public static int value() { return 1; }
                }
                """);
        assertThat(compile(source)).hadErrorContaining("'bad prefix' is not a valid C identifier")
                .inFile(source).onLineContaining("public class BadPrefix");
    }

    @Test
    public void rejectsMethodNameThatIsNotACIdentifier() {
        var source = source("BadMethod", """
                @CLibClass("BadMethod")
                public class BadMethod {
                    @CLibMethod(flags = Noexcept, name = "2ndValue")
                    public static int value() { return 1; }
                }
                """);
        assertThat(compile(source)).hadErrorContaining("'2ndValue' is not a valid C identifier")
                .inFile(source).onLineContaining("public static int value()");
    }

    @Test
    public void rejectsStructNameThatIsNotACIdentifier() {
        var source = source("BadStruct", """
                import org.graalvm.nativeimage.c.struct.CField;
                import org.graalvm.nativeimage.c.struct.CStruct;
                import org.graalvm.word.PointerBase;
                @CLibClass("BadStruct")
                public class BadStruct {
                    @CLibStruct("Bad Point")
                    @CStruct("Point")
                    public interface Point extends PointerBase {
                        @CField("x") double x();
                    }

                    @CLibMethod(flags = Noexcept)
                    public static int value() { return 1; }
                }
                """);
        assertThat(compile(source)).hadErrorContaining("'Bad Point' is not a valid C identifier")
                .inFile(source).onLineContaining("public interface Point extends PointerBase");
    }

    @Test
    public void rejectsStructNameThatCollidesWithAClass() {
        var source = source("ClassStructCollision", """
                import org.graalvm.nativeimage.c.struct.CField;
                import org.graalvm.nativeimage.c.struct.CStruct;
                import org.graalvm.word.PointerBase;
                @CLibClass("Point")
                public class ClassStructCollision {
                    @CLibStruct("Point")
                    @CStruct("Coord")
                    public interface Coord extends PointerBase {
                        @CField("x") double x();
                    }

                    @CLibMethod(flags = Noexcept)
                    public static int value() { return 1; }
                }
                """);
        assertThat(compile(source)).hadErrorContaining(
                "Struct name 'Point' collides with class Point: validation.ClassStructCollision.Coord")
                .inFile(source).onLineContaining("public interface Coord extends PointerBase");
    }

    @Test
    public void rejectsEnumNameThatCollidesWithAClass() {
        var source = source("EnumClassCollision", """
                @CLibClass("Shade")
                public class EnumClassCollision {
                    @CLibEnum("Shade")
                    public enum Tone { Dark }

                    @CLibMethod(flags = Noexcept)
                    public static Tone first() { return Tone.Dark; }
                }
                """);
        assertThat(compile(source)).hadErrorContaining(
                "Enum name 'Shade' collides with class Shade: validation.EnumClassCollision.Tone")
                .inFile(source).onLineContaining("public enum Tone");
    }

    @Test
    public void acceptsAStructView() {
        assertThat(compile(source("Points", """
                import org.graalvm.nativeimage.c.struct.CField;
                import org.graalvm.nativeimage.c.struct.CStruct;
                import org.graalvm.word.PointerBase;
                @CLibClass("Points")
                public class Points {
                    @CLibStruct
                    @CStruct("Point")
                    public interface Point extends PointerBase {
                        @CField("x") double x();
                        @CField("x") void setX(double x);
                        @CField("y") double y();
                    }

                    @CLibMethod(flags = Noexcept)
                    public static int count() { return 1; }
                }
                """))).succeeded();
    }

    @Test
    public void acceptsAReferenceToGeneratedStructDirectives() {
        // The view names its own generated directives class, which only exists once the
        // processor has run. BasicAnnotationProcessor would defer the @CLibClass on the
        // unresolved reference forever, so the artifacts are generated from the raw round in
        // postRound, and this pins that the deferred class then resolves and processes.
        assertThat(compile(source("Contexts", """
                import org.graalvm.nativeimage.c.CContext;
                import org.graalvm.nativeimage.c.struct.AllowNarrowingCast;
                import org.graalvm.nativeimage.c.struct.CField;
                import org.graalvm.nativeimage.c.struct.CStruct;
                import org.graalvm.word.PointerBase;
                @CLibClass("Contexts")
                public class Contexts {
                    @CLibStruct
                    @CContext(validation.generated.Contexts_Point$$Directives.class)
                    @CStruct("Point")
                    public interface Point extends PointerBase {
                        @CField("x") double x();
                        @CField("x") void setX(double x);
                        @CField("enabled") @AllowNarrowingCast boolean isEnabled();
                    }

                    @CLibMethod(flags = Noexcept)
                    public static int count() { return 1; }
                }
                """))).succeeded();
    }

    @Test
    public void rejectsStructViewOnNonInterface() {
        var source = source("StructClass", """
                @CLibClass("StructClass")
                public class StructClass {
                    @CLibStruct("Point")
                    public static class Point { }

                    @CLibMethod(flags = Noexcept)
                    public static int count() { return 1; }
                }
                """);
        assertThat(compile(source)).hadErrorContaining("@CLibStruct may only be used on an interface")
                .inFile(source).onLineContaining("public static class Point");
    }

    @Test
    public void rejectsStructViewWithoutCStruct() {
        var source = source("NoCStruct", """
                import org.graalvm.nativeimage.c.struct.CField;
                import org.graalvm.word.PointerBase;
                @CLibClass("NoCStruct")
                public class NoCStruct {
                    @CLibStruct("Point")
                    public interface Point extends PointerBase {
                        @CField("x") double x();
                    }

                    @CLibMethod(flags = Noexcept)
                    public static int count() { return 1; }
                }
                """);
        assertThat(compile(source)).hadErrorContaining("@CLibStruct requires a GraalVM @CStruct annotation")
                .inFile(source).onLineContaining("public interface Point extends PointerBase");
    }

    @Test
    public void rejectsStructViewWithoutPointerBase() {
        var source = source("NoPointerBase", """
                import org.graalvm.nativeimage.c.struct.CField;
                import org.graalvm.nativeimage.c.struct.CStruct;
                @CLibClass("NoPointerBase")
                public class NoPointerBase {
                    @CLibStruct
                    @CStruct("Point")
                    public interface Point {
                        @CField("x") double x();
                    }

                    @CLibMethod(flags = Noexcept)
                    public static int count() { return 1; }
                }
                """);
        assertThat(compile(source)).hadErrorContaining("@CLibStruct view must extend PointerBase")
                .inFile(source).onLineContaining("public interface Point {");
    }

    @Test
    public void rejectsNonAccessorOnStructView() {
        var source = source("DefaultMethod", """
                import org.graalvm.nativeimage.c.struct.CField;
                import org.graalvm.nativeimage.c.struct.CStruct;
                import org.graalvm.word.PointerBase;
                @CLibClass("DefaultMethod")
                public class DefaultMethod {
                    @CLibStruct
                    @CStruct("Point")
                    public interface Point extends PointerBase {
                        @CField("x") double x();
                        default double twiceX() { return 2 * x(); }
                    }

                    @CLibMethod(flags = Noexcept)
                    public static int count() { return 1; }
                }
                """);
        assertThat(compile(source)).hadErrorContaining("may only declare @CField accessors, found 'twiceX'")
                .inFile(source).onLineContaining("public interface Point extends PointerBase");
    }

    @Test
    public void rejectsStructSetterWithoutGetter() {
        var source = source("SetterOnly", """
                import org.graalvm.nativeimage.c.struct.CField;
                import org.graalvm.nativeimage.c.struct.CStruct;
                import org.graalvm.word.PointerBase;
                @CLibClass("SetterOnly")
                public class SetterOnly {
                    @CLibStruct
                    @CStruct("Point")
                    public interface Point extends PointerBase {
                        @CField("x") double x();
                        @CField("y") void setY(double y);
                    }

                    @CLibMethod(flags = Noexcept)
                    public static int count() { return 1; }
                }
                """);
        assertThat(compile(source)).hadErrorContaining("@CField 'y' has a setter but no matching getter")
                .inFile(source).onLineContaining("public interface Point extends PointerBase");
    }

    @Test
    public void rejectsNonPrimitiveStructField() {
        var source = source("StringField", """
                import org.graalvm.nativeimage.c.struct.CField;
                import org.graalvm.nativeimage.c.struct.CStruct;
                import org.graalvm.word.PointerBase;
                @CLibClass("StringField")
                public class StringField {
                    @CLibStruct
                    @CStruct("Point")
                    public interface Point extends PointerBase {
                        @CField("label") String label();
                    }

                    @CLibMethod(flags = Noexcept)
                    public static int count() { return 1; }
                }
                """);
        assertThat(compile(source)).hadErrorContaining("@CLibStruct fields may only be primitives, @CSizeType long, or typed GraalVM pointers")
                .inFile(source).onLineContaining("public interface Point extends PointerBase");
    }

    @Test
    public void acceptsPointerStructFields() {
        // Pins pointer fields through the full pipeline: the layout mirrors carry every
        // field, the record faces skip the pointer ones, and the throwing @CLibOut method
        // exercises the generated out-struct zeroing for pointer setters.
        assertThat(compile(source("PointerFields", """
                import org.graalvm.nativeimage.c.struct.CField;
                import org.graalvm.nativeimage.c.struct.CStruct;
                import org.graalvm.nativeimage.c.type.CCharPointer;
                import org.graalvm.nativeimage.c.type.CDoublePointer;
                import org.graalvm.word.PointerBase;
                @CLibClass("PointerFields")
                public class PointerFields {
                    @CLibStruct
                    @CStruct("Row")
                    public interface Row extends PointerBase {
                        @CField("scale") double scale();
                        @CField("scale") void setScale(double scale);
                        @CField("values") CDoublePointer values();
                        @CField("values") void setValues(CDoublePointer values);
                        @CField("label") CCharPointer label();
                        @CField("label") void setLabel(CCharPointer label);
                    }

                    @CLibMethod(flags = Noexcept)
                    public static double sum(Row row) { return 0; }

                    @CLibMethod
                    public static void fill(int index, @CLibOut Row row) throws Exception { }
                }
                """))).succeeded();
    }

    @Test
    public void rejectsBoolAccessorWithoutCastAnnotation() {
        // The field is stored as int32 while a Java boolean is 1 byte, so the image build
        // rejects the accessor unless it allows the cast explicitly
        var source = source("BoolField", """
                import org.graalvm.nativeimage.c.struct.CField;
                import org.graalvm.nativeimage.c.struct.CStruct;
                import org.graalvm.word.PointerBase;
                @CLibClass("BoolField")
                public class BoolField {
                    @CLibStruct
                    @CStruct("Point")
                    public interface Point extends PointerBase {
                        @CField("enabled") boolean enabled();
                    }

                    @CLibMethod(flags = Noexcept)
                    public static int count() { return 1; }
                }
                """);
        assertThat(compile(source)).hadErrorContaining("boolean accessor 'enabled' requires @AllowNarrowingCast")
                .inFile(source).onLineContaining("public interface Point extends PointerBase");
    }

    @Test
    public void rejectsStructViewReturn() {
        var source = source("StructReturn", """
                import org.graalvm.nativeimage.c.struct.CField;
                import org.graalvm.nativeimage.c.struct.CStruct;
                import org.graalvm.word.PointerBase;
                @CLibClass("StructReturn")
                public class StructReturn {
                    @CLibStruct
                    @CStruct("Point")
                    public interface Point extends PointerBase {
                        @CField("x") double x();
                    }

                    @CLibMethod(flags = Noexcept)
                    public static Point origin() { return null; }
                }
                """);
        assertThat(compile(source)).hadErrorContaining("@CLibStruct views may not be returned, declare void with a @CLibOut parameter")
                .inFile(source).onLineContaining("public static Point origin()");
    }

    @Test
    public void rejectsStructFieldWithDisagreeingConstness() {
        var source = source("MixedConst", """
                import org.graalvm.nativeimage.c.struct.CField;
                import org.graalvm.nativeimage.c.struct.CStruct;
                import org.graalvm.nativeimage.c.type.CConst;
                import org.graalvm.nativeimage.c.type.CDoublePointer;
                import org.graalvm.word.PointerBase;
                @CLibClass("MixedConst")
                public class MixedConst {
                    @CLibStruct
                    @CStruct("Point")
                    public interface Point extends PointerBase {
                        @CField("data") @CConst CDoublePointer data();
                        @CField("data") void setData(CDoublePointer data);
                    }
                }
                """);
        assertThat(compile(source)).hadErrorContaining("is declared @CConst on one accessor and not the other")
                .inFile(source).onLineContaining("public interface Point extends PointerBase");
    }

    @Test
    public void rejectsConstOnANonPointerStructField() {
        var source = source("ConstValue", """
                import org.graalvm.nativeimage.c.struct.CField;
                import org.graalvm.nativeimage.c.struct.CStruct;
                import org.graalvm.nativeimage.c.type.CConst;
                import org.graalvm.word.PointerBase;
                @CLibClass("ConstValue")
                public class ConstValue {
                    @CLibStruct
                    @CStruct("Point")
                    public interface Point extends PointerBase {
                        @CField("x") @CConst double x();
                    }
                }
                """);
        assertThat(compile(source)).hadErrorContaining("@CConst field 'x' must be a pointer")
                .inFile(source).onLineContaining("public interface Point extends PointerBase");
    }

    @Test
    public void acceptsAStructViewParameter() {
        assertThat(compile(source("StructParam", """
                import org.graalvm.nativeimage.c.struct.CField;
                import org.graalvm.nativeimage.c.struct.CStruct;
                import org.graalvm.word.PointerBase;
                @CLibClass("StructParam")
                public class StructParam {
                    @CLibStruct
                    @CStruct("Point")
                    public interface Point extends PointerBase {
                        @CField("x") double x();
                        @CField("y") double y();
                    }

                    @CLibMethod(flags = Noexcept)
                    public static double distance(Point point) { return 0; }
                }
                """))).succeeded();
    }

    @Test
    public void acceptsAnOutParameter() {
        assertThat(compile(source("OutParam", """
                import org.graalvm.nativeimage.c.struct.AllowNarrowingCast;
                import org.graalvm.nativeimage.c.struct.AllowWideningCast;
                import org.graalvm.nativeimage.c.struct.CField;
                import org.graalvm.nativeimage.c.struct.CStruct;
                import org.graalvm.word.PointerBase;
                @CLibClass("OutParam")
                public class OutParam {
                    @CLibStruct
                    @CStruct("Point")
                    public interface Point extends PointerBase {
                        @CField("x") double x();
                        @CField("x") void setX(double x);
                        @CField("valid") @AllowNarrowingCast boolean valid();
                        @CField("valid") @AllowWideningCast void setValid(boolean valid);
                    }

                    @CLibMethod
                    public static void corner(int index, @CLibOut Point point) { }
                }
                """))).succeeded();
    }

    @Test
    public void rejectsANonTrailingOutParameter() {
        var source = source("LeadingOutParam", """
                import org.graalvm.nativeimage.c.struct.CField;
                import org.graalvm.nativeimage.c.struct.CStruct;
                import org.graalvm.word.PointerBase;
                @CLibClass("LeadingOutParam")
                public class LeadingOutParam {
                    @CLibStruct
                    @CStruct("Point")
                    public interface Point extends PointerBase {
                        @CField("x") double x();
                        @CField("x") void setX(double x);
                    }

                    @CLibMethod(flags = Noexcept)
                    public static void corner(@CLibOut Point point, int index) { }
                }
                """);
        assertThat(compile(source)).hadErrorContaining("the @CLibOut parameter must be declared last")
                .inFile(source).onLineContaining("public static void corner(@CLibOut Point point, int index)");
    }

    @Test
    public void rejectsOutParameterOnNonStructView() {
        var source = source("OutOnPrimitive", """
                @CLibClass("OutOnPrimitive")
                public class OutOnPrimitive {
                    @CLibMethod(flags = Noexcept)
                    public static void origin(@CLibOut double x) { }
                }
                """);
        assertThat(compile(source)).hadErrorContaining("@CLibOut may only be used with a @CLibStruct annotated type, got 'x'")
                .inFile(source).onLineContaining("public static void origin(@CLibOut double x)");
    }

    @Test
    public void rejectsTwoOutParameters() {
        var source = source("TwoOutParams", """
                import org.graalvm.nativeimage.c.struct.CField;
                import org.graalvm.nativeimage.c.struct.CStruct;
                import org.graalvm.word.PointerBase;
                @CLibClass("TwoOutParams")
                public class TwoOutParams {
                    @CLibStruct
                    @CStruct("Point")
                    public interface Point extends PointerBase {
                        @CField("x") double x();
                        @CField("x") void setX(double x);
                    }

                    @CLibMethod(flags = Noexcept)
                    public static void corners(@CLibOut Point first, @CLibOut Point second) { }
                }
                """);
        assertThat(compile(source)).hadErrorContaining("at most one @CLibOut parameter is allowed, found a second one on 'second'")
                .inFile(source).onLineContaining("public static void corners(");
    }

    @Test
    public void rejectsOutParameterWithNonVoidReturn() {
        var source = source("OutWithReturn", """
                import org.graalvm.nativeimage.c.struct.CField;
                import org.graalvm.nativeimage.c.struct.CStruct;
                import org.graalvm.word.PointerBase;
                @CLibClass("OutWithReturn")
                public class OutWithReturn {
                    @CLibStruct
                    @CStruct("Point")
                    public interface Point extends PointerBase {
                        @CField("x") double x();
                        @CField("x") void setX(double x);
                    }

                    @CLibMethod(flags = Noexcept)
                    public static int origin(@CLibOut Point point) { return 0; }
                }
                """);
        assertThat(compile(source)).hadErrorContaining("a method with a @CLibOut parameter must be declared void")
                .inFile(source).onLineContaining("public static int origin(@CLibOut Point point)");
    }

    @Test
    public void rejectsOutViewWithoutSetterForEveryField() {
        var source = source("PartialOutView", """
                import org.graalvm.nativeimage.c.struct.CField;
                import org.graalvm.nativeimage.c.struct.CStruct;
                import org.graalvm.word.PointerBase;
                @CLibClass("PartialOutView")
                public class PartialOutView {
                    @CLibStruct
                    @CStruct("Point")
                    public interface Point extends PointerBase {
                        @CField("x") double x();
                        @CField("x") void setX(double x);
                        @CField("y") double y();
                    }

                    @CLibMethod(flags = Noexcept)
                    public static void origin(@CLibOut Point point) { }
                }
                """);
        assertThat(compile(source)).hadErrorContaining("@CLibOut view requires a setter for every field, missing [y]")
                .inFile(source).onLineContaining("public interface Point extends PointerBase");
    }

    @Test
    public void rejectsDuplicateStructName() {
        var source = source("Shapes", """
                import org.graalvm.nativeimage.c.struct.CField;
                import org.graalvm.nativeimage.c.struct.CStruct;
                import org.graalvm.word.PointerBase;
                @CLibClass("Shapes")
                public class Shapes {
                    @CLibStruct
                    @CStruct("Point")
                    public interface Point extends PointerBase {
                        @CField("x") double x();
                    }

                    @CLibMethod(flags = Noexcept)
                    public static int count() { return 1; }

                    @CLibClass("OtherShapes")
                    public static class Other {
                        @CLibStruct("Point")
                        @CStruct("OtherPoint")
                        public interface Corner extends PointerBase {
                            @CField("x") double x();
                        }

                        @CLibMethod(flags = Noexcept)
                        public static int count() { return 2; }
                    }
                }
                """);
        assertThat(compile(source)).hadErrorContaining("Duplicate @CLibStruct value 'Point'")
                .inFile(source).onLineContaining("public interface Corner extends PointerBase");
    }

    @Test
    public void rejectsClasspathViewWithoutContext() {
        assertThat(compile(source("ClasspathNoContext", """
                import us.hebi.graalvm.bindings.tests.fixtures.NoContextView;
                @CLibClass("ClasspathNoContext")
                public class ClasspathNoContext {
                    @CLibMethod(flags = Noexcept)
                    public static double x(NoContextView point) { return 0; }
                }
                """))).hadErrorContaining(
                "A precompiled @CLibStruct view must ship its own directives and name them in @CContext");
    }

    @Test
    public void acceptsClasspathViewWithContext() {
        assertThat(compile(source("ClasspathWithContext", """
                import us.hebi.graalvm.bindings.tests.fixtures.WithContextView;
                @CLibClass("ClasspathWithContext")
                public class ClasspathWithContext {
                    @CLibMethod(flags = Noexcept)
                    public static double x(WithContextView point) { return 0; }
                }
                """))).succeeded();
    }

    @Test
    public void registersTheAnnotationMarshaller() throws IOException {
        assertThat(compile(source("Marshalled", """
                import us.hebi.graalvm.bindings.tests.fixtures.MarshalledView;
                @CLibClass("Marshalled")
                public class Marshalled {
                    @CLibMethod(flags = Noexcept)
                    public static double x(MarshalledView point) { return 0; }
                }
                """))).succeeded();

        // The custom face and its include contribution reach the generated C++ wrapper
        String hpp = readGeneratedFile("validation.hpp");
        assertTrue(hpp.contains("#include <array>"));
        assertTrue(hpp.contains("const std::array<double, 1>&"));
    }

    @Test
    public void rejectsAClassThatIsNotAMarshaller() {
        var source = source("NotAMarshaller", """
                import org.graalvm.nativeimage.c.struct.CField;
                import org.graalvm.nativeimage.c.struct.CStruct;
                import org.graalvm.word.PointerBase;
                @CLibClass("NotAMarshaller")
                public class NotAMarshaller {
                    @CLibStruct(marshaller = String.class)
                    @CStruct("BadView")
                    public interface BadView extends PointerBase {
                        @CField("x") double x();
                    }

                    @CLibMethod(flags = Noexcept)
                    public static double x(BadView view) { return 0; }
                }
                """);
        assertThat(compile(source)).hadErrorContaining("Struct marshaller does not implement")
                .inFile(source).onLineContaining("public interface BadView extends PointerBase");
    }

    @Test
    public void acceptsOneMarshallerOnTwoViews() {
        // The annotated view is the only naming direction, so a marshaller class is
        // reusable across views rather than being tied to a single one.
        assertThat(compile(source("SharedMarshaller", """
                import org.graalvm.nativeimage.c.struct.CField;
                import org.graalvm.nativeimage.c.struct.CStruct;
                import org.graalvm.word.PointerBase;
                import us.hebi.graalvm.bindings.tests.fixtures.MarshalledViewMarshaller;
                @CLibClass("SharedMarshaller")
                public class SharedMarshaller {
                    @CLibStruct(marshaller = MarshalledViewMarshaller.class)
                    @CStruct("FirstView")
                    public interface FirstView extends PointerBase {
                        @CField("x") double x();
                    }

                    @CLibStruct(marshaller = MarshalledViewMarshaller.class)
                    @CStruct("SecondView")
                    public interface SecondView extends PointerBase {
                        @CField("x") double x();
                    }

                    @CLibMethod(flags = Noexcept)
                    public static double x(FirstView first, SecondView second) { return 0; }
                }
                """))).succeeded();
    }

    @Test
    public void rejectsCallbackWithoutInvokeMethod() {
        var source = source("NoInvoke", """
                import org.graalvm.nativeimage.c.function.CFunctionPointer;
                @CLibClass("NoInvoke")
                public class NoInvoke {
                    public interface Callback extends CFunctionPointer {
                    }

                    @CLibMethod(flags = Noexcept)
                    public static void run(Callback callback) { }
                }
                """);
        assertThat(compile(source)).hadErrorContaining("exactly one @InvokeCFunctionPointer method, found none")
                .inFile(source).onLineContaining("public interface Callback extends CFunctionPointer");
    }

    @Test
    public void rejectsCallbackWithTwoInvokeMethods() {
        var source = source("TwoInvokes", """
                import org.graalvm.nativeimage.c.function.CFunctionPointer;
                import org.graalvm.nativeimage.c.function.InvokeCFunctionPointer;
                @CLibClass("TwoInvokes")
                public class TwoInvokes {
                    public interface Callback extends CFunctionPointer {
                        @InvokeCFunctionPointer void first(int index);
                        @InvokeCFunctionPointer void second(int index);
                    }

                    @CLibMethod(flags = Noexcept)
                    public static void run(Callback callback) { }
                }
                """);
        assertThat(compile(source)).hadErrorContaining("exactly one @InvokeCFunctionPointer method, found [first, second]")
                .inFile(source).onLineContaining("public interface Callback extends CFunctionPointer");
    }

    @Test
    public void rejectsCallbackWithUnmappableParameter() {
        var source = source("HandleCallback", """
                import org.graalvm.nativeimage.c.function.CFunctionPointer;
                import org.graalvm.nativeimage.c.function.InvokeCFunctionPointer;
                @CLibClass("HandleCallback")
                public class HandleCallback {
                    public interface Callback extends CFunctionPointer {
                        @InvokeCFunctionPointer void invoke(HandleCallback handle);
                    }

                    @CLibMethod(flags = Noexcept)
                    public static void run(Callback callback) { }
                }
                """);
        assertThat(compile(source)).hadErrorContaining("is not supported in a callback signature: Callback.invoke")
                .inFile(source).onLineContaining("void invoke(HandleCallback handle)");
    }

    /**
     * A struct view crosses a function pointer as a pointer, and the callee has no memory to
     * point a returned one at, so only the parameter direction is admitted.
     */
    @Test
    public void rejectsCallbackReturningAStructView() {
        var source = source("SpanCallback", """
                import org.graalvm.nativeimage.c.function.CFunctionPointer;
                import org.graalvm.nativeimage.c.function.InvokeCFunctionPointer;
                import us.hebi.graalvm.bindings.runtime.structs.DoubleSpan;
                @CLibClass("SpanCallback")
                public class SpanCallback {
                    public interface Callback extends CFunctionPointer {
                        @InvokeCFunctionPointer DoubleSpan invoke(DoubleSpan span);
                    }

                    @CLibMethod(flags = Noexcept)
                    public static void run(Callback callback) { }
                }
                """);
        assertThat(compile(source)).hadErrorContaining("@InvokeCFunctionPointer return type"
                                          + " 'us.hebi.graalvm.bindings.runtime.structs.DoubleSpan'"
                                          + " is not supported in a callback signature: Callback.invoke")
                .inFile(source).onLineContaining("DoubleSpan invoke(DoubleSpan span)");
    }

    /**
     * The annotated API declares plain Java enums, and native-image rejects those in an
     * {@code @InvokeCFunctionPointer} signature ("parameter types are restricted to primitive
     * types, word types and enumerations (@CEnum)"). Only stage 1 sees that, so the processor
     * has to say it first. See plans item 1 for what admitting them would take.
     */
    @Test
    public void rejectsCallbackWithJavaEnumParameter() {
        var source = source("EnumCallback", """
                import org.graalvm.nativeimage.c.function.CFunctionPointer;
                import org.graalvm.nativeimage.c.function.InvokeCFunctionPointer;
                @CLibClass("EnumCallback")
                public class EnumCallback {
                    @CLibEnum("Color")
                    public enum Color { Red, Green }

                    public interface Callback extends CFunctionPointer {
                        @InvokeCFunctionPointer void invoke(Color value);
                    }

                    @CLibMethod(flags = Noexcept)
                    public static void run(Callback callback) { }
                }
                """);
        assertThat(compile(source)).hadErrorContaining("@InvokeCFunctionPointer parameter type 'validation.EnumCallback.Color'"
                                          + " is a Java enum, which a callback signature cannot use because GraalVM"
                                          + " only accepts @CEnum types there: Callback.invoke")
                .inFile(source).onLineContaining("void invoke(Color value)");
    }

    @Test
    public void rejectsCallbackAnnotationOnNonFunctionPointer() {
        var source = source("NotAPointer", """
                @CLibClass("NotAPointer")
                public class NotAPointer {
                    @CLibCallback
                    public interface Callback {
                    }

                    @CLibMethod(flags = Noexcept)
                    public static int value() { return 1; }
                }
                """);
        assertThat(compile(source)).hadErrorContaining("@CLibCallback requires an interface extending CFunctionPointer: validation.NotAPointer.Callback")
                .inFile(source).onLineContaining("public interface Callback {");
    }

    @Test
    public void appliesTheCallbackNameOverride() throws IOException {
        assertThat(compile(source("Renamed", """
                import org.graalvm.nativeimage.c.function.CFunctionPointer;
                import org.graalvm.nativeimage.c.function.InvokeCFunctionPointer;
                @CLibClass("Renamed")
                public class Renamed {
                    @CLibCallback("OnValue")
                    public interface Callback extends CFunctionPointer {
                        @InvokeCFunctionPointer void invoke(int value);
                    }

                    @CLibMethod(flags = Noexcept)
                    public static void run(Callback callback) { }
                }
                """))).succeeded();

        // The override names the typedef everywhere, so the descriptor is what the
        // language faces then read it from
        String json = readGeneratedFile("api.json");
        assertTrue(json.contains("\"name\": \"OnValue\""));
        assertFalse(json.contains("\"Callback\""));
    }

    @Test
    public void appliesTheCallbackPrefixOverride() throws IOException {
        assertThat(compile(source("Prefixed", """
                import org.graalvm.nativeimage.c.function.CFunctionPointer;
                import org.graalvm.nativeimage.c.function.InvokeCFunctionPointer;
                @CLibClass("Prefixed")
                public class Prefixed {
                    @CLibCallback(cPrefix = "custom")
                    public interface Callback extends CFunctionPointer {
                        @InvokeCFunctionPointer void invoke(int value);
                    }

                    @CLibMethod(flags = Noexcept)
                    public static void run(Callback callback) { }
                }
                """))).succeeded();

        // Only C needs the prefix to stay unique, the namespaced faces keep the plain name
        assertTrue(readGeneratedFile("validation_c.h").contains("typedef void (*custom_Callback)(int value);"));
        assertFalse(readGeneratedFile("validation_c.h").contains("validation_Callback"));
        assertTrue(readGeneratedFile("validation.hpp").contains("using Callback = void (*)(int value);"));
        assertTrue(readGeneratedFile("validation.py").contains("Callback = CFUNCTYPE(None, c_int)"));
    }

    @Test
    public void rejectsCallbackNameThatIsNotACIdentifier() {
        var source = source("BadName", """
                import org.graalvm.nativeimage.c.function.CFunctionPointer;
                import org.graalvm.nativeimage.c.function.InvokeCFunctionPointer;
                @CLibClass("BadName")
                public class BadName {
                    @CLibCallback("on value")
                    public interface Callback extends CFunctionPointer {
                        @InvokeCFunctionPointer void invoke(int value);
                    }

                    @CLibMethod(flags = Noexcept)
                    public static void run(Callback callback) { }
                }
                """);
        assertThat(compile(source)).hadErrorContaining("'on value' is not a valid C identifier")
                .inFile(source).onLineContaining("public interface Callback extends CFunctionPointer");
    }

    @Test
    public void rejectsCallbackNameThatCollidesWithAClass() {
        var source = source("Collision", """
                import org.graalvm.nativeimage.c.function.CFunctionPointer;
                import org.graalvm.nativeimage.c.function.InvokeCFunctionPointer;
                @CLibClass("Collision")
                public class Collision {
                    @CLibCallback("Collision")
                    public interface Callback extends CFunctionPointer {
                        @InvokeCFunctionPointer void invoke(int value);
                    }

                    @CLibMethod(flags = Noexcept)
                    public static void run(Callback callback) { }
                }
                """);
        assertThat(compile(source)).hadErrorContaining(
                "Callback name 'Collision' collides with class Collision: validation.Collision.Callback")
                .inFile(source).onLineContaining("public interface Callback extends CFunctionPointer");
    }

    @Test
    public void rejectsCallbackReturn() {
        var source = source("CallbackReturn", """
                import org.graalvm.nativeimage.c.function.CFunctionPointer;
                import org.graalvm.nativeimage.c.function.InvokeCFunctionPointer;
                @CLibClass("CallbackReturn")
                public class CallbackReturn {
                    public interface Callback extends CFunctionPointer {
                        @InvokeCFunctionPointer void invoke(int value);
                    }

                    @CLibMethod(flags = Noexcept)
                    public static Callback current() { return null; }
                }
                """);
        assertThat(compile(source)).hadErrorContaining(
                "a CFunctionPointer return is not supported until a use case needs it: current")
                .inFile(source).onLineContaining("public static Callback current()");
    }

    private String readGeneratedFile(String fileName) throws IOException {
        try (var files = Files.walk(outputDir.getRoot().toPath())) {
            var file = files.filter(f -> f.getFileName().toString().equals(fileName)).findFirst();
            assertTrue("Missing generated file: " + fileName, file.isPresent());
            return Files.readString(file.get());
        }
    }

    private Compilation compile(JavaFileObject... sources) {
        return Compiler.javac()
                // A new instance per compilation: BasicAnnotationProcessor keeps deferred
                // elements between rounds and is not safe to reuse across compilations.
                .withProcessors(new NativeAnnotationProcessor())
                .withOptions(
                        "-Abindings.outputDir=" + outputDir.getRoot().getAbsolutePath(),
                        "-Abindings.libName=validation",
                        "-Abindings.namespace=validation")
                .compile(sources);
    }

    private static JavaFileObject source(String name, String body) {
        return JavaFileObjects.forSourceString("validation." + name, String.join("\n",
                "package validation;",
                "import us.hebi.graalvm.bindings.annotations.*;",
                "import static us.hebi.graalvm.bindings.annotations.MethodFlag.*;",
                body));
    }

}
