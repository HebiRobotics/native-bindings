package us.hebi.graalvm.bindings.tests.api.generated;

import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import us.hebi.graalvm.bindings.runtime.pointers.CPointers;
import us.hebi.graalvm.bindings.tests.api.TestIndices;

public final class TestIndices$CEntries {
    @CEntryPoint(
            name = "__bindings_tests_Indices_echo"
    )
    public static int bindings_tests_Indices_echo(IsolateThread _isolate, int index) {
        try {
            return TestIndices.echo(index);
        } catch (Throwable throwable) {
            // Declared noexcept, so there is no error struct to report through
            return 0;
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Indices_countUpTo"
    )
    public static int bindings_tests_Indices_countUpTo(IsolateThread _isolate, int index) {
        try {
            return TestIndices.countUpTo(index);
        } catch (Throwable throwable) {
            // Declared noexcept, so there is no error struct to report through
            return 0;
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Indices_lastIndexOf"
    )
    public static int bindings_tests_Indices_lastIndexOf(IsolateThread _isolate, int count) {
        try {
            return TestIndices.lastIndexOf(count);
        } catch (Throwable throwable) {
            // Declared noexcept, so there is no error struct to report through
            return 0;
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Indices_offsetFrom"
    )
    public static int bindings_tests_Indices_offsetFrom(IsolateThread _isolate, int row, int col) {
        try {
            return TestIndices.offsetFrom(row, col);
        } catch (Throwable throwable) {
            // Declared noexcept, so there is no error struct to report through
            return 0;
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Indices_fromRow"
    )
    public static int bindings_tests_Indices_fromRow(IsolateThread _isolate, int row) {
        try {
            return TestIndices.fromRow(row);
        } catch (Throwable throwable) {
            // Declared noexcept, so there is no error struct to report through
            return 0;
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Indices_echoSize"
    )
    public static long bindings_tests_Indices_echoSize(IsolateThread _isolate, long index) {
        try {
            return TestIndices.echoSize(CPointers.capSizeToLong(index));
        } catch (Throwable throwable) {
            // Declared noexcept, so there is no error struct to report through
            return 0;
        }
    }
}
