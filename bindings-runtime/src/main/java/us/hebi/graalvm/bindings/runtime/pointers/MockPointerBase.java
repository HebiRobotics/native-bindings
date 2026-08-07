package us.hebi.graalvm.bindings.runtime.pointers;

import org.graalvm.word.ComparableWord;
import org.graalvm.word.PointerBase;

/**
 * @author Florian Enner
 * @since 02 Aug 2024
 */
public abstract class MockPointerBase implements PointerBase {

    // Override for classes that need null check testing
    @Override
    public boolean isNull() {
        return false;
    }

    @Override
    public boolean isNonNull() {
        return !isNull();
    }

    @Override
    public boolean equal(ComparableWord val) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean notEqual(ComparableWord val) {
        throw new UnsupportedOperationException();
    }

    @Override
    public long rawValue() {
        throw new UnsupportedOperationException();
    }

}
