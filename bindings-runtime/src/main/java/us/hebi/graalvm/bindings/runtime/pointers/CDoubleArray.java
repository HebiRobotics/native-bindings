package us.hebi.graalvm.bindings.runtime.pointers;

import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CDoublePointer;

/**
 * RAII wrapper around an unmanaged double array. Not thread safe: a single
 * instance must not be resized while another thread reads or writes it.
 *
 * @author Florian Enner
 * @since 03 Nov 2025
 */
public class CDoubleArray implements AutoCloseable {

    public CDoubleArray resizeTo(int size) {
        if (size < 0) {
            throw new IllegalArgumentException("Size must be positive");
        }
        if (length != size) {
            close();
            if (size > 0) {
                pointer = UnmanagedMemory.malloc(size * SizeOf.get(CDoublePointer.class));
            }
            length = size;
        }
        return this;
    }

    public void copyTo(double[] target) {
        for (int i = 0; i < length; i++) {
            target[i] = pointer.read(i);
        }
    }

    public void copyFrom(double[] source) {
        for (int i = 0; i < length; i++) {
            pointer.write(i, source[i]);
        }
    }

    @Override
    public void close() {
        if (length != 0) {
            UnmanagedMemory.free(pointer);
        }
        length = 0;
    }

    public int getLength() {
        return length;
    }

    public CDoublePointer getPointer() {
        return pointer;
    }

    int length = 0;

    CDoublePointer pointer;

}
