package us.hebi.graalvm.bindings.runtime.views;

import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CDoublePointer;
import org.graalvm.nativeimage.c.type.CFloatPointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordFactory;
import us.hebi.glue.Buffer6;
import us.hebi.glue.DirectBuffer6;
import us.hebi.graalvm.bindings.runtime.pointers.CBytePointer;
import us.hebi.graalvm.bindings.runtime.pointers.CBytePointerMock;
import us.hebi.graalvm.bindings.runtime.pointers.CCharPointerMock;
import us.hebi.graalvm.bindings.runtime.pointers.CDoublePointerMock;
import us.hebi.graalvm.bindings.runtime.pointers.CFloatPointerMock;
import us.hebi.graalvm.bindings.runtime.pointers.CIntPointerMock;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * Base for buffers that are exposed via the C ABI. They typically
 * are thread-local and exist only for one call.
 */
public abstract class BufferView<Ptr extends PointerBase> {

    public BufferView() {
        this(64);
    }

    public BufferView(int minSize) {
        MIN_SIZE = minSize;
    }

    /**
     * @return true if the buffer has changed
     */
    protected boolean ensureSpace(int numBytes) {
        if (bytes == null || bytes.capacity() < numBytes) {
            bytes = ByteBuffer.allocateDirect(Math.max(numBytes, MIN_SIZE));
            bytes.order(ByteOrder.nativeOrder());
            address = DirectBuffer6.getAddress(bytes);
            return true;
        }
        return false;
    }

    protected boolean isNull() {
        return isNull || address == 0;
    }

    public long getAddress() {
        return isNull ? 0 : address;
    }

    public Ptr getPointer() {
        if (ImageInfo.inImageRuntimeCode()) {
            return isNull() ? WordFactory.nullPointer() : WordFactory.pointer(address);
        } else {
            return getJvmPointer();
        }
    }

    /**
     * Overridable for tests on the JVM that rely on isNull() being available on a non-null object.
     * A direct buffer has no accessible backing array, so the implementations copy. Nothing
     * outside a test reaches this path.
     */
    protected abstract Ptr getJvmPointer();

    public static class CChar extends BufferView<CCharPointer> {

        @Override
        final protected CCharPointer getJvmPointer() {
            return new CCharPointerMock(isNull() ? null : toArray(), 0);
        }

        private byte[] toArray() {
            byte[] array = new byte[bytes.position()];
            Buffer6.rewind(bytes);
            bytes.get(array);
            return array;
        }

    }

    public static class CByte extends BufferView<CBytePointer> {

        @Override
        final protected CBytePointer getJvmPointer() {
            return new CBytePointerMock(isNull() ? null : toArray(), 0);
        }

        private byte[] toArray() {
            byte[] array = new byte[bytes.position()];
            Buffer6.rewind(bytes);
            bytes.get(array);
            return array;
        }

    }

    public static class CInt extends BufferView<CIntPointer> {

        @Override
        final protected CIntPointer getJvmPointer() {
            return new CIntPointerMock(isNull() ? null : toArray(), 0);
        }

        private int[] toArray() {
            int[] array = new int[ints.position()];
            Buffer6.rewind(ints);
            ints.get(array);
            return array;
        }

        @Override
        protected boolean ensureSpace(int numBytes) {
            if (super.ensureSpace(numBytes)) {
                ints = bytes.asIntBuffer();
                return true;
            }
            return false;
        }

        protected IntBuffer ints;

    }

    public static class CFloat extends BufferView<CFloatPointer> {

        @Override
        final protected CFloatPointer getJvmPointer() {
            return new CFloatPointerMock(isNull() ? null : toArray(), 0);
        }

        private float[] toArray() {
            float[] array = new float[floats.position()];
            Buffer6.rewind(floats);
            floats.get(array);
            return array;
        }

        @Override
        protected boolean ensureSpace(int numBytes) {
            if (super.ensureSpace(numBytes)) {
                floats = bytes.asFloatBuffer();
                return true;
            }
            return false;
        }

        protected FloatBuffer floats;

    }

    public static class CDouble extends BufferView<CDoublePointer> {

        @Override
        final protected CDoublePointer getJvmPointer() {
            return new CDoublePointerMock(isNull() ? null : toArray(), 0);
        }

        private double[] toArray() {
            double[] array = new double[doubles.position()];
            Buffer6.rewind(doubles);
            doubles.get(array);
            return array;
        }

        @Override
        protected boolean ensureSpace(int numBytes) {
            if (super.ensureSpace(numBytes)) {
                doubles = bytes.asDoubleBuffer();
                return true;
            }
            return false;
        }

        protected DoubleBuffer doubles;

    }

    /**
     * Sets the internal reference to null if the input is null.
     *
     * @return true if the input object was not null
     */
    protected boolean clearAndCheckNotNull(Object object) {
        return !(isNull = (object == null));
    }

    private boolean isNull = true;
    private long address;
    protected ByteBuffer bytes;
    final int MIN_SIZE;

}
