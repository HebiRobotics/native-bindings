package us.hebi.graalvm.bindings.tests.api.generated;

import us.hebi.graalvm.bindings.runtime.pointers.MockPointerBase;
import us.hebi.graalvm.bindings.tests.api.TestCallbacks;

/**
 * Field-backed {@link TestCallbacks.Progress} so that annotated classes can be exercised on
 * HotSpot without building a native image.
 */
public final class TestCallbacks_Progress$$Mock extends MockPointerBase implements TestCallbacks.Progress {
    private int step;

    private double fraction;

    @Override
    public int step() {
        return step;
    }

    @Override
    public void setStep(int step) {
        this.step = step;
    }

    @Override
    public double fraction() {
        return fraction;
    }

    @Override
    public void setFraction(double fraction) {
        this.fraction = fraction;
    }
}
