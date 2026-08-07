package us.hebi.graalvm.bindings.tests.api;

import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.struct.AllowNarrowingCast;
import org.graalvm.nativeimage.c.struct.AllowWideningCast;
import org.graalvm.nativeimage.c.struct.CField;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.nativeimage.c.type.CLongPointer;
import org.graalvm.nativeimage.c.type.CShortPointer;
import org.graalvm.nativeimage.c.type.VoidPointer;
import org.graalvm.word.PointerBase;
import us.hebi.graalvm.bindings.annotations.CLibClass;
import us.hebi.graalvm.bindings.annotations.CLibComment;
import us.hebi.graalvm.bindings.annotations.CLibMethod;
import us.hebi.graalvm.bindings.annotations.CLibOut;
import us.hebi.graalvm.bindings.annotations.CLibStruct;

import static us.hebi.graalvm.bindings.annotations.MethodFlag.*;

/**
 * Covers a user-defined struct as an input parameter. The caller owns the struct and
 * passes it by value, so the wrappers hand over a plain aggregate rather than a handle.
 * <p>
 * The three fields are deliberately mixed: a double, an int, and a boolean that lands in
 * the layout as an int32, which is what makes the MATLAB and Python conversions
 * interesting. Every accessor exists in both directions, so the same view can carry an
 * out-parameter later without changing the interface.
 * <p>
 * All four language suites assert this table, with a current of 2.5 A:
 * <pre>
 *   voltage  state  enabled   totalPower   checkedVoltage
 *   48.0     2      true      120.0        48.0
 *   48.0     1      true      0.0          48.0
 *   48.0     2      false     0.0          throws
 * </pre>
 * <p>
 * {@code getStatus} and {@code getCheckedStatus} present the same struct the other way
 * round, as a {@code @CLibOut} return keyed by index rather than an input the caller
 * assembles:
 * <pre>
 *   index   voltage  state  enabled
 *   0       48.0     2      true
 *   1       12.5     1      false
 * </pre>
 *
 * @author Florian Enner
 */
@CLibComment("Reads fields out of a caller-owned struct.")
@CLibClass(value = "Structs", enableInstancing = false)
public class TestStructs {

    /**
     * The {@code @CContext} names the directives class that the processor generates for
     * this view, which is the shape a user writes and therefore the one worth pinning.
     */
    @CLibStruct
    @CContext(us.hebi.graalvm.bindings.tests.api.generated.TestStructs_StatusInfo$$Directives.class)
    @CStruct("StatusInfo")
    public interface StatusInfo extends PointerBase {

        @CField("voltage")
        double voltage();

        @CField("voltage")
        void setVoltage(double voltageVolts);

        @CField("state")
        int state();

        @CField("state")
        void setState(int state);

        // A boolean accessor is 1 byte while the field is an int32, so GraalVM requires
        // the explicit casts. The processor rejects bool accessors without them.
        @CField("enabled")
        @AllowNarrowingCast
        boolean enabled();

        @CField("enabled")
        @AllowWideningCast
        void setEnabled(boolean enabled);

    }

    /**
     * The field types StatusInfo leaves out: a char code unit and the typed pointers.
     * Pointer fields only exist in the C, C++ and layout mirrors, the MATLAB struct
     * and the Python dataclass skip them because they carry no lifetime story there.
     */
    @CLibStruct
    @CContext(us.hebi.graalvm.bindings.tests.api.generated.TestStructs_FieldTypes$$Directives.class)
    @CStruct("FieldTypes")
    public interface FieldTypes extends PointerBase {

        @CField("unit")
        char unit();

        @CField("unit")
        void setUnit(char unit);

        @CField("shorts")
        CShortPointer shorts();

        @CField("shorts")
        void setShorts(CShortPointer shorts);

        @CField("longs")
        CLongPointer longs();

        @CField("longs")
        void setLongs(CLongPointer longs);

        // Arbitrary caller data, the shape a callback context uses
        @CField("context")
        VoidPointer context();

        @CField("context")
        void setContext(VoidPointer context);

    }

    @CLibMethod(flags = Noexcept)
    @CLibComment("""
            Fills the out parameter from the arguments. Pins the field types StatusInfo
            leaves out: the char code unit crosses as uint16, the pointers pass through
            untouched so the caller can verify them against what it handed in.
            """)
    public static void fillFieldTypes(char unit, CShortPointer shorts, CLongPointer longs, VoidPointer context,
                                      @CLibOut FieldTypes out) {
        out.setUnit(unit);
        out.setShorts(shorts);
        out.setLongs(longs);
        out.setContext(context);
    }

    @CLibMethod(flags = Noexcept)
    @CLibComment("""
            Power draw in watts for the given current. Returns zero unless the actuator is
            enabled and in the running state (2), so all three fields decide the result.
            """)
    public static double totalPower(StatusInfo status, double currentAmps) {
        if (!status.enabled() || status.state() != STATE_RUNNING) {
            return 0;
        }
        return status.voltage() * currentAmps;
    }

    @CLibMethod
    @CLibComment(value = "Bus voltage in volts. Returns 0.0 through the ABI when it throws.",
            throwsOn = "the actuator is disabled")
    public static double checkedVoltage(StatusInfo status) {
        if (!status.enabled()) {
            throw new IllegalArgumentException("actuator is disabled, state " + status.state());
        }
        return status.voltage();
    }

    @CLibMethod(flags = Noexcept)
    @CLibComment("""
            Fills the out parameter with a canned reading for the given index: index 0
            reads (48.0, running, enabled), every other index reads (12.5, 1, disabled).
            """)
    public static void getStatus(int index, @CLibOut @CLibComment("Status reading for the given index.") StatusInfo out) {
        if (index == 0) {
            out.setVoltage(48.0);
            out.setState(STATE_RUNNING);
            out.setEnabled(true);
        } else {
            out.setVoltage(12.5);
            out.setState(1);
            out.setEnabled(false);
        }
    }

    @CLibMethod
    @CLibComment(value = "Same readings as getStatus. Every field of the out parameter reads as zero through the ABI when it throws.",
            throwsOn = "the index is negative")
    public static void getCheckedStatus(int index, @CLibOut StatusInfo out) {
        if (index < 0) {
            throw new IllegalArgumentException("index must not be negative: " + index);
        }
        getStatus(index, out);
    }

    private static final int STATE_RUNNING = 2;

}
