package us.hebi.graalvm.bindings.tests.api.generated;

import us.hebi.graalvm.bindings.runtime.pointers.MockPointerBase;
import us.hebi.graalvm.bindings.tests.api.TestStructs;

/**
 * Field-backed {@link TestStructs.StatusInfo} so that annotated classes can be exercised on
 * HotSpot without building a native image.
 */
public final class TestStructs_StatusInfo$$Mock extends MockPointerBase implements TestStructs.StatusInfo {
    private double voltage;

    private int state;

    private boolean enabled;

    @Override
    public double voltage() {
        return voltage;
    }

    @Override
    public void setVoltage(double voltage) {
        this.voltage = voltage;
    }

    @Override
    public int state() {
        return state;
    }

    @Override
    public void setState(int state) {
        this.state = state;
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
