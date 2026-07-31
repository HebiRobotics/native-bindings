package us.hebi.graalvm.bindings.tests.api;

import us.hebi.graalvm.bindings.annotations.CDefault;
import us.hebi.graalvm.bindings.annotations.CLibClass;
import us.hebi.graalvm.bindings.annotations.CLibComment;
import us.hebi.graalvm.bindings.annotations.CLibMethod;

import static us.hebi.graalvm.bindings.annotations.MethodFlag.*;

/**
 * Covers the parts of the API surface that exist only in the generated wrappers:
 * default parameter values and the per-language overloads.
 * <p>
 * There is exactly one C symbol per method here. Everything else is wrapper sugar,
 * which is why the reference files are the only place these are fully checked.
 *
 * @author Florian Enner
 */
@CLibComment("Default arguments and language specific convenience overloads.")
@CLibClass(value = "Overloads", enableInstancing = false)
public class TestOverloads {

    @CLibMethod(flags = Noexcept)
    @CLibComment("Both parameters have defaults, so wrappers may call this with no arguments.")
    public static int scale(@CDefault("1") int value, @CDefault("10") int factor) {
        return value * factor;
    }

    @CLibMethod(flags = Noexcept)
    @CLibComment("A trailing default only, which is the common case.")
    public static String greet(String name, @CDefault("\"Hello\"") String greeting) {
        return greeting + ", " + name;
    }

    @CLibMethod(
            flags = Noexcept,
            // The whole prologue goes in 'body'. There is no postBody here on purpose:
            // the C++ generator does not emit that field, so anything placed there is
            // silently dropped. See the known gaps section of the README.
            cppOverloads = @CLibMethod.MethodOverload(
                    parameters = "const std::vector<int>& values, int factor",
                    body = "int sum = 0; for (int v : values) { sum += v; }",
                    call = "sum, factor"
            ),
            // The receiver (self or cls) is prepended by the generator
            pythonOverload = @CLibMethod.MethodOverload(
                    parameters = "values, factor = 1",
                    body = "total = sum(values)",
                    call = "total, factor"
            ),
            matlabOverload = @CLibMethod.MethodOverload(
                    parameters = "values, factor",
                    body = "if nargin < 2, factor = 1; end",
                    call = "sum(values), factor"
            )
    )
    @CLibComment("Takes a pre-summed total. Each language gets a wrapper that sums a collection for you.")
    public static int total(int sum, int factor) {
        return sum * factor;
    }

    @CLibMethod(flags = Noexcept)
    @CLibComment("""
            A boolean default and a double default in scientific notation. Each wrapper has
            to render both in its own syntax, which is where they tend to go wrong.
            """)
    public static double gain(@CDefault("false") boolean invert, @CDefault("1e-3") double scale) {
        return invert ? -scale : scale;
    }

    @CLibMethod(
            flags = Noexcept,
            // postBody is emitted right before the call, postCall right after it. Neither
            // needs a 'parameters' entry, because the signature stays the same. The C++
            // generator emits neither, so this only overloads Python and MATLAB.
            pythonOverload = @CLibMethod.MethodOverload(
                    postBody = "index = max(index, 0)"
            ),
            matlabOverload = @CLibMethod.MethodOverload(
                    postBody = "index = index - 1;",
                    postCall = "result = result + 1;"
            )
    )
    @CLibComment("""
            Returns the index unchanged. MATLAB converts to zero based indexing on the way
            in and back to one based on the way out, so it sees the value it passed in.
            Python clamps negatives instead, which matches C for every valid index.
            """)
    public static int echoIndex(int index) {
        return index;
    }

    @CLibMethod(
            flags = Noexcept,
            cppBody = "return 21 * multiplier;"
    )
    @CLibComment("""
            The C++ wrapper body is hand written and never calls into the library, so
            this is the one method where the C++ result may legitimately differ from
            the others. Kept deliberately obvious.
            """)
    public static int answer(int multiplier) {
        return 42 * multiplier;
    }

    @CLibMethod(flags = Noexcept, name = "renamed")
    @CLibComment("Declared as originalName in Java but exported as renamed.")
    public static int originalName() {
        return 7;
    }

    @CLibMethod(ignore = true)
    @CLibComment("Must not appear anywhere in the generated output.")
    public static int hidden() {
        return -1;
    }

}
