package us.hebi.graalvm.bindings.generator.bindings;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeName;
import org.graalvm.nativeimage.ObjectHandle;
import org.graalvm.nativeimage.c.type.*;
import us.hebi.graalvm.bindings.generator.bindings.marshallers.StructMarshallers;
import us.hebi.graalvm.bindings.model.DataType;
import us.hebi.graalvm.bindings.model.TypeDescription;
import us.hebi.graalvm.bindings.runtime.pointers.CBytePointer;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Florian Enner
 * @since 20 Feb 2026
 */
public final class TypeMap {

    private TypeMap() {
    }

    public static boolean isVoid(DataType type) {
        return type == DataType.VOID;
    }

    public static boolean isPointerType(DataType type) {
        return switch (type) {
            case OBJECT_HANDLE,
                 VOID_POINTER,
                 CHAR_POINTER,
                 CHAR_POINTER_POINTER,
                 UINT8_POINTER,
                 INT16_POINTER,
                 INT32_POINTER,
                 INT64_POINTER,
                 FLOAT32_POINTER,
                 FLOAT64_POINTER -> true;
            default -> false;
        };
    }

    public static String toExternalCpp(DataType type) {
        return switch (type) {
            case BOOL -> "bool";
            default -> toExternalC(type);
        };
    }

    public static String toExternalC(TypeDescription type, boolean ptrSuffix) {
        return switch (type.getDataType()) {
            case OBJECT_HANDLE -> type.getCPrefix() + type.getTypeName() + (ptrSuffix ? C_PTR_SUFFIX : "");
            case ENUM, CALLBACK -> type.getCPrefix() + type.getTypeName();
            case STRUCT -> toStructCName(type.getCPrefix(), type.getTypeName());
            default -> toExternalC(type.getDataType());
        };
    }

    /**
     * Public C name of a struct. Types are {@code <prefix>_UpperCamel} like the handle
     * typedefs and the class segment in function names.
     */
    public static String toStructCName(String cPrefix, String name) {
        return cPrefix + name;
    }

    /**
     * @return C type of a struct field. Fixed width on purpose: the public header and the
     * native-image probe header declare the same layout and must not disagree on a 32 bit target.
     * Constness comes from the declaration, i.e. a GraalVM {@code @CConst} on the accessor. It is
     * a statement to the external consumer only.
     */
    public static String toStructFieldC(DataType type, boolean isConst) {
        return (isConst ? "const " : "") + toStructFieldC(type);
    }

    private static String toStructFieldC(DataType type) {
        return switch (type) {
            case FLOAT64 -> "double";
            case FLOAT32 -> "float";
            case INT64 -> "int64_t";
            case INT32 -> "int32_t";
            case INT16 -> "int16_t";
            case INT8 -> "int8_t";
            case CHAR16 -> "uint16_t"; // a UTF-16 code unit, same as the scalar parameter face
            case BOOL -> "int32_t"; // native-image maps a Java boolean to a 4 byte int
            case SIZE_T -> "size_t"; // the exception to the fixed width rule, both headers say size_t

            case FLOAT64_POINTER -> "double*";
            case FLOAT32_POINTER -> "float*";
            case INT64_POINTER -> "int64_t*";
            case INT32_POINTER -> "int32_t*";
            case INT16_POINTER -> "int16_t*";
            case UINT8_POINTER -> "uint8_t*";
            case CHAR_POINTER -> "char*";
            case VOID_POINTER -> "void*";

            default -> throw new IllegalArgumentException("Unsupported struct field type: " + type);
        };
    }

    /**
     * @return ctypes type of a struct field. Fixed width like {@link #toStructFieldC}, because
     * the mirror Structure has to reproduce the C layout exactly.
     */
    public static String toStructFieldCPython(DataType type) {
        return switch (type) {
            case FLOAT64 -> "c_double";
            case FLOAT32 -> "c_float";
            case INT64 -> "c_int64";
            case INT32, BOOL -> "c_int32";
            case INT16 -> "c_int16";
            case INT8 -> "c_int8";
            case CHAR16 -> "c_uint16";
            case SIZE_T -> "c_size_t";
            case FLOAT64_POINTER -> "POINTER(c_double)";
            case FLOAT32_POINTER -> "POINTER(c_float)";
            case INT64_POINTER -> "POINTER(c_int64)";
            case INT32_POINTER -> "POINTER(c_int32)";
            case INT16_POINTER -> "POINTER(c_int16)";
            case UINT8_POINTER -> "POINTER(c_uint8)"; // binary data must not auto-convert like c_char_p
            case CHAR_POINTER -> "c_char_p";
            case VOID_POINTER -> "c_void_p";
            default -> throw new IllegalArgumentException("Unsupported struct field type: " + type);
        };
    }

    /**
     * @return Python type of a struct field as the dataclass declares it. Unlike the layout,
     * the public face is idiomatic, so a boolean field stays a bool.
     */
    public static String toStructFieldPythonHint(DataType type) {
        return switch (type) {
            case FLOAT64, FLOAT32 -> "float";
            case INT64, INT32, INT16, INT8, CHAR16, SIZE_T -> "int";
            case BOOL -> "bool";
            default -> throw new IllegalArgumentException("Unsupported struct field type: " + type);
        };
    }

    public static final String C_PTR_SUFFIX = "Ptr";

    /**
     * @return C type as built into the public shared library
     */
    public static String toExternalC(DataType type) {
        return switch (type) {
            case SIZE_T -> "size_t";
            case UINT8_POINTER -> "uint8_t*"; // internally char*, the shim casts
            default -> toInternalC(type);
        };
    }

    /**
     * @return C type as used by GraalVM
     */
    public static String toInternalC(DataType type) {
        return switch (type) {
            case VOID -> "void";
            case BOOL -> "int";               // Java boolean maps to C int
            case CHAR16 -> "unsigned short";  // Java char (unsigned 16) -> C short
            case INT8 -> "char";              // Java byte (signed 8) -> C char
            case INT16 -> "short";            // Java short -> C short
            case INT32 -> "int";              // Java int -> C int
            case INT64 -> "long long";        // Java long -> C long long int
            case FLOAT32 -> "float";
            case FLOAT64 -> "double";

            // Platform dependent (size_t)
            case SIZE_T -> "long long";       // not-supported, so we always use long for the boundary

            // Pointers
            case VOID_POINTER -> "void*";
            case CHAR_POINTER -> "char*";
            case CHAR_POINTER_POINTER -> "char**";
            case UINT8_POINTER -> "char*";      // binary data, distinct from CHAR_POINTER only in the model
            case INT16_POINTER -> "short*";
            case INT32_POINTER -> "int*";
            case INT64_POINTER -> "long long int*";
            case FLOAT32_POINTER -> "float*";
            case FLOAT64_POINTER -> "double*";

            // Custom / Opaque
            case ENUM -> "int";               // @CEnum maps to int
            case OBJECT_HANDLE -> "void*";    // technically void*

            // Structs only cross the public ABI by value. The internal entry point sees
            // them as struct pointers, so they never have an internal name. Callbacks are
            // always named by their typedef.
            case STRUCT, CALLBACK, UNSPECIFIED ->
                    throw new IllegalArgumentException("Can't determine C type for: " + type);
        };
    }

    public static String toCPython(TypeDescription type) {
        return switch (type.getDataType()) {
            // Primitives
            case VOID -> "None";
            case BOOL -> "c_int";       // Java boolean -> C int
            case CHAR16 -> "c_short";   // Java char (16-bit) -> C short
            case INT8 -> "c_byte";      // Java byte (8-bit) -> C char
            case INT16 -> "c_short";    // Java short -> C short
            case INT32 -> "c_int";      // Java int -> C int
            case INT64 -> "c_longlong"; // Java long -> C long long (8-byte)
            case FLOAT32 -> "c_float";
            case FLOAT64 -> "c_double";

            // Platform dependent (size_t)
            case SIZE_T -> "c_size_t";

            // Pointers
            case VOID_POINTER -> "c_void_p";
            case CHAR_POINTER -> "c_char_p";
            case CHAR_POINTER_POINTER -> "POINTER(c_char_p)";
            case UINT8_POINTER -> "POINTER(c_uint8)";
            case INT16_POINTER -> "POINTER(c_short)";
            case INT32_POINTER -> "POINTER(c_int)";
            case INT64_POINTER -> "POINTER(c_longlong)";
            case FLOAT32_POINTER -> "POINTER(c_float)";
            case FLOAT64_POINTER -> "POINTER(c_double)";

            // Meta / Custom
            case CALLBACK -> type.getTypeName(); // the CFUNCTYPE prototype declared next to the typedefs
            case ENUM -> "c_int32";             // @CEnum maps to int
            case OBJECT_HANDLE -> "c_void_p";   // Opaque handles

            case STRUCT -> toStructMirrorCPython(type.getTypeName());

            case UNSPECIFIED -> throw new IllegalArgumentException("Unknown data type: " + type);
        };
    }

    /**
     * @return name of the ctypes Structure mirroring the C layout, which sits next to the
     * public dataclass of the same name
     */
    public static String toStructMirrorCPython(String structName) {
        return "_" + structName;
    }

    public static String toPythonTypeHint(TypeDescription desc) {
        return switch (desc.getDataType()) {
            case STRUCT -> StructMarshallers.resolve(desc)
                    .flatMap(StructMarshaller::python)
                    .map(StructMarshaller.PyStructMarshaller::typeHint)
                    .orElseGet(desc::getTypeName);
            case OBJECT_HANDLE, ENUM, CALLBACK -> desc.getTypeName();
            case VOID -> "None";
            case BOOL -> "bool";

            // Map all integer widths to Python 'int'
            case INT8, INT16, INT32, INT64, SIZE_T -> "int";

            // Map floating point widths to Python 'float'
            case FLOAT32, FLOAT64 -> "float";

            // Map string pointers to 'str'
            case CHAR_POINTER -> "str";

            // Map generic data pointers to Any or bytes
            case VOID_POINTER -> "Any | bytes | None";

            // Note: we could use numpy.typing's ArrayLike, but we want to be compatible without numpy
            case FLOAT32_POINTER, FLOAT64_POINTER -> "Sequence[float]";
            case UINT8_POINTER, INT32_POINTER, INT64_POINTER -> "Sequence[int]";

            // Map string arrays to List of strings
            case CHAR_POINTER_POINTER -> "Sequence[str]";

            default -> "Any";
        };
    }

    public static DataType getGraalDataType(TypeName javaType) {
        return JavaToGraalLookup.mapping.get(javaType);
    }

    static class JavaToGraalLookup {
        static final Map<TypeName, DataType> mapping = new HashMap<>();

        private static void addMapping(Class<?> clazz, DataType value) {
            mapping.put(ClassName.get(clazz), value);
        }

        private static void addPrimitive(TypeName primitive, DataType value) {
            mapping.put(primitive, value);
            mapping.put(primitive.box(), value);
        }

        static {
            // Primitives and their Boxed counterparts
            addPrimitive(TypeName.VOID, DataType.VOID);
            addPrimitive(TypeName.BOOLEAN, DataType.BOOL);
            addPrimitive(TypeName.CHAR, DataType.CHAR16);
            addPrimitive(TypeName.BYTE, DataType.INT8);
            addPrimitive(TypeName.SHORT, DataType.INT16);
            addPrimitive(TypeName.INT, DataType.INT32);
            addPrimitive(TypeName.LONG, DataType.INT64);
            addPrimitive(TypeName.FLOAT, DataType.FLOAT32);
            addPrimitive(TypeName.DOUBLE, DataType.FLOAT64);

            // Convenience shortcut for Strings
            addMapping(String.class, DataType.CHAR_POINTER);

            // GraalVM C-API Pointers. The lookup is exact on the declared type, so
            // CBytePointer never collides with its CCharPointer super interface.
            addMapping(VoidPointer.class, DataType.VOID_POINTER);
            addMapping(CBytePointer.class, DataType.UINT8_POINTER); // custom marker since GraalVM uses char*
            addMapping(CCharPointer.class, DataType.CHAR_POINTER);
            addMapping(CCharPointerPointer.class, DataType.CHAR_POINTER_POINTER);
            addMapping(CShortPointer.class, DataType.INT16_POINTER);
            addMapping(CIntPointer.class, DataType.INT32_POINTER);
            addMapping(CLongPointer.class, DataType.INT64_POINTER);
            addMapping(CFloatPointer.class, DataType.FLOAT32_POINTER);
            addMapping(CDoublePointer.class, DataType.FLOAT64_POINTER);
            addMapping(ObjectHandle.class, DataType.OBJECT_HANDLE);
        }
    }

}
