package us.hebi.graalvm.bindings.generator.bindings.marshallers;

import com.squareup.javapoet.ArrayTypeName;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.TypeName;
import org.graalvm.word.WordFactory;
import us.hebi.graalvm.bindings.generator.bindings.StructMarshaller;
import us.hebi.graalvm.bindings.generator.bindings.TypeMap;
import us.hebi.graalvm.bindings.generator.generators.IndentingTextRenderer;
import us.hebi.graalvm.bindings.runtime.structs.DoubleSpan;

import java.util.Optional;

/**
 * Marshaller for the {@code (pointer, length)} view of a contiguous double buffer. The struct
 * itself stays internal in every language face: C++ sees {@code std::vector}, Python numpy
 * arrays and MATLAB native row vectors.
 *
 * @author Florian Enner
 * @since 6 Aug 2026
 */
class DoubleSpanMarshaller implements StructMarshaller {

    @Override
    public Optional<AllocatingReturn> allocatingReturn() {
        return Optional.of(ALLOCATING_RETURN);
    }

    @Override
    public Optional<CppStructMarshaller> cpp() {
        return Optional.of(CPP);
    }

    @Override
    public Optional<PyStructMarshaller> python() {
        return Optional.of(PYTHON);
    }

    @Override
    public Optional<MexStructMarshaller> matlab() {
        return Optional.of(MATLAB);
    }

    private static final class SpanReturn implements AllocatingReturn {

        @Override
        public TypeName javaType() {
            return ArrayTypeName.of(TypeName.DOUBLE);
        }

        /**
         * The array gets copied into a per-thread scratch buffer that stays valid until the
         * thread's next call, same as the string returns.
         */
        @Override
        public CodeBlock fillOut(String outName, String resultVar) {
            return CodeBlock.builder()
                    .addStatement("$N.setData($T.toTempDoublePointer($N))", outName, CPOINTERS, resultVar)
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

    private static final class SpanCpp implements CppStructMarshaller {

        @Override
        public String paramType() {
            return "const std::vector<double>&";
        }

        /** Vectors carry their own length, so the span packs without extra arguments */
        @Override
        public String pack(String paramName) {
            return "{" + paramName + ".data(), " + paramName + ".size()}";
        }

        @Override
        public String returnType() {
            return "std::vector<double>";
        }

        @Override
        public String unpack(String structVar) {
            return "  return std::vector<double>(%1$s.data, %1$s.data + %1$s.length); // copy before the next call invalidates it\n"
                    .formatted(structVar);
        }

    }

    private static final class SpanPython implements PyStructMarshaller {

        /** The temporary array keeps the buffer alive until the call returns */
        @Override
        public String pack(String paramName) {
            return IndentingTextRenderer.of("""
                            if %%NAME%% is None:
                                %%NAME%% = %%CTYPE%%(None, 0)
                            elif not isinstance(%%NAME%%, %%CTYPE%%):
                                %%NAME%%_arr = np.ascontiguousarray(%%NAME%%, dtype=np.float64)
                                %%NAME%% = %%CTYPE%%(%%NAME%%_arr.ctypes.data_as(POINTER(c_double)), len(%%NAME%%_arr))
                            """)
                    .set("NAME", paramName)
                    .set("CTYPE", TypeMap.toStructMirrorCPython(STRUCT_NAME))
                    .render();
        }

        /** Copied out immediately because the span is only valid until the next call */
        @Override
        public String unpack(String structVar) {
            return IndentingTextRenderer.of("""
                            %%VAR%% = %%CALL%%
                            if %%VAR%%.length == 0 or not %%VAR%%.data:
                                return np.empty(0, dtype=np.float64)
                            return np.ctypeslib.as_array(%%VAR%%.data, shape=(%%VAR%%.length,)).copy()
                            """)
                    .set("VAR", structVar)
                    .render();
        }

        /** Note: we could use numpy.typing's ArrayLike, but we want to be compatible without numpy */
        @Override
        public String typeHint() {
            return "Sequence[float]";
        }

    }

    private static final class SpanMex implements MexStructMarshaller {

        /** MatlabArray handles class conversion, and the element count rides along in the span */
        @Override
        public String read(String prhsExpr, String structVar) {
            return ("MatlabArray<double, mxDOUBLE_CLASS> %1$s_arr(%2$s);\n" +
                    "%3$s %1$s = {%1$s_arr.ptr, mxGetNumberOfElements(%2$s)}")
                    .formatted(structVar, prhsExpr, STRUCT_NAME);
        }

        @Override
        public String write(String structVar) {
            return "createMexDoubleArray(" + structVar + ")";
        }

        @Override
        public String helpers() {
            return IndentingTextRenderer.of("""

                            // Returned spans are library-owned and only valid until the next call,
                            // so they get copied immediately
                            inline mxArray* createMexDoubleArray(%%STRUCT_NAME%% span) {
                                if (!span.data || span.length == 0) {
                                    return createEmpty();
                                }
                                mxArray* array = mxCreateDoubleMatrix(1, span.length, mxREAL);
                                std::memcpy(mxGetPr(array), span.data, span.length * sizeof(double));
                                return array;
                            }
                            """)
                    .set("STRUCT_NAME", STRUCT_NAME)
                    .render();
        }

        @Override
        public String typeHint() {
            return "numeric";
        }

    }

    static final String VIEW_TYPE = DoubleSpan.class.getCanonicalName();
    private static final String STRUCT_NAME = StructMarshallers.toStructName(VIEW_TYPE);

    private static final ClassName CPOINTERS = ClassName.get(us.hebi.graalvm.bindings.runtime.pointers.CPointers.class);

    private static final AllocatingReturn ALLOCATING_RETURN = new SpanReturn();
    private static final CppStructMarshaller CPP = new SpanCpp();
    private static final PyStructMarshaller PYTHON = new SpanPython();
    private static final MexStructMarshaller MATLAB = new SpanMex();

}
