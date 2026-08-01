package us.hebi.graalvm.bindings.generator.annotations;

import com.google.auto.common.MoreElements;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeName;
import org.graalvm.nativeimage.Isolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.ObjectHandle;
import org.graalvm.nativeimage.c.type.CCharPointer;
import us.hebi.graalvm.bindings.runtime.types.DoubleSpan;
import us.hebi.graalvm.bindings.annotations.CDefault;
import us.hebi.graalvm.bindings.annotations.CLibClass;
import us.hebi.graalvm.bindings.annotations.CLibComment;
import us.hebi.graalvm.bindings.annotations.CLibEnum;
import us.hebi.graalvm.bindings.annotations.CLibIndex;
import us.hebi.graalvm.bindings.annotations.CLibTypeDef;
import us.hebi.graalvm.bindings.annotations.CSizeType;
import us.hebi.graalvm.bindings.generator.bindings.TypeMap;
import us.hebi.graalvm.bindings.generator.generators.CEnumGenerator;
import us.hebi.graalvm.bindings.model.DataType;
import us.hebi.graalvm.bindings.model.TypeDescription;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.util.Optional;

import static com.google.common.base.Preconditions.*;

/**
 * @author Florian Enner
 * @since 09 Feb 2024
 */
public class AnnotatedClibType extends AnnotatedCLibElement<TypeElement> {

    public static AnnotatedClibType fromType(TypeMirror type, ProcessingEnvironment env) {
        // Note that primitives like "int" return a null element
        var element = (TypeElement) env.getTypeUtils().asElement(type);
        return new AnnotatedClibType(element, element, type, env, "");
    }

    public static AnnotatedClibType fromVariable(VariableElement element, ProcessingEnvironment env) {
        TypeMirror typeMirror = element.asType();
        var typeElement = typeMirror instanceof DeclaredType declared ? (TypeElement) declared.asElement() : null;
        var defaultValue = element.getAnnotationMirrors().stream()
                .filter(am -> am.getAnnotationType().toString().endsWith("CApiDefault"))
                .map(AnnotationMirror::getElementValues)
                .flatMap(values -> values.values().stream())
                .map(annotationValue -> annotationValue.getValue().toString())
                .findFirst()
                .orElse(null);
        return new AnnotatedClibType(element, typeElement, element.asType(), env, String.valueOf(element.getSimpleName()));
    }

    public AnnotatedClibType(Element sourceElement, TypeElement element, TypeMirror mirror, ProcessingEnvironment env, String name) {
        super(element, env);
        this.sourceElement = sourceElement;
        this.mirror = mirror;
        this.rawMirror = env.getTypeUtils().erasure(mirror); // raw type without <T>
        this.type = TypeName.get(mirror);
        this.unboxedType = type.isBoxedPrimitive() ? type.unbox() : type;
        this.name = name;
    }

    public Optional<String> getDefaultValue() {
        return AnnotationUtil.tryGetAnnotationValue(sourceElement, CDefault.class, CDefault::value);
    }

    public Optional<String> getComment() {
        return AnnotationUtil.tryGetAnnotationValue(sourceElement, CLibComment.class, CLibComment::value);
    }

    public TypeName getJavaType() {
        return type;
    }

    public TypeName getCEntryType() {
        if (isNativeApiType(mirror)) return TypeName.get(ObjectHandle.class);
        if (isString()) return ClassName.get(CCharPointer.class);
        if (isSizeType()) return TypeName.LONG;
        // Span parameters cross as a pointer to the by-value ABI struct, a word type
        // with zero allocation. Span returns get a trailing out-param of the same type.
        if (isSpan()) return ClassName.get(DoubleSpan.class);

        // Map enums to int rather than going through a generated header. We already know all numbers anyways
        var generatedEnum = getGeneratedCEnumType();
        if (generatedEnum.isPresent()) {
            return TypeName.INT;
        }

        return type;
    }

    private boolean isConst() {
        return isConst(mirror) || ClassName.get(String.class).equals(type);
    }

    public boolean isSizeType() {
        if (mirror.toString().contains(CSizeType.class.getSimpleName())) {
            checkState(unboxedType == TypeName.INT || unboxedType == TypeName.LONG, "size_t is may only be long or int");
            return true;
        }
        return false;
    }

    /**
     * True for a parameter or return value marked with {@link CLibIndex}. The element type
     * is validated in {@code AnnotatedCLibMethod}, where the message can name the method.
     */
    public boolean isIndex() {
        return mirror.toString().contains(CLibIndex.class.getSimpleName());
    }

    public DataType getDataType() {
        // Handle size first so we don't use primitive int/long
        if (isSizeType()) return DataType.SIZE_T;

        // All standard primitives and Graal pointers
        var dataType = TypeMap.getGraalDataType(type);
        if (dataType != null) return dataType;

        // Special types we added
        if (isGeneratedCEnumType()) return DataType.ENUM;
        if (isDefinedType(mirror)) return DataType.TYPEDEF;
        if (isNativeApiType()) return DataType.OBJECT_HANDLE;

        throw new IllegalArgumentException("Unsupported data type: " + type);
    }

    public TypeDescription toDescription() {
        var desc = TypeDescription.newInstance()
                .setIsConst(isConst())
                .setIsIndex(isIndex())
                .setDataType(getDataType());

        getComment().ifPresent(desc::setComment);
        getDefaultValue().ifPresent(desc::setDefaultValue);
        getClassName().ifPresent(desc::setTypeName);
        getCPrefix().ifPresent(desc::setCPrefix);
        return desc;
    }

    public boolean isVoid() {
        return rawMirror.getKind() == TypeKind.VOID;
    }

    public boolean isIsolateThread() {
        return isTypeOf(rawMirror, IsolateThread.class);
    }

    public boolean isIsolateOrThread() {
        return isIsolateThread() || isTypeOf(rawMirror, Isolate.class);
    }

    public boolean isString() {
        return isTypeOf(rawMirror, String.class);
    }

    /**
     * True for both forms of {@link DataType#FLOAT64_SPAN}: DoubleSpan parameters
     * (borrowed view) and double[] returns (copied out via a temp buffer).
     */
    public boolean isSpan() {
        return TypeMap.getGraalDataType(type) == DataType.FLOAT64_SPAN;
    }

    public boolean isNativeApiType() {
        return isNativeApiType(rawMirror);
    }

    public boolean isGeneratedCEnumType() {
        return isGeneratedCEnumType(mirror);
    }

    public Optional<ClassName> getApiTypeWrapperClass() {
        if (isNativeApiType()) {
            TypeElement typeElement = (TypeElement) env.getTypeUtils().asElement(rawMirror);
            var namespace = MoreElements.getPackage(typeElement).getQualifiedName();
            var name = typeElement.getSimpleName();
            return Optional.of(AnnotatedCLibClass.getWrapperType(namespace.toString(), name.toString()));
        }
        return Optional.empty();
    }

    public Optional<String> getCPrefix() {
        return switch (getDataType()) {
            case OBJECT_HANDLE -> {
                TypeElement typeElement = (TypeElement) env.getTypeUtils().asElement(mirror);
                var annotation = AnnotationUtil.extractAnnotation(typeElement, CLibClass.class);
                var prefix = AnnotationUtil.getCPrefix(getOptions(), annotation.cPrefix()) + "_";
                yield Optional.of(prefix);
            }
            case ENUM -> Optional.ofNullable(getTypeAnnotation(mirror, CLibEnum.class))
                    .map(annotation -> AnnotatedCLibEnum.getCPrefix(getOptions(), annotation));
            // The span struct is defined once per library, so it uses the library-wide prefix
            case FLOAT64_SPAN -> Optional.of(AnnotationUtil.getCPrefix(getOptions(), "") + "_");
            default -> Optional.empty();
        };
    }

    public Optional<String> getClassName() {
        return switch (getDataType()) {
            case OBJECT_HANDLE ->
                    AnnotationUtil.tryGetAnnotationValue((TypeElement) env.getTypeUtils().asElement(mirror), CLibClass.class, CLibClass::value);
            case ENUM ->
                    AnnotationUtil.tryGetAnnotationValue((TypeElement) env.getTypeUtils().asElement(mirror), CLibEnum.class, CLibEnum::value);
            case TYPEDEF ->
                    AnnotationUtil.tryGetAnnotationValue((TypeElement) env.getTypeUtils().asElement(mirror), CLibTypeDef.class, CLibTypeDef::name);
            default -> Optional.empty();
        };
    }

    public Optional<ClassName> getGeneratedCEnumType() {
        if (isGeneratedCEnumType(mirror)) {
            return Optional.ofNullable(CEnumGenerator.getCEnumWrapperClassName(type));
        }
        return Optional.empty();
    }

    public String getName() {
        return name;
    }

    private final Element sourceElement;
    private final TypeMirror mirror;
    private final TypeMirror rawMirror;
    private final TypeName type;
    private final TypeName unboxedType;

    private final String name;

}
