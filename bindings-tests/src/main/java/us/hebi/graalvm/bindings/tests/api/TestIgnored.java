package us.hebi.graalvm.bindings.tests.api;

import us.hebi.graalvm.bindings.annotations.CLibClass;
import us.hebi.graalvm.bindings.annotations.CLibComment;
import us.hebi.graalvm.bindings.annotations.CLibMethod;

import static us.hebi.graalvm.bindings.annotations.MethodFlag.*;

/**
 * Covers the class level ignore, the counterpart to the method level one in
 * {@link TestOverloads#hidden()}.
 * <p>
 * Nothing in here may reach the generated output. The reference file check is what
 * enforces that, because a regression would show up as a new set of files.
 *
 * @author Florian Enner
 */
@CLibComment("Must not appear anywhere in the generated output.")
@CLibClass(value = "Ignored", ignore = true)
public class TestIgnored {

    @CLibMethod(flags = Noexcept)
    public static int shouldNotExist() {
        return -1;
    }

}
