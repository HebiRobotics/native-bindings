package us.hebi.graalvm.bindings.runtime.types;

import org.graalvm.word.ComparableWord;
import org.graalvm.word.PointerBase;

/**
 * @author Florian Enner
 * @since 02 Aug 2024
 */
public class MockPointerBase implements PointerBase {
    @Override
    public boolean isNull() {
        return false;
    }

    @Override
    public boolean isNonNull() {
        return true;
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
