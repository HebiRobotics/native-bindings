package us.hebi.graalvm.bindings.generator.annotations;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeName;
import org.graalvm.nativeimage.Isolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.ObjectHandle;
import org.graalvm.nativeimage.c.type.CCharPointer;
import us.hebi.graalvm.bindings.annotations.CDefault;
import us.hebi.graalvm.bindings.annotations.CLibClass;
import us.hebi.graalvm.bindings.annotations.CLibComment;
import us.hebi.graalvm.bindings.annotations.CLibEnum;
import us.hebi.graalvm.bindings.annotations.CLibIndex;
import us.hebi.graalvm.bindings.annotations.CLibOut;
import us.hebi.graalvm.bindings.annotations.CLibStruct;
import us.hebi.graalvm.bindings.annotations.CSizeType;
import us.hebi.graalvm.bindings.generator.bindings.StructMarshaller;
import us.hebi.graalvm.bindings.generator.bindings.marshallers.StructMarshallers;
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
import java.util.Map;
import java.util.Optional;

import static us.hebi.graalvm.bindings.generator.generators.ProcessingException.*;

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
            check(unboxedType == TypeName.INT || unboxedType == TypeName.LONG, sourceElement, "size_t is may only be long or int");
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
        if (getStructElement().isPresent()) return DataType.STRUCT;
        if (isGeneratedCEnumType()) return DataType.ENUM;
        if (isCallbackType(mirror)) return DataType.CALLBACK;
        if (isNativeApiType()) return DataType.OBJECT_HANDLE;

        throw new IllegalArgumentException("Unsupported data type: " + type);
    }

    public TypeDescription toDescription() {
        var desc = TypeDescription.newInstance()
                .setDataType(getDataType());

        // Only add flags that are not set to the default value
        if (isConst()) desc.setIsConst(true);
        if (isIndex()) desc.setIsIndex(true);

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

    public boolean isNativeApiType() {
        return isNativeApiType(rawMirror);
    }

    public boolean isGeneratedCEnumType() {
        return isGeneratedCEnumType(mirror);
    }

    /**
     * A {@link CLibStruct} view that crosses the C ABI by value.
     * TODO: also support user structs by ref? Most structs should be small and it'd be hard to allocate in Python/MATLAB
     */
    public boolean isStructView() {
        return getTypeAnnotation(mirror, CLibStruct.class) != null;
    }

    /**
     * The struct view this type resolves to, which is either the declared type itself or the
     * view a registered marshaller accepts this Java type as the authoring form of, e.g. a
     * {@code double[]} return standing in for the span the entry point fills.
     */
    private Optional<TypeElement> getStructElement() {
        if (isStructView()) {
            return Optional.of((TypeElement) env.getTypeUtils().asElement(mirror));
        }
        return getAllocatingMarshaller().map(entry -> env.getElementUtils().getTypeElement(entry.getKey()));
    }

    /**
     * @return the view a trailing out-parameter for this type has to be declared as
     */
    public Optional<ClassName> getStructViewType() {
        return getStructElement().map(ClassName::get);
    }

    /**
     * A marshaller may accept a second Java form for returns, e.g. a {@code double[]} that the
     * entry point copies into the span view it fills. Empty for the view type itself.
     */
    public Optional<StructMarshaller.AllocatingReturn> getAllocatingReturn() {
        return getAllocatingMarshaller().flatMap(entry -> entry.getValue().allocatingReturn());
    }

    private Optional<Map.Entry<String, StructMarshaller>> getAllocatingMarshaller() {
        return StructMarshallers.getAll().entrySet().stream()
                .filter(entry -> entry.getValue().allocatingReturn()
                        .filter(allocating -> allocating.javaType().equals(type))
                        .isPresent())
                .findFirst();
    }

    /**
     * @return {@link CLibOut} parameter that gets allocated by the wrapper and returned as value
     */
    public boolean isOutParameter() {
        return sourceElement.getAnnotation(CLibOut.class) != null;
    }

    public Optional<ClassName> getApiTypeWrapperClass() {
        if (isNativeApiType()) {
            TypeElement typeElement = (TypeElement) env.getTypeUtils().asElement(rawMirror);
            return Optional.of(AnnotatedCLibClass.getWrapperType(ClassName.get(typeElement)));
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
            case STRUCT -> getStructElement()
                    .map(element -> element.getAnnotation(CLibStruct.class))
                    .map(annotation -> AnnotatedCLibStruct.getCPrefix(getOptions(), annotation));
            case CALLBACK -> Optional.of(AnnotatedCLibCallback.getCPrefix(getOptions(), toTypeElement(mirror)));
            default -> Optional.empty();
        };
    }

    public Optional<String> getClassName() {
        return switch (getDataType()) {
            case OBJECT_HANDLE ->
                    AnnotationUtil.tryGetAnnotationValue((TypeElement) env.getTypeUtils().asElement(mirror), CLibClass.class, CLibClass::value);
            case ENUM ->
                    AnnotationUtil.tryGetAnnotationValue((TypeElement) env.getTypeUtils().asElement(mirror), CLibEnum.class, CLibEnum::value);
            case CALLBACK -> Optional.of(AnnotatedCLibCallback.getName((TypeElement) env.getTypeUtils().asElement(mirror)));
            case STRUCT -> getStructElement()
                    .map(AnnotatedCLibStruct::getName)
                    .filter(name -> !name.isEmpty());
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

    /**
     * @return the element this type was read from, i.e. the parameter itself for a parameter
     */
    Element getSourceElement() {
        return sourceElement;
    }

    /**
     * @return the declared type, the view a marshalled authoring form resolves to, or null
     * for primitives and arrays
     */
    public TypeElement getTypeElement() {
        return getStructElement().orElse(element);
    }

    private final Element sourceElement;
    private final TypeMirror mirror;
    private final TypeMirror rawMirror;
    private final TypeName type;
    private final TypeName unboxedType;

    private final String name;

}
