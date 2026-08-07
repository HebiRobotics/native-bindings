package us.hebi.graalvm.bindings.generator.annotations;

import com.google.common.base.CaseFormat;
import com.google.common.base.Strings;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeName;
import org.graalvm.nativeimage.c.struct.AllowNarrowingCast;
import org.graalvm.nativeimage.c.struct.AllowWideningCast;
import org.graalvm.nativeimage.c.struct.CField;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.nativeimage.c.type.CConst;
import org.graalvm.word.PointerBase;
import us.hebi.graalvm.bindings.annotations.CLibComment;
import us.hebi.graalvm.bindings.annotations.CLibStruct;
import us.hebi.graalvm.bindings.annotations.CSizeType;
import us.hebi.graalvm.bindings.generator.bindings.marshallers.StructMarshallers;
import us.hebi.graalvm.bindings.generator.bindings.TypeMap;
import us.hebi.graalvm.bindings.generator.generators.ProcessingException;
import us.hebi.graalvm.bindings.model.DataType;
import us.hebi.graalvm.bindings.model.StructDescription;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

import static com.google.common.base.Preconditions.*;

/**
 * Annotated {@code @CStruct} view that describes a user struct crossing the public C ABI
 * by value. Version 1 allows primitive fields and typed GraalVM pointers.
 *
 * @author Florian Enner
 * @since 6 Aug 2026
 */
public class AnnotatedCLibStruct extends AnnotatedCLibElement<TypeElement> {

    public static boolean isNativeStruct(Element element) {
        return element.getAnnotation(CLibStruct.class) != null;
    }

    public AnnotatedCLibStruct(Element element, ProcessingEnvironment processingEnvironment) {
        super(castToStructElement(element), processingEnvironment);
        annotation = checkNotNull(element.getAnnotation(CLibStruct.class), "Struct must contain a @CLibStruct annotation");

        // native-image reports a missing @CStruct or PointerBase as "Unexpected method
        // without annotation", which names neither the type nor the real problem.
        if (this.element.getAnnotation(CStruct.class) == null) {
            throw error("@CLibStruct requires a GraalVM @CStruct annotation: %s", getQualifiedName());
        }
        if (!extendsPointerBase()) {
            throw error("@CLibStruct view must extend PointerBase: %s", getQualifiedName());
        }
        if (getName().isEmpty()) {
            throw error("@CLibStruct requires a name in either @CLibStruct or @CStruct: %s", getQualifiedName());
        }

        fields = parseFields();
        registerMarshaller();
    }

    /**
     * Registers the marshaller named in {@code @CLibStruct(marshaller = ...)}. The class
     * has to be precompiled (it lives in a separate module), because the annotation is
     * read while this compilation is still running and the generators need a live
     * instance to emit the custom code.
     */
    private void registerMarshaller() {
        var marshallerClassName = getMarshallerClassName();
        if (marshallerClassName == null) {
            return;
        }
        try {
            StructMarshallers.register(getQualifiedName(), marshallerClassName);
        } catch (IllegalArgumentException iae) {
            throw error("%s", iae.getMessage());
        }
    }

    private String getMarshallerClassName() {
        try {
            // Reading a Class value only succeeds when the class is loadable, which is
            // exactly the precompiled case. The mirror path covers the rest.
            Class<?> marshaller = annotation.marshaller();
            return marshaller == void.class ? null : marshaller.getName();
        } catch (MirroredTypeException mte) {
            var mirror = mte.getTypeMirror();
            if (mirror.getKind() == TypeKind.VOID) {
                return null;
            }
            var element = (TypeElement) env.getTypeUtils().asElement(mirror);
            return env.getElementUtils().getBinaryName(element).toString();
        }
    }

    private boolean extendsPointerBase() {
        var pointerBase = env.getElementUtils().getTypeElement(PointerBase.class.getCanonicalName());
        return pointerBase != null && env.getTypeUtils().isAssignable(element.asType(), pointerBase.asType());
    }

    private static TypeElement castToStructElement(Element element) {
        if (element instanceof TypeElement type && element.getKind() == ElementKind.INTERFACE) {
            return type;
        }
        throw new ProcessingException(element, "@CLibStruct may only be used on an interface: %s", element);
    }

    /**
     * Fields are listed in source order of the first accessor that mentions them, because
     * that order is the ABI layout the probe header and every language face agree on.
     */
    private List<StructField> parseFields() {
        var byName = new LinkedHashMap<String, StructField>();
        for (var enclosed : element.getEnclosedElements()) {
            if (enclosed.getKind() != ElementKind.METHOD) {
                continue;
            }
            var method = (ExecutableElement) enclosed;
            var methodName = String.valueOf(method.getSimpleName());
            if (method.getModifiers().contains(Modifier.DEFAULT) || method.getModifiers().contains(Modifier.STATIC)) {
                throw error("@CLibStruct view may only declare @CField accessors, found '%s': %s", methodName, getQualifiedName());
            }

            var cField = method.getAnnotation(CField.class);
            if (cField == null) {
                throw error("@CLibStruct view may only declare @CField accessors, found '%s': %s", methodName, getQualifiedName());
            }

            boolean isGetter = method.getParameters().isEmpty() && method.getReturnType().getKind() != TypeKind.VOID;
            boolean isSetter = method.getParameters().size() == 1 && method.getReturnType().getKind() == TypeKind.VOID;
            if (!isGetter && !isSetter) {
                throw error("@CField accessor '%s' must be a getter (no parameters, non-void) or a setter (one parameter, void): %s",
                        methodName, getQualifiedName());
            }

            var fieldName = getFieldName(cField, methodName);
            var type = isGetter ? method.getReturnType() : method.getParameters().get(0).asType();
            var dataType = getFieldDataType(fieldName, type, method, isGetter);

            // A boolean accessor is 1 byte while the field lands as int32, and the image
            // build rejects the size mismatch unless the accessor allows the cast.
            if (dataType == DataType.BOOL) {
                var required = isGetter ? AllowNarrowingCast.class : AllowWideningCast.class;
                if (method.getAnnotation(required) == null) {
                    throw error("boolean accessor '%s' requires @%s because the field is stored as int32: %s",
                            methodName, required.getSimpleName(), getQualifiedName());
                }
            }

            var isConst = isConstType(fieldName, type, dataType, method, isGetter);
            var field = byName.computeIfAbsent(fieldName, name -> new StructField(name, dataType, isConst));
            if (field.dataType != dataType) {
                throw error("@CField '%s' is declared as both %s and %s: %s",
                        fieldName, field.dataType, dataType, getQualifiedName());
            }
            if (field.isConst != isConst) {
                throw error("@CField '%s' is declared @CConst on one accessor and not the other: %s",
                        fieldName, getQualifiedName());
            }
            if (isGetter) {
                field.hasGetter = true;
            } else {
                field.hasSetter = true;
            }
            accessors.add(new StructAccessor(methodName, fieldName, TypeName.get(type), isGetter));
        }

        var result = new ArrayList<StructField>(byName.size());
        for (var field : byName.values()) {
            if (!field.hasGetter) {
                throw error("@CField '%s' has a setter but no matching getter: %s", field.name, getQualifiedName());
            }
            result.add(field);
        }
        return List.copyOf(result);
    }

    private DataType getFieldDataType(String fieldName, TypeMirror type, ExecutableElement method, boolean isGetter) {
        var javaType = TypeName.get(type);
        var dataType = isSizeType(fieldName, javaType, method, isGetter) ? DataType.SIZE_T : TypeMap.getGraalDataType(javaType);
        if (dataType == null || !isSupportedFieldType(javaType, dataType)) {
            throw error("@CLibStruct fields may only be primitives, @CSizeType long, "
                            + "or typed GraalVM pointers, got '%s %s': %s",
                    type, fieldName, getQualifiedName());
        }
        return dataType;
    }

    /**
     * GraalVM's {@code @CConst} is a type-use annotation, so it sits on the getter's return
     * type and on the setter's parameter type rather than on the method. It says the public
     * C face may only read through the pointer, which is meaningless on a value field.
     */
    private boolean isConstType(String fieldName, TypeMirror type, DataType dataType, ExecutableElement method, boolean isGetter) {
        var isConst = hasTypeAnnotation(method, isGetter, CConst.class);
        if (isConst && !TypeMap.isPointerType(dataType)) {
            throw error("@CConst field '%s' must be a pointer, got '%s': %s", fieldName, type, getQualifiedName());
        }
        return isConst;
    }

    /**
     * Checked before the type lookup, because JavaPoet drops the type-use annotation and the
     * field would map to a plain long. Only long is allowed: unlike a method parameter, which
     * gets converted at the boundary, the field is the probed layout.
     */
    private boolean isSizeType(String fieldName, TypeName javaType, ExecutableElement method, boolean isGetter) {
        if (!hasTypeAnnotation(method, isGetter, CSizeType.class)) {
            return false;
        }
        if (!TypeName.LONG.equals(javaType)) {
            throw error("@CSizeType field '%s' must be a long, got '%s': %s", fieldName, javaType, getQualifiedName());
        }
        return true;
    }

    /**
     * Checks a type-use annotation on the accessor's return or parameter type. The mirror is
     * authoritative for source elements, but javac does not reliably surface type annotations
     * of elements loaded from class files (broken on JDK 21, fine on 25), so precompiled
     * views fall back to reflection. They are loadable by construction: a precompiled view
     * ships on the processor classpath the same way its marshaller does.
     */
    private boolean hasTypeAnnotation(ExecutableElement method, boolean isGetter, Class<? extends Annotation> annotation) {
        var type = isGetter ? method.getReturnType() : method.getParameters().get(0).asType();
        for (var mirror : type.getAnnotationMirrors()) {
            if (annotation.getCanonicalName().contentEquals(
                    ((TypeElement) mirror.getAnnotationType().asElement()).getQualifiedName())) {
                return true;
            }
        }
        return hasReflectiveTypeAnnotation(method, isGetter, annotation);
    }

    private boolean hasReflectiveTypeAnnotation(ExecutableElement method, boolean isGetter, Class<? extends Annotation> annotation) {
        var binaryName = env.getElementUtils().getBinaryName((TypeElement) method.getEnclosingElement());
        Class<?> view;
        try {
            view = Class.forName(binaryName.toString(), false, AnnotatedCLibStruct.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError e) {
            return false; // declared in this compilation, so the mirror was authoritative
        }
        for (var candidate : view.getDeclaredMethods()) {
            if (candidate.getName().contentEquals(method.getSimpleName())
                    && candidate.getParameterCount() == (isGetter ? 0 : 1)) {
                var annotated = isGetter ? candidate.getAnnotatedReturnType() : candidate.getAnnotatedParameterTypes()[0];
                return annotated.isAnnotationPresent(annotation);
            }
        }
        return false;
    }

    /**
     * TypeMap maps String to CHAR_POINTER as a convenience for method signatures, but a field
     * accessor has to name the pointer type that native-image probes the layout against.
     */
    private static boolean isSupportedFieldType(TypeName javaType, DataType dataType) {
        return switch (dataType) {
            case FLOAT64, FLOAT32, INT64, INT32, INT16, INT8, CHAR16, BOOL, SIZE_T -> true;
            case FLOAT64_POINTER, FLOAT32_POINTER, INT64_POINTER, INT32_POINTER, INT16_POINTER,
                 UINT8_POINTER, CHAR_POINTER, VOID_POINTER -> !STRING_TYPE.equals(javaType);
            default -> false;
        };
    }

    /**
     * The {@code @CField} value names the field in C, and an empty one falls back to the
     * accessor name the same way native-image resolves it: only a "get" or "set" prefix
     * is removed, an "is" prefix is kept (see the CField javadoc).
     */
    private static String getFieldName(CField cField, String methodName) {
        if (!Strings.isNullOrEmpty(cField.value())) {
            return cField.value();
        }
        for (String prefix : ACCESSOR_PREFIXES) {
            if (methodName.length() > prefix.length() && methodName.startsWith(prefix)
                    && Character.isUpperCase(methodName.charAt(prefix.length()))) {
                return Character.toLowerCase(methodName.charAt(prefix.length())) + methodName.substring(prefix.length() + 1);
            }
        }
        return methodName;
    }

    /**
     * An out-parameter is filled through its setters, so a view used as one needs a setter
     * for every field. An input view is read-only and gets away with getters alone.
     */
    public void checkEveryFieldHasSetter() {
        var missing = fields.stream()
                .filter(field -> !field.hasSetter)
                .map(StructField::getName)
                .toList();
        if (!missing.isEmpty()) {
            throw error("@CLibOut view requires a setter for every field, missing %s: %s", missing, getQualifiedName());
        }
    }

    public Optional<String> getComment() {
        return AnnotationUtil.tryGetAnnotationValue(element, CLibComment.class, CLibComment::value);
    }

    public StructDescription toDescription() {
        var desc = StructDescription.newInstance()
                .setName(getName())
                .setCPrefix(getCPrefix())
                .setCName(getCName());
        getComment().ifPresent(desc::setComment);

        for (var field : fields) {
            var fieldDesc = desc.getMutableFields().next()
                    .setName(field.getName())
                    .setLowerUnderscoreName(CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, field.getName()))
                    .setDataType(field.getDataType());
            if (field.isConst()) { // only set when it differs from the proto default
                fieldDesc.setIsConst(true);
            }
        }
        return desc;
    }

    public static String getCName(EnvProcessor.GeneratorOptions options, CLibStruct annotation, String name) {
        return TypeMap.toStructCName(getCPrefix(options, annotation), name);
    }

    public static String getCPrefix(EnvProcessor.GeneratorOptions options, CLibStruct annotation) {
        return AnnotationUtil.getCPrefix(options, annotation.prefix()) + "_";
    }

    public String getCName() {
        return getCName(getOptions(), annotation, getName());
    }

    public String getCPrefix() {
        return getCPrefix(getOptions(), annotation);
    }

    /**
     * The name native-image probes for, which may differ from {@link #getName()} when the
     * author names the struct in {@code @CLibStruct}.
     */
    public String getCStructName() {
        return element.getAnnotation(CStruct.class).value();
    }

    /**
     * @return the struct name, which defaults to the {@code @CStruct} value
     */
    public String getName() {
        return getName(element);
    }

    public static String getName(TypeElement element) {
        var annotation = element.getAnnotation(CLibStruct.class);
        if (annotation != null && !annotation.value().isEmpty()) {
            return annotation.value();
        }
        var cStruct = element.getAnnotation(CStruct.class);
        return cStruct == null ? "" : cStruct.value();
    }

    public TypeElement getElement() {
        return element;
    }

    public ClassName getClassName() {
        return ClassName.get(element);
    }

    public List<StructField> getFields() {
        return fields;
    }

    /**
     * The view's own accessors, each already resolved to the field it reads or writes, so
     * that a generated implementation cannot disagree with the probed layout.
     */
    public List<StructAccessor> getAccessors() {
        return accessors;
    }

    private String getQualifiedName() {
        return String.valueOf(element.getQualifiedName());
    }

    private ProcessingException error(String message, Object... args) {
        return new ProcessingException(element, message, args);
    }

    public static class StructField {

        StructField(String name, DataType dataType, boolean isConst) {
            this.name = name;
            this.dataType = dataType;
            this.isConst = isConst;
        }

        public String getName() {
            return name;
        }

        public DataType getDataType() {
            return dataType;
        }

        /**
         * @return true if the public C face declares the pointer as {@code const}
         */
        public boolean isConst() {
            return isConst;
        }

        final String name;
        final DataType dataType;
        final boolean isConst;
        boolean hasGetter = false;
        boolean hasSetter = false;

    }

    public static class StructAccessor {

        StructAccessor(String methodName, String fieldName, TypeName javaType, boolean getter) {
            this.methodName = methodName;
            this.fieldName = fieldName;
            this.javaType = javaType;
            this.getter = getter;
        }

        public String getMethodName() {
            return methodName;
        }

        public String getFieldName() {
            return fieldName;
        }

        /**
         * @return return type for a getter, parameter type for a setter
         */
        public TypeName getJavaType() {
            return javaType;
        }

        public boolean isGetter() {
            return getter;
        }

        final String methodName;
        final String fieldName;
        final TypeName javaType;
        final boolean getter;

    }

    final CLibStruct annotation;

    final List<StructField> fields;

    final List<StructAccessor> accessors = new ArrayList<>();

    private static final List<String> ACCESSOR_PREFIXES = List.of("get", "set");

    private static final TypeName STRING_TYPE = ClassName.get(String.class);

}
