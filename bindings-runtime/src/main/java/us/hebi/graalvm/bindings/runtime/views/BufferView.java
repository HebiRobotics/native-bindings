package us.hebi.graalvm.bindings.runtime.views;

import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CDoublePointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordFactory;
import us.hebi.glue.DirectBuffer6;
import us.hebi.graalvm.bindings.runtime.types.CCharPointerMock;
import us.hebi.graalvm.bindings.runtime.types.CDoublePointerMock;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;

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
     * Overridable for tests on the JVM that rely on isNull() being available on a non-null object
     */
    protected abstract Ptr getJvmPointer();

    public static class CChar extends BufferView<CCharPointer> {

        @Override
        final protected CCharPointer getJvmPointer() {
            var array = isNull() ? null : bytes.array();
            return new CCharPointerMock(array, 0);
        }

    }

    public static class CDouble extends BufferView<CDoublePointer> {

        @Override
        final protected CDoublePointer getJvmPointer() {
            var array = isNull() ? null : doubles.array();
            return new CDoublePointerMock(array, 0);
        }

        @Override
        protected boolean ensureSpace(int numElements) {
            if (super.ensureSpace(numElements)) {
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
