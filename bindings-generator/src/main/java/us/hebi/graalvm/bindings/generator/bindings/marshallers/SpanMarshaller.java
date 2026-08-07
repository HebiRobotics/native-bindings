package us.hebi.graalvm.bindings.generator.bindings.marshallers;

import com.squareup.javapoet.ArrayTypeName;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.TypeName;
import org.graalvm.word.WordFactory;
import us.hebi.graalvm.bindings.generator.bindings.StructMarshaller;
import us.hebi.graalvm.bindings.generator.bindings.TypeMap;
import us.hebi.graalvm.bindings.generator.generators.IndentingTextRenderer;

import java.util.Optional;

/**
 * Marshaller for the {@code (pointer, length)} view of a contiguous element buffer. The
 * struct itself stays internal in every language face: C++ sees {@code std::vector},
 * Python numpy arrays and MATLAB native row vectors. One instance per element type,
 * differing only in the {@link Element} table.
 *
 * @author Florian Enner
 * @since 6 Aug 2026
 */
class SpanMarshaller implements StructMarshaller {

    /**
     * Everything that changes between element types. The {@code word} is the element's
     * name in Java identifiers ({@code toTempDoublePointer}, {@code createMexDoubleArray}).
     */
    record Element(Class<?> viewType,
                   TypeName javaType,
                   String word,
                   String cppType,
                   String numpyDtype,
                   String ctypesType,
                   String mxType,
                   String mxClassId,
                   String mxCreate,
                   String mxData) {
    }

    SpanMarshaller(Element element) {
        this.element = element;
        this.viewType = element.viewType().getCanonicalName();
        this.structName = StructMarshallers.toStructName(viewType);
        this.allocatingReturn = new SpanReturn();
        this.cpp = new SpanCpp();
        this.python = new SpanPython();
        this.matlab = new SpanMex();
    }

    @Override
    public Optional<AllocatingReturn> allocatingReturn() {
        return Optional.of(allocatingReturn);
    }

    @Override
    public Optional<CppStructMarshaller> cpp() {
        return Optional.of(cpp);
    }

    @Override
    public Optional<PyStructMarshaller> python() {
        return Optional.of(python);
    }

    @Override
    public Optional<MexStructMarshaller> matlab() {
        return Optional.of(matlab);
    }

    private final class SpanReturn implements AllocatingReturn {

        @Override
        public TypeName javaType() {
            return ArrayTypeName.of(element.javaType());
        }

        /**
         * The array gets copied into a per-thread scratch buffer that stays valid until the
         * thread's next call, same as the string returns.
         */
        @Override
        public CodeBlock fillOut(String outName, String resultVar) {
            return CodeBlock.builder()
                    .addStatement("$N.setData($T.toTemp" + element.word() + "Pointer($N))", outName, CPOINTERS, resultVar)
                    .addStatement("$N.setLength($N == null ? 0 : $N.length)", outName, resultVar, resultVar)
                    .build();
        }

        @Override
        public CodeBlock zeroOut(String outName) {
            return CodeBlock.builder()
                    .addStatement("$N.setData($T.nullPointer())", outName, WordFactory.class)
                    .addStatement("$N.setLength(0)", outName)
                    .build();
        }

    }

    private final class SpanCpp implements CppStructMarshaller {

        @Override
        public String paramType() {
            return "const std::vector<" + element.cppType() + ">&";
        }

        /** Vectors carry their own length, so the span packs without extra arguments */
        @Override
        public String pack(String paramName) {
            return "{" + paramName + ".data(), " + paramName + ".size()}";
        }

        @Override
        public String returnType() {
            return "std::vector<" + element.cppType() + ">";
        }

        @Override
        public String unpack(String structVar) {
            return "  return std::vector<%2$s>(%1$s.data, %1$s.data + %1$s.length); // copy before the next call invalidates it\n"
                    .formatted(structVar, element.cppType());
        }

    }

    private final class SpanPython implements PyStructMarshaller {

        /** The temporary array keeps the buffer alive until the call returns */
        @Override
        public String pack(String paramName) {
            return IndentingTextRenderer.of("""
                            if %%NAME%% is None:
                                %%NAME%% = %%CTYPE%%(None, 0)
                            elif not isinstance(%%NAME%%, %%CTYPE%%):
                                %%NAME%%_arr = np.ascontiguousarray(%%NAME%%, dtype=np.%%DTYPE%%)
                                %%NAME%% = %%CTYPE%%(%%NAME%%_arr.ctypes.data_as(POINTER(%%ELEMENT%%)), len(%%NAME%%_arr))
                            """)
                    .set("NAME", paramName)
                    .set("CTYPE", TypeMap.toStructMirrorCPython(structName))
                    .set("DTYPE", element.numpyDtype())
                    .set("ELEMENT", element.ctypesType())
                    .render();
        }

        /** Copied out immediately because the span is only valid until the next call */
        @Override
        public String unpack(String structVar) {
            return IndentingTextRenderer.of("""
                            %%VAR%% = %%CALL%%
                            if %%VAR%%.length == 0 or not %%VAR%%.data:
                                return np.empty(0, dtype=np.%%DTYPE%%)
                            return np.ctypeslib.as_array(%%VAR%%.data, shape=(%%VAR%%.length,)).copy()
                            """)
                    .set("VAR", structVar)
                    .set("DTYPE", element.numpyDtype())
                    .render();
        }

        /** Note: we could use numpy.typing's ArrayLike, but we want to be compatible without numpy */
        @Override
        public String typeHint() {
            return element.javaType() == TypeName.DOUBLE || element.javaType() == TypeName.FLOAT
                    ? "Sequence[float]" : "Sequence[int]";
        }

    }

    private final class SpanMex implements MexStructMarshaller {

        /** MatlabArray handles class conversion, and the element count rides along in the span */
        @Override
        public String read(String prhsExpr, String structVar) {
            return ("MatlabArray<%4$s, %5$s> %1$s_arr(%2$s);\n" +
                    "%3$s %1$s = {%1$s_arr.ptr, mxGetNumberOfElements(%2$s)}")
                    .formatted(structVar, prhsExpr, structName, element.mxType(), element.mxClassId());
        }

        @Override
        public String write(String structVar) {
            return "createMex" + element.word() + "Array(" + structVar + ")";
        }

        @Override
        public String helpers() {
            return IndentingTextRenderer.of("""

                            // Returned spans are library-owned and only valid until the next call,
                            // so they get copied immediately
                            inline mxArray* createMex%%WORD%%Array(%%STRUCT_NAME%% span) {
                                if (!span.data || span.length == 0) {
                                    return createEmpty();
                                }
                                mxArray* array = %%CREATE%%;
                                std::memcpy(%%DATA%%, span.data, span.length * sizeof(%%ELEMENT%%));
                                return array;
                            }
                            """)
                    .set("WORD", element.word())
                    .set("STRUCT_NAME", structName)
                    .set("CREATE", element.mxCreate())
                    .set("DATA", element.mxData())
                    .set("ELEMENT", element.mxType())
                    .render();
        }

        @Override
        public String typeHint() {
            return "numeric";
        }

    }

    static final SpanMarshaller DOUBLE = new SpanMarshaller(new Element(
            us.hebi.graalvm.bindings.runtime.structs.DoubleSpan.class, TypeName.DOUBLE, "Double",
            "double", "float64", "c_double",
            "double", "mxDOUBLE_CLASS",
            "mxCreateDoubleMatrix(1, span.length, mxREAL)", "mxGetPr(array)"));

    static final SpanMarshaller INT = new SpanMarshaller(new Element(
            us.hebi.graalvm.bindings.runtime.structs.IntSpan.class, TypeName.INT, "Int",
            "int32_t", "int32", "c_int32",
            "int32_t", "mxINT32_CLASS",
            "mxCreateNumericMatrix(1, span.length, mxINT32_CLASS, mxREAL)", "mxGetData(array)"));

    static final SpanMarshaller FLOAT = new SpanMarshaller(new Element(
            us.hebi.graalvm.bindings.runtime.structs.FloatSpan.class, TypeName.FLOAT, "Float",
            "float", "float32", "c_float",
            "float", "mxSINGLE_CLASS",
            "mxCreateNumericMatrix(1, span.length, mxSINGLE_CLASS, mxREAL)", "mxGetData(array)"));

    static final SpanMarshaller BYTE = new SpanMarshaller(new Element(
            us.hebi.graalvm.bindings.runtime.structs.ByteSpan.class, TypeName.BYTE, "Byte",
            "uint8_t", "uint8", "c_uint8",
            "uint8_t", "mxUINT8_CLASS",
            "mxCreateNumericMatrix(1, span.length, mxUINT8_CLASS, mxREAL)", "mxGetData(array)"));

    final String viewType;
    private final String structName;
    private final Element element;

    private final AllocatingReturn allocatingReturn;
    private final CppStructMarshaller cpp;
    private final PyStructMarshaller python;
    private final MexStructMarshaller matlab;

    private static final ClassName CPOINTERS = ClassName.get(us.hebi.graalvm.bindings.runtime.pointers.CPointers.class);

}
