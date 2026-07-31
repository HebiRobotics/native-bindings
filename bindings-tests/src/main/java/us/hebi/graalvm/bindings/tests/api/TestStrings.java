package us.hebi.graalvm.bindings.tests.api;

import org.graalvm.nativeimage.c.type.CCharPointerPointer;
import org.graalvm.nativeimage.c.type.CConst;
import us.hebi.graalvm.bindings.annotations.CLibClass;
import us.hebi.graalvm.bindings.annotations.CLibComment;
import us.hebi.graalvm.bindings.annotations.CLibMethod;
import us.hebi.graalvm.bindings.annotations.CSizeType;
import us.hebi.graalvm.bindings.runtime.types.CTypes;

import java.nio.charset.StandardCharsets;

import static us.hebi.graalvm.bindings.annotations.MethodFlag.*;

/**
 * Covers String marshalling in both directions, plus char** arrays.
 * <p>
 * Returned strings live in a per-thread scratch buffer that is overwritten by the
 * next call on the same thread, so {@link #concat} deliberately returns a value
 * long enough to force the buffer to grow past its initial size.
 *
 * @author Florian Enner
 */
@CLibComment("String conversion, including non-ASCII round trips.")
@CLibClass(value = "Strings", enableInstancing = false)
public class TestStrings {

    @CLibMethod(flags = Noexcept)
    @CLibComment("Returns the input unchanged. Round trips UTF-8 through Java's UTF-16.")
    public static String echo(String value) {
        return value;
    }

    @CLibMethod(flags = Noexcept)
    public static String concat(String a, String b) {
        return a + b;
    }

    @CLibMethod(flags = Noexcept)
    @CLibComment("Length in UTF-8 bytes, which differs from the Java String length for non-ASCII input.")
    public static @CSizeType long utf8Length(String value) {
        return value == null ? 0 : value.getBytes(StandardCharsets.UTF_8).length;
    }

    @CLibMethod(flags = Noexcept)
    @CLibComment("Number of UTF-16 code units, i.e. the Java String length.")
    public static @CSizeType long utf16Length(String value) {
        return value == null ? 0 : value.length();
    }

    @CLibMethod(flags = Noexcept)
    @CLibComment("Returns an empty string, which must not be confused with a null pointer.")
    public static String empty() {
        return "";
    }

    @CLibMethod(flags = Noexcept)
    @CLibComment("Returns a null pointer rather than an empty string.")
    public static String nullString() {
        return null;
    }

    @CLibMethod(flags = Noexcept)
    @CLibComment("Joins a char* array with a separator. Checks const char** parameter handling.")
    public static String join(@CConst CCharPointerPointer values,
                             @CLibComment("number of entries in values") @CSizeType int count,
                             String separator) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) sb.append(separator);
            sb.append(CTypes.utf8ToJavaString(values.read(i)));
        }
        return sb.toString();
    }

    @CLibMethod(flags = Noexcept)
    @CLibComment("Returns a string longer than the initial scratch buffer, forcing it to grow.")
    public static String repeat(String value, @CSizeType int times) {
        return value.repeat(times);
    }

}
