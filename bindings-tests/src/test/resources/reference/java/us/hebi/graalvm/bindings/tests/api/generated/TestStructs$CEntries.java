package us.hebi.graalvm.bindings.tests.api.generated;

import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import us.hebi.graalvm.bindings.runtime.structs.CLibError;
import us.hebi.graalvm.bindings.runtime.structs.ErrorInfo;
import us.hebi.graalvm.bindings.tests.api.TestStructs;

public final class TestStructs$CEntries {
    @CEntryPoint(
            name = "__bindings_tests_Structs_totalPower"
    )
    public static double bindings_tests_Structs_totalPower(IsolateThread _isolate,
            TestStructs.StatusInfo status, double currentAmps) {
        try {
            return TestStructs.totalPower(status, currentAmps);
        } catch (Throwable throwable) {
            // Declared noexcept, so there is no error struct to report through
            return 0;
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Structs_checkedVoltage"
    )
    public static double bindings_tests_Structs_checkedVoltage(IsolateThread _isolate,
            ErrorInfo _error, TestStructs.StatusInfo status) {
        CLibError.clearStruct(_error);
        try {
            return TestStructs.checkedVoltage(status);
        } catch (Throwable throwable) {
            CLibError.fillStruct(throwable, _error, "bindings_tests");
            return 0;
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Structs_getStatus"
    )
    public static void bindings_tests_Structs_getStatus(IsolateThread _isolate, int index,
            TestStructs.StatusInfo out) {
        try {
            TestStructs.getStatus(index, out);
        } catch (Throwable throwable) {
            // Declared noexcept, so there is no error struct to report through
            out.setVoltage(0);
            out.setState(0);
            out.setEnabled(false);
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Structs_getCheckedStatus"
    )
    public static void bindings_tests_Structs_getCheckedStatus(IsolateThread _isolate,
            ErrorInfo _error, int index, TestStructs.StatusInfo out) {
        CLibError.clearStruct(_error);
        try {
            TestStructs.getCheckedStatus(index, out);
        } catch (Throwable throwable) {
            CLibError.fillStruct(throwable, _error, "bindings_tests");
            out.setVoltage(0);
            out.setState(0);
            out.setEnabled(false);
        }
    }
}
