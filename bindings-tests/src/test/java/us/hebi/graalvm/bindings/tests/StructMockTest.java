package us.hebi.graalvm.bindings.tests;

import org.junit.Test;
import us.hebi.graalvm.bindings.tests.api.TestStructs;
import us.hebi.graalvm.bindings.tests.api.generated.TestStructs_StatusInfo$$Mock;

import static org.junit.Assert.*;

/**
 * Runs the struct input and out-parameter methods on HotSpot, where there is no native
 * struct and the generated mock backs the view with plain fields. The values are the
 * same table the four language suites assert against the compiled library.
 *
 * @author Florian Enner
 */
public class StructMockTest {

    @Test
    public void totalPowerReadsEveryField() {
        assertEquals(120.0, TestStructs.totalPower(status(48.0, 2, true), 2.5), 0.0);

        // state 1 is not the running state
        assertEquals(0.0, TestStructs.totalPower(status(48.0, 1, true), 2.5), 0.0);
        assertEquals(0.0, TestStructs.totalPower(status(48.0, 2, false), 2.5), 0.0);
    }

    @Test
    public void checkedVoltageIgnoresState() {
        assertEquals(48.0, TestStructs.checkedVoltage(status(48.0, 2, true)), 0.0);
        assertEquals(48.0, TestStructs.checkedVoltage(status(48.0, 1, true)), 0.0);
    }

    @Test
    public void checkedVoltageThrowsWhenDisabled() {
        try {
            TestStructs.checkedVoltage(status(48.0, 2, false));
            fail("Expected a disabled actuator to throw");
        } catch (IllegalArgumentException iae) {
            assertTrue(iae.getMessage(), iae.getMessage().contains("disabled"));
        }
    }

    @Test
    public void getStatusFillsTheOutParameter() {
        TestStructs_StatusInfo$$Mock out = new TestStructs_StatusInfo$$Mock();

        TestStructs.getStatus(0, out);
        assertEquals(48.0, out.voltage(), 0.0);
        assertEquals(2, out.state());
        assertTrue(out.enabled());

        TestStructs.getStatus(1, out);
        assertEquals(12.5, out.voltage(), 0.0);
        assertEquals(1, out.state());
        assertFalse(out.enabled());
    }

    @Test
    public void getCheckedStatusFillsTheOutParameter() {
        TestStructs_StatusInfo$$Mock out = new TestStructs_StatusInfo$$Mock();

        TestStructs.getCheckedStatus(0, out);
        assertEquals(48.0, out.voltage(), 0.0);
        assertEquals(2, out.state());
        assertTrue(out.enabled());
    }

    @Test
    public void getCheckedStatusThrowsOnNegativeIndex() {
        // The entry point's on-error zeroing of the out parameter is generated behavior,
        // not something the plain Java method under test performs, so it is not asserted here.
        TestStructs_StatusInfo$$Mock out = new TestStructs_StatusInfo$$Mock();
        try {
            TestStructs.getCheckedStatus(-1, out);
            fail("Expected a negative index to throw");
        } catch (IllegalArgumentException iae) {
            assertTrue(iae.getMessage(), iae.getMessage().contains("negative"));
        }
    }

    private static TestStructs.StatusInfo status(double voltageVolts, int state, boolean enabled) {
        TestStructs_StatusInfo$$Mock status = new TestStructs_StatusInfo$$Mock();
        status.setVoltage(voltageVolts);
        status.setState(state);
        status.setEnabled(enabled);
        return status;
    }

}
