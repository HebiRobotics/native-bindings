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
                import us.hebi.graalvm.bindings.runtime.structs.DoubleSpan;
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

    @Test
    public void rejectsDuplicateClassName() {
        assertThat(compile(source("Twins", """
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
                """))).hadErrorContaining("Duplicate @CLibClass value 'Twin'");
    }

    @Test
    public void rejectsDuplicateEnumName() {
        assertThat(compile(source("Palettes", """
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
                """))).hadErrorContaining("Duplicate @CLibEnum value 'Color'");
    }

    @Test
    public void rejectsClassNameThatIsNotACIdentifier() {
        assertThat(compile(source("BadClass", """
                @CLibClass("Bad Name")
                public class BadClass {
                    @CLibMethod(flags = Noexcept)
                    public static int value() { return 1; }
                }
                """))).hadErrorContaining("'Bad Name' is not a valid C identifier");
    }

    @Test
    public void rejectsClassPrefixThatIsNotACIdentifier() {
        assertThat(compile(source("BadPrefix", """
                @CLibClass(value = "BadPrefix", cPrefix = "bad prefix")
                public class BadPrefix {
                    @CLibMethod(flags = Noexcept)
                    public static int value() { return 1; }
                }
                """))).hadErrorContaining("'bad prefix' is not a valid C identifier");
    }

    @Test
    public void rejectsMethodNameThatIsNotACIdentifier() {
        assertThat(compile(source("BadMethod", """
                @CLibClass("BadMethod")
                public class BadMethod {
                    @CLibMethod(flags = Noexcept, name = "2ndValue")
                    public static int value() { return 1; }
                }
                """))).hadErrorContaining("'2ndValue' is not a valid C identifier");
    }

    @Test
    public void rejectsStructNameThatIsNotACIdentifier() {
        assertThat(compile(source("BadStruct", """
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
                """))).hadErrorContaining("'Bad Point' is not a valid C identifier");
    }

    @Test
    public void rejectsStructNameThatCollidesWithAClass() {
        assertThat(compile(source("ClassStructCollision", """
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
                """))).hadErrorContaining(
                "Struct name 'Point' collides with class Point: validation.ClassStructCollision.Coord");
    }

    @Test
    public void rejectsEnumNameThatCollidesWithAClass() {
        assertThat(compile(source("EnumClassCollision", """
                @CLibClass("Shade")
                public class EnumClassCollision {
                    @CLibEnum("Shade")
                    public enum Tone { Dark }

                    @CLibMethod(flags = Noexcept)
                    public static Tone first() { return Tone.Dark; }
                }
                """))).hadErrorContaining(
                "Enum name 'Shade' collides with class Shade: validation.EnumClassCollision.Tone");
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
        assertThat(compile(source("StructClass", """
                @CLibClass("StructClass")
                public class StructClass {
                    @CLibStruct("Point")
                    public static class Point { }

                    @CLibMethod(flags = Noexcept)
                    public static int count() { return 1; }
                }
                """))).hadErrorContaining("@CLibStruct may only be used on an interface");
    }

    @Test
    public void rejectsStructViewWithoutCStruct() {
        assertThat(compile(source("NoCStruct", """
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
                """))).hadErrorContaining("@CLibStruct requires a GraalVM @CStruct annotation");
    }

    @Test
    public void rejectsStructViewWithoutPointerBase() {
        assertThat(compile(source("NoPointerBase", """
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
                """))).hadErrorContaining("@CLibStruct view must extend PointerBase");
    }

    @Test
    public void rejectsNonAccessorOnStructView() {
        assertThat(compile(source("DefaultMethod", """
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
                """))).hadErrorContaining("may only declare @CField accessors, found 'twiceX'");
    }

    @Test
    public void rejectsStructSetterWithoutGetter() {
        assertThat(compile(source("SetterOnly", """
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
                """))).hadErrorContaining("@CField 'y' has a setter but no matching getter");
    }

    @Test
    public void rejectsNonPrimitiveStructField() {
        assertThat(compile(source("StringField", """
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
                """))).hadErrorContaining("@CLibStruct fields may only be primitives, @CSizeType long, or typed GraalVM pointers");
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
        assertThat(compile(source("BoolField", """
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
                """))).hadErrorContaining("boolean accessor 'enabled' requires @AllowNarrowingCast");
    }

    @Test
    public void rejectsStructViewReturn() {
        assertThat(compile(source("StructReturn", """
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
                """))).hadErrorContaining("@CLibStruct views may not be returned, declare void with a @CLibOut parameter");
    }

    @Test
    public void rejectsStructFieldWithDisagreeingConstness() {
        assertThat(compile(source("MixedConst", """
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
                """))).hadErrorContaining("is declared @CConst on one accessor and not the other");
    }

    @Test
    public void rejectsConstOnANonPointerStructField() {
        assertThat(compile(source("ConstValue", """
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
                """))).hadErrorContaining("@CConst field 'x' must be a pointer");
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
        assertThat(compile(source("LeadingOutParam", """
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
                """))).hadErrorContaining("the @CLibOut parameter must be declared last");
    }

    @Test
    public void rejectsOutParameterOnNonStructView() {
        assertThat(compile(source("OutOnPrimitive", """
                @CLibClass("OutOnPrimitive")
                public class OutOnPrimitive {
                    @CLibMethod(flags = Noexcept)
                    public static void origin(@CLibOut double x) { }
                }
                """))).hadErrorContaining("@CLibOut may only be used with a @CLibStruct annotated type, got 'x'");
    }

    @Test
    public void rejectsTwoOutParameters() {
        assertThat(compile(source("TwoOutParams", """
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
                """))).hadErrorContaining("at most one @CLibOut parameter is allowed, found a second one on 'second'");
    }

    @Test
    public void rejectsOutParameterWithNonVoidReturn() {
        assertThat(compile(source("OutWithReturn", """
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
                """))).hadErrorContaining("a method with a @CLibOut parameter must be declared void");
    }

    @Test
    public void rejectsOutViewWithoutSetterForEveryField() {
        assertThat(compile(source("PartialOutView", """
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
                """))).hadErrorContaining("@CLibOut view requires a setter for every field, missing [y]");
    }

    @Test
    public void rejectsDuplicateStructName() {
        assertThat(compile(source("Shapes", """
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
                """))).hadErrorContaining("Duplicate @CLibStruct value 'Point'");
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
        assertThat(compile(source("NotAMarshaller", """
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
                """))).hadErrorContaining("Struct marshaller does not implement");
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
        assertThat(compile(source("NoInvoke", """
                import org.graalvm.nativeimage.c.function.CFunctionPointer;
                @CLibClass("NoInvoke")
                public class NoInvoke {
                    public interface Callback extends CFunctionPointer {
                    }

                    @CLibMethod(flags = Noexcept)
                    public static void run(Callback callback) { }
                }
                """))).hadErrorContaining("exactly one @InvokeCFunctionPointer method, found none");
    }

    @Test
    public void rejectsCallbackWithTwoInvokeMethods() {
        assertThat(compile(source("TwoInvokes", """
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
                """))).hadErrorContaining("exactly one @InvokeCFunctionPointer method, found [first, second]");
    }

    @Test
    public void rejectsCallbackWithUnmappableParameter() {
        assertThat(compile(source("HandleCallback", """
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
                """))).hadErrorContaining("is not supported in a callback signature: Callback.invoke");
    }

    /**
     * The annotated API declares plain Java enums, and native-image rejects those in an
     * {@code @InvokeCFunctionPointer} signature ("parameter types are restricted to primitive
     * types, word types and enumerations (@CEnum)"). Only stage 1 sees that, so the processor
     * has to say it first. See plans item 1 for what admitting them would take.
     */
    @Test
    public void rejectsCallbackWithJavaEnumParameter() {
        assertThat(compile(source("EnumCallback", """
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
                """))).hadErrorContaining("@InvokeCFunctionPointer parameter type 'validation.EnumCallback.Color'"
                                          + " is a Java enum, which a callback signature cannot use because GraalVM"
                                          + " only accepts @CEnum types there: Callback.invoke");
    }

    @Test
    public void rejectsCallbackAnnotationOnNonFunctionPointer() {
        assertThat(compile(source("NotAPointer", """
                @CLibClass("NotAPointer")
                public class NotAPointer {
                    @CLibCallback
                    public interface Callback {
                    }

                    @CLibMethod(flags = Noexcept)
                    public static int value() { return 1; }
                }
                """))).hadErrorContaining("@CLibCallback requires an interface extending CFunctionPointer: validation.NotAPointer.Callback");
    }

    @Test
    public void appliesTheCallbackNameOverride() throws IOException {
        assertThat(compile(source("Renamed", """
                import org.graalvm.nativeimage.c.function.CFunctionPointer;
                import org.graalvm.nativeimage.c.function.InvokeCFunctionPointer;
                @CLibClass("Renamed")
                public class Renamed {
                    @CLibCallback(name = "OnValue")
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
        assertThat(compile(source("BadName", """
                import org.graalvm.nativeimage.c.function.CFunctionPointer;
                import org.graalvm.nativeimage.c.function.InvokeCFunctionPointer;
                @CLibClass("BadName")
                public class BadName {
                    @CLibCallback(name = "on value")
                    public interface Callback extends CFunctionPointer {
                        @InvokeCFunctionPointer void invoke(int value);
                    }

                    @CLibMethod(flags = Noexcept)
                    public static void run(Callback callback) { }
                }
                """))).hadErrorContaining(
                "'on value' is not a valid C identifier");
    }

    @Test
    public void rejectsCallbackNameThatCollidesWithAClass() {
        assertThat(compile(source("Collision", """
                import org.graalvm.nativeimage.c.function.CFunctionPointer;
                import org.graalvm.nativeimage.c.function.InvokeCFunctionPointer;
                @CLibClass("Collision")
                public class Collision {
                    @CLibCallback(name = "Collision")
                    public interface Callback extends CFunctionPointer {
                        @InvokeCFunctionPointer void invoke(int value);
                    }

                    @CLibMethod(flags = Noexcept)
                    public static void run(Callback callback) { }
                }
                """))).hadErrorContaining(
                "Callback name 'Collision' collides with class Collision: validation.Collision.Callback");
    }

    @Test
    public void rejectsCallbackReturn() {
        assertThat(compile(source("CallbackReturn", """
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
                """))).hadErrorContaining(
                "a CFunctionPointer return is not supported until a use case needs it: current");
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
