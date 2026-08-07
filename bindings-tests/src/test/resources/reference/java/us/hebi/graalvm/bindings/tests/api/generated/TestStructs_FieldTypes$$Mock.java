package us.hebi.graalvm.bindings.tests.api.generated;

import org.graalvm.nativeimage.c.type.CLongPointer;
import org.graalvm.nativeimage.c.type.CShortPointer;
import org.graalvm.nativeimage.c.type.VoidPointer;
import us.hebi.graalvm.bindings.runtime.pointers.MockPointerBase;
import us.hebi.graalvm.bindings.tests.api.TestStructs;

/**
 * Field-backed {@link TestStructs.FieldTypes} so that annotated classes can be exercised on
 * HotSpot without building a native image.
 */
public final class TestStructs_FieldTypes$$Mock extends MockPointerBase implements TestStructs.FieldTypes {
    private char unit;

    private CShortPointer shorts;

    private CLongPointer longs;

    private VoidPointer context;

    @Override
    public char unit() {
        return unit;
    }

    @Override
    public void setUnit(char unit) {
        this.unit = unit;
    }

    @Override
    public CShortPointer shorts() {
        return shorts;
    }

    @Override
    public void setShorts(CShortPointer shorts) {
        this.shorts = shorts;
    }

    @Override
    public CLongPointer longs() {
        return longs;
    }

    @Override
    public void setLongs(CLongPointer longs) {
        this.longs = longs;
    }

    @Override
    public VoidPointer context() {
        return context;
    }

    @Override
    public void setContext(VoidPointer context) {
        this.context = context;
    }
}
