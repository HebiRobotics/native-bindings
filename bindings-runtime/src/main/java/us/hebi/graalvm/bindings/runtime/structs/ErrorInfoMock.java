package us.hebi.graalvm.bindings.runtime.structs;

import org.graalvm.nativeimage.c.type.CCharPointer;
import us.hebi.graalvm.bindings.runtime.pointers.MockPointerBase;

/**
 * Field-backed {@link ErrorInfo} so that annotated classes can be exercised
 * on HotSpot without building a native image.
 *
 * @author Florian Enner
 * @since 05 Aug 2026
 */
public class ErrorInfoMock extends MockPointerBase implements ErrorInfo {

    @Override
    public CCharPointer message() {
        return message;
    }

    @Override
    public void setMessage(CCharPointer message) {
        this.message = message;
    }

    @Override
    public CCharPointer id() {
        return id;
    }

    @Override
    public void setId(CCharPointer id) {
        this.id = id;
    }

    @Override
    public int code() {
        return code;
    }

    @Override
    public void setCode(int code) {
        this.code = code;
    }

    private CCharPointer message;
    private CCharPointer id;
    private int code;

}
