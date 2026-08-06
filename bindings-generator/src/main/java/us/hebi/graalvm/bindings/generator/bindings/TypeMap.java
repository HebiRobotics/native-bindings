package us.hebi.graalvm.bindings.generator.bindings;

import com.google.common.base.CaseFormat;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeName;
import org.graalvm.nativeimage.ObjectHandle;
import org.graalvm.nativeimage.c.type.*;
import us.hebi.graalvm.bindings.model.DataType;
import us.hebi.graalvm.bindings.model.TypeDescription;

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
            case TYPEDEF, ENUM -> type.getCPrefix() + type.getTypeName();
            case STRUCT -> toStructCName(type.getCPrefix(), type.getTypeName());
            default -> toExternalC(type.getDataType());
        };
    }

    /**
     * Public C name of a user struct. Bare rather than suffixed with {@code _t}, which POSIX
     * reserves and which here marks the hand-built plumbing typedefs, while generated data
     * structs follow the span's style.
     */
    public static String toStructCName(String cPrefix, String name) {
        return cPrefix + CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, name);
    }

    /**
     * @return C type of a struct field. Fixed width on purpose: the public header and the
     * native-image probe header declare the same layout and must not disagree on a 32 bit target.
     */
    public static String toStructFieldC(DataType type) {
        return switch (type) {
            case FLOAT64 -> "double";
            case FLOAT32 -> "float";
            case INT64 -> "int64_t";
            case INT32 -> "int32_t";
            case INT16 -> "int16_t";
            case INT8 -> "int8_t";
            case BOOL -> "int32_t"; // native-image maps a Java boolean to a 4 byte int
            case SIZE_T -> "size_t"; // the exception to the fixed width rule, both headers say size_t

            // Const is a statement to the external consumer only. The Java setter keeps
            // working, because the entry point writes the field through the view.
            case FLOAT64_POINTER -> "const double*";
            case CHAR_POINTER -> "const char*";

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
            case SIZE_T -> "c_size_t";
            case FLOAT64_POINTER -> "POINTER(c_double)";
            case CHAR_POINTER -> "c_char_p";
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
            case INT64, INT32, INT16, INT8, SIZE_T -> "int";
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
            case INT16_POINTER -> "short*";
            case INT32_POINTER -> "int*";
            case INT64_POINTER -> "long long int*";
            case FLOAT32_POINTER -> "float*";
            case FLOAT64_POINTER -> "double*";

            // Custom / Opaque
            case ENUM -> "int";               // @CEnum maps to int
            case OBJECT_HANDLE -> "void*";    // technically void*

            // Structs only cross the public ABI by value. The internal entry point sees
            // them as struct pointers, so they never have an internal name.
            case STRUCT, TYPEDEF, UNSPECIFIED ->
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
            case INT16_POINTER -> "POINTER(c_short)";
            case INT32_POINTER -> "POINTER(c_int)";
            case INT64_POINTER -> "POINTER(c_longlong)";
            case FLOAT32_POINTER -> "POINTER(c_float)";
            case FLOAT64_POINTER -> "POINTER(c_double)";

            // Meta / Custom
            case TYPEDEF -> "c_void_p";         // Treat aliases as opaque
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
            case OBJECT_HANDLE, TYPEDEF, ENUM -> desc.getTypeName();
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
            case INT32_POINTER, INT64_POINTER -> "Sequence[int]";

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

            // GraalVM C-API Pointers
            addMapping(VoidPointer.class, DataType.VOID_POINTER);
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
