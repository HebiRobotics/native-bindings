package us.hebi.graalvm.bindings.tests.api;

import us.hebi.graalvm.bindings.annotations.CDefault;
import us.hebi.graalvm.bindings.annotations.CLibClass;
import us.hebi.graalvm.bindings.annotations.CLibComment;
import us.hebi.graalvm.bindings.annotations.CLibIndex;
import us.hebi.graalvm.bindings.annotations.CLibMethod;
import us.hebi.graalvm.bindings.annotations.CSizeType;

import static us.hebi.graalvm.bindings.annotations.MethodFlag.*;

/**
 * Covers {@code @CLibIndex}, which converts indices for languages that count from one.
 * <p>
 * Java and the C ABI are zero based, so C, C++ and Python assert the raw values here.
 * MATLAB is one based and asserts the converted ones, which is the whole point of the
 * annotation: the same method reads naturally in every language without a hand written
 * overload per call site.
 * <p>
 * Compare {@link TestOverloads#echoIndex(int)}, which does the same conversion by hand
 * through a {@code matlabOverload}. That one stays as coverage for {@code postBody} and
 * {@code postCall}.
 *
 * @author Florian Enner
 */
@CLibComment("Index conversion for languages that count from one.")
@CLibClass(value = "Indices", enableInstancing = false)
public class TestIndices {

    @CLibMethod(flags = Noexcept)
    @CLibComment("""
            Returns the index unchanged. Both directions are marked, so a one-based caller
            passes a value and gets the same value back.
            """)
    public static @CLibIndex int echo(@CLibIndex int index) {
        return index;
    }

    @CLibMethod(flags = Noexcept)
    @CLibComment("Index in, plain number out, so only the input converts.")
    public static int countUpTo(@CLibIndex int index) {
        return index + 1;
    }

    @CLibMethod(flags = Noexcept)
    @CLibComment("Plain number in, index out, so only the output converts.")
    public static @CLibIndex int lastIndexOf(int count) {
        return count - 1;
    }

    @CLibMethod(flags = Noexcept)
    @CLibComment("""
            Two indices that default to the first element. The default is written zero based
            here and each one-based wrapper shifts it, so an omitted argument converts the
            same way a supplied one does.
            """)
    public static int offsetFrom(@CLibIndex @CDefault("0") int row,
                                 @CLibIndex @CDefault("0") int col) {
        return row * 10 + col;
    }

    @CLibMethod(flags = Noexcept)
    @CLibComment("""
            An index whose default is not zero, which is what catches a default that gets
            converted twice. Omitting the argument has to reach Java as 5 in every language.
            """)
    public static int fromRow(@CLibIndex @CDefault("5") int row) {
        return row;
    }

    @CLibMethod(flags = Noexcept)
    @CLibComment("A size_t index, so the size bridge and the index conversion have to compose.")
    public static @CLibIndex @CSizeType long echoSize(@CLibIndex @CSizeType long index) {
        return index;
    }

}
