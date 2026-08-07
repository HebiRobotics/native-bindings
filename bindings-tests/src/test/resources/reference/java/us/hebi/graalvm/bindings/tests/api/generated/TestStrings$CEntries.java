package us.hebi.graalvm.bindings.tests.api.generated;

import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CCharPointerPointer;
import org.graalvm.word.WordFactory;
import us.hebi.graalvm.bindings.runtime.pointers.CPointers;
import us.hebi.graalvm.bindings.tests.api.TestStrings;

public final class TestStrings$CEntries {
    @CEntryPoint(
            name = "__bindings_tests_Strings_echo"
    )
    public static CCharPointer bindings_tests_Strings_echo(IsolateThread _isolate,
            CCharPointer value) {
        try {
            return CPointers.toTempCharPointer(TestStrings.echo(CPointers.utf8ToJavaString(value)));
        } catch (Throwable throwable) {
            // Declared noexcept, so there is no error struct to report through
            return WordFactory.nullPointer();
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Strings_concat"
    )
    public static CCharPointer bindings_tests_Strings_concat(IsolateThread _isolate, CCharPointer a,
            CCharPointer b) {
        try {
            return CPointers.toTempCharPointer(TestStrings.concat(CPointers.utf8ToJavaString(a), CPointers.utf8ToJavaString(b)));
        } catch (Throwable throwable) {
            // Declared noexcept, so there is no error struct to report through
            return WordFactory.nullPointer();
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Strings_utf8Length"
    )
    public static long bindings_tests_Strings_utf8Length(IsolateThread _isolate,
            CCharPointer value) {
        try {
            return TestStrings.utf8Length(CPointers.utf8ToJavaString(value));
        } catch (Throwable throwable) {
            // Declared noexcept, so there is no error struct to report through
            return 0;
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Strings_utf16Length"
    )
    public static long bindings_tests_Strings_utf16Length(IsolateThread _isolate,
            CCharPointer value) {
        try {
            return TestStrings.utf16Length(CPointers.utf8ToJavaString(value));
        } catch (Throwable throwable) {
            // Declared noexcept, so there is no error struct to report through
            return 0;
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Strings_empty"
    )
    public static CCharPointer bindings_tests_Strings_empty(IsolateThread _isolate) {
        try {
            return CPointers.toTempCharPointer(TestStrings.empty());
        } catch (Throwable throwable) {
            // Declared noexcept, so there is no error struct to report through
            return WordFactory.nullPointer();
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Strings_nullString"
    )
    public static CCharPointer bindings_tests_Strings_nullString(IsolateThread _isolate) {
        try {
            return CPointers.toTempCharPointer(TestStrings.nullString());
        } catch (Throwable throwable) {
            // Declared noexcept, so there is no error struct to report through
            return WordFactory.nullPointer();
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Strings_join"
    )
    public static CCharPointer bindings_tests_Strings_join(IsolateThread _isolate,
            CCharPointerPointer values, long count, CCharPointer separator) {
        try {
            return CPointers.toTempCharPointer(TestStrings.join(values, CPointers.capSizeToInt(count), CPointers.utf8ToJavaString(separator)));
        } catch (Throwable throwable) {
            // Declared noexcept, so there is no error struct to report through
            return WordFactory.nullPointer();
        }
    }

    @CEntryPoint(
            name = "__bindings_tests_Strings_repeat"
    )
    public static CCharPointer bindings_tests_Strings_repeat(IsolateThread _isolate,
            CCharPointer value, long times) {
        try {
            return CPointers.toTempCharPointer(TestStrings.repeat(CPointers.utf8ToJavaString(value), CPointers.capSizeToInt(times)));
        } catch (Throwable throwable) {
            // Declared noexcept, so there is no error struct to report through
            return WordFactory.nullPointer();
        }
    }
}
