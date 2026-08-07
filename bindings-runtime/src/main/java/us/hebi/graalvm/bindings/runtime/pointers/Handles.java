package us.hebi.graalvm.bindings.runtime.pointers;

import org.graalvm.nativeimage.ObjectHandle;
import org.graalvm.nativeimage.ObjectHandles;

/**
 * @author Florian Enner
 * @since 05 Jan 2024
 */
public class Handles {

    public static <T> ObjectHandle create(T obj) {
        return create(ObjectHandles.getGlobal(), obj);
    }

    public static <T> T get(ObjectHandle handle) {
        return get(ObjectHandles.getGlobal(), handle);
    }

    public static boolean destroy(ObjectHandle handle) {
        return destroy(ObjectHandles.getGlobal(), handle);
    }

    public static <T> ObjectHandle create(ObjectHandles handles, T obj) {
        if (obj == null) {
            throw new NullPointerException("Can't create a handle for null");
        }
        return handles.create(obj);
    }

    public static <T> T get(ObjectHandles handles, ObjectHandle handle) {
        // GraalVM complains when doing a null comparison
        return handles.get(handle);
    }

    public static boolean destroy(ObjectHandles handles, ObjectHandle handle) {
        try {
            if (get(handles, handle) instanceof AutoCloseable res) {
                res.close();
            }
            return true;
        } catch (Exception ex) {
            return false;
        } finally {
            handles.destroy(handle);
        }
    }

}
