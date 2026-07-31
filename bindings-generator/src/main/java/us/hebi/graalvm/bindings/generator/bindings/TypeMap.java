package us.hebi.graalvm.bindings.generator.bindings;

import com.squareup.javapoet.ArrayTypeName;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeName;
import org.graalvm.nativeimage.ObjectHandle;
import org.graalvm.nativeimage.c.type.*;
import us.hebi.graalvm.bindings.model.DataType;
import us.hebi.graalvm.bindings.model.TypeDescription;
import us.hebi.graalvm.bindings.runtime.types.DoubleSpan;

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
        return type == DataType.VOID || type == DataType.VOID_WITH_ERROR_CODE;
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
            case VOID_WITH_ERROR_CODE -> "void";
            case BOOL -> "bool";
            default -> toExternalC(type);
        };
    }

    public static String toExternalC(TypeDescription type, boolean ptrSuffix) {
        return switch (type.getDataType()) {
            case OBJECT_HANDLE -> type.getCPrefix() + type.getTypeName() + (ptrSuffix ? C_PTR_SUFFIX : "");
            case TYPEDEF, ENUM -> type.getCPrefix() + type.getTypeName();
            case FLOAT64_SPAN -> type.getCPrefix() + SPAN_STRUCT_NAME;
            default -> toExternalC(type.getDataType());
        };
    }

    /**
     * Simple name of the by-value span struct, prefixed with the library c_prefix
     * in the public header.
     */
    public static final String SPAN_STRUCT_NAME = "double_span";

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
            case VOID_WITH_ERROR_CODE -> "int";
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

            // Spans only exist as a by-value struct in the public ABI. The internal entry
            // point explodes them into (pointer, length), so they never have an internal name.
            case FLOAT64_SPAN, TYPEDEF, UNSPECIFIED ->
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

            // The span Structure emitted once per generated Python file
            case FLOAT64_SPAN -> "_DoubleSpan";

            case UNSPECIFIED -> throw new IllegalArgumentException("Unknown data type: " + type);
            case VOID_WITH_ERROR_CODE -> "c_int";
        };
    }

    public static String toPythonTypeHint(TypeDescription desc) {
        return switch (desc.getDataType()) {
            case OBJECT_HANDLE, TYPEDEF, ENUM -> desc.getTypeName();
            case VOID, VOID_WITH_ERROR_CODE -> "None";
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
            case FLOAT32_POINTER, FLOAT64_POINTER, FLOAT64_SPAN -> "Sequence[float]";
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

            // Spans: DoubleSpan params (borrowed view) and double[] returns (copied out)
            addMapping(DoubleSpan.class, DataType.FLOAT64_SPAN);
            mapping.put(ArrayTypeName.of(TypeName.DOUBLE), DataType.FLOAT64_SPAN);
        }
    }

}
