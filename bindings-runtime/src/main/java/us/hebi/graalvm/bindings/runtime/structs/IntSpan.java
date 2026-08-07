package us.hebi.graalvm.bindings.runtime.structs;

import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.struct.CField;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.nativeimage.c.type.CConst;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.word.PointerBase;
import us.hebi.graalvm.bindings.annotations.CLibComment;
import us.hebi.graalvm.bindings.annotations.CLibStruct;
import us.hebi.graalvm.bindings.annotations.CSizeType;
import us.hebi.graalvm.bindings.runtime.structs.include.StructDirectives;

/**
 * View of a C {@code (const int32_t*, size_t)} pair, i.e. a pointer to the by-value
 * span struct in the public ABI. A word type, so parameters cross the boundary with
 * zero allocation.
 * <p>
 * Spans passed as parameters are caller-owned and only valid for the duration of
 * the call. The buffer is never owned or freed through this type.
 *
 * @author Florian Enner
 * @since 07 Aug 2026
 */
@CLibStruct
@CLibComment("""
        A (pointer, length) view of a contiguous int32 buffer, passed and returned
        by value. Inputs are caller-owned and only read during the call. Returned
        spans are library-owned and stay valid until the calling thread's next
        library call, so they have to be copied immediately.
        """)
@CContext(StructDirectives.class)
@CStruct("IntSpan")
public interface IntSpan extends PointerBase {

    @CField("data")
    @CConst
    CIntPointer data();

    @CField("data")
    void setData(@CConst CIntPointer data);

    @CField("length")
    @CSizeType
    long length();

    @CField("length")
    void setLength(@CSizeType long length);

}
