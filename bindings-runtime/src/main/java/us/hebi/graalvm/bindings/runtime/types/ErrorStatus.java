package us.hebi.graalvm.bindings.runtime.types;

import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.struct.CField;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.PointerBase;

/**
 * View of the error out-struct that every throwing entry point fills. A word type, so
 * it crosses the boundary with zero allocation.
 * <p>
 * The struct is caller-owned and only valid for the duration of the call. The message
 * and id point into the calling thread's scratch buffer and have to be copied before
 * that thread makes its next call, the same contract as string and span returns.
 *
 * @author Florian Enner
 * @since 05 Aug 2026
 */
@CContext(StructDirectives.class)
@CStruct("Error")
public interface ErrorStatus extends PointerBase {

    @CField("message")
    CCharPointer message();

    @CField("message")
    void setMessage(CCharPointer message);

    @CField("id")
    CCharPointer id();

    @CField("id")
    void setId(CCharPointer id);

    @CField("code")
    int code();

    @CField("code")
    void setCode(int code);

}
