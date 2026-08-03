package us.hebi.graalvm.bindings.tests.api;

import us.hebi.graalvm.bindings.annotations.CDefault;
import us.hebi.graalvm.bindings.annotations.CLibClass;
import us.hebi.graalvm.bindings.annotations.CLibComment;
import us.hebi.graalvm.bindings.annotations.CLibMethod;

import static us.hebi.graalvm.bindings.annotations.MethodFlag.*;

/**
 * Covers {@code cPrefix} and {@code useCamelCase}, the two {@code @CLibClass} options that
 * change exported symbol names. Both exist so an annotated class can match an existing
 * hand-written C ABI, which is what {@code native-sdk} uses them for.
 * <p>
 * Only the C symbols change. {@code cPrefix} replaces the library-wide prefix, including in
 * the handle typedef, and {@code useCamelCase = false} converts method names to snake case.
 * The class name is not converted, so the symbols read {@code custom_prefix_Naming_get_start_value}
 * rather than {@code custom_prefix_naming_get_start_value}. Everything else follows the usual
 * rules: the C++ namespace, the Python module and the MATLAB package all come from the library
 * name, and the wrappers keep the names they would have had without either option.
 *
 * @author Florian Enner
 */
@CLibComment("A counter exported under a custom prefix in snake case.")
@CLibClass(value = "Naming", cPrefix = "custom_prefix", useCamelCase = false)
public class TestNaming {

    @CLibMethod(constructor = true)
    @CLibComment("Creates an instance holding the given value, or zero by default.")
    public static TestNaming create(@CDefault("0") int startValue) {
        return new TestNaming(startValue);
    }

    @CLibMethod(flags = {Noexcept, Const})
    @CLibComment("An instance method whose name needs more than one word to show the conversion.")
    public int getStartValue() {
        return startValue;
    }

    @CLibMethod(flags = Noexcept)
    @CLibComment("A static method, so the conversion is pinned for both kinds of call.")
    public static int addTwoNumbers(int first, int second) {
        return first + second;
    }

    private TestNaming(int startValue) {
        this.startValue = startValue;
    }

    private final int startValue;

}
