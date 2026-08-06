package us.hebi.graalvm.bindings.runtime.types;

import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.struct.CField;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.nativeimage.c.type.CDoublePointer;
import org.graalvm.word.PointerBase;
import us.hebi.graalvm.bindings.annotations.CLibComment;
import us.hebi.graalvm.bindings.annotations.CLibStruct;
import us.hebi.graalvm.bindings.annotations.CSizeType;

/**
 * View of a C {@code (const double*, size_t)} pair, i.e. a pointer to the by-value
 * span struct in the public ABI. A word type, so parameters cross the boundary with
 * zero allocation.
 * <p>
 * Spans passed as parameters are caller-owned and only valid for the duration of
 * the call. The buffer is never owned or freed through this type.
 *
 * @author Florian Enner
 * @since 01 Aug 2026
 */
@CLibStruct
@CLibComment("""
        A (pointer, length) view of a contiguous double buffer, passed and returned
        by value. Inputs are caller-owned and only read during the call. Returned
        spans are library-owned and stay valid until the calling thread's next
        library call, so they have to be copied immediately.
        """)
@CContext(StructDirectives.class)
@CStruct("DoubleSpan")
public interface DoubleSpan extends PointerBase {

    @CField("data")
    CDoublePointer data();

    @CField("data")
    void setData(CDoublePointer data);

    @CField("length")
    @CSizeType
    long length();

    @CField("length")
    void setLength(@CSizeType long length);

    // No convenience methods here on purpose: native-image rejects any method on a
    // @CStruct interface that is not an annotated accessor. Helpers live in Spans.

}
