package us.hebi.graalvm.bindings.generator.generators;

import com.google.common.base.CaseFormat;
import us.hebi.graalvm.bindings.annotations.CLibTypeDef;
import us.hebi.graalvm.bindings.generator.annotations.*;
import us.hebi.graalvm.bindings.generator.bindings.BindingGenerator;
import us.hebi.graalvm.bindings.generator.bindings.features.CocoaFeature;
import us.hebi.graalvm.bindings.generator.bindings.features.LibraryInfoFeature;
import us.hebi.graalvm.bindings.model.*;
import us.hebi.quickbuf.JsonSink;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import static us.hebi.quickbuf.ProtoUtil.*;

/**
 * @author Florian Enner
 * @since 21 Feb 2024
 */
public class LibraryDescriptionGenerator extends AbstractGenerator {

    @Override
    protected void process(AnnotatedCLibClass apiClass) {
        var classDesc = description.getMutableClasses().next()
                .setCDestructorName(apiClass.getDestructorCEntryName())
                .setInstantiable(apiClass.enableInstancing())
                .setType(apiClass.getNativeType().toDescription());

        apiClass.getIsolateName().ifPresent(classDesc::setIsolateName);

        apiClass.getParent().map(AnnotatedClibType::toDescription).ifPresent(parent -> {
            classDesc.setParent(parent);
            classDesc.clearCDestructorName();
        });
        apiClass.getToParentCast().ifPresent(classDesc::setToParentCastMethod);

        for (AnnotatedCLibMethod method : apiClass.getPublicApiMethods()) {
            var methodDesc = classDesc.getMutableMethods().next()
                    .setName(method.getMethodName())
                    .setLowerUnderscoreName(CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, method.getMethodName()))
                    .setCName(method.getCEntryPointName())
                    .setReturnType(method.getReturnType().toDescription());

            // Only set to non-standard values
            if (method.isStatic()) methodDesc.setIsStatic(true);
            if (method.isConstructor()) methodDesc.setIsConstructor(true);

            method.getPropertyName().ifPresent(methodDesc::setPropertyName);

            for (MethodFlag flag : method.getFlags()) {
                methodDesc.addFlags(flag);
            }

            // Change the C type of throwing void methods so we can return a status
            if (methodDesc.getReturnType().getDataType() == DataType.VOID && method.isThrowing()) {
                methodDesc.getMutableReturnType().setDataType(DataType.VOID_WITH_ERROR_CODE);
            }

            method.getDocumentation().ifPresent(methodDesc::setDocumentation);
            method.getThrowsOnComment().ifPresent(methodDesc::setThrowsOn);

            // TODO: move verification to a better place
            if (method.isConstructor()) {
                checkArgument(method.isStatic(), "Constructors must be static");
                checkArgument(classDesc.getInstantiable(), "Classes with constructors must have instancing enabled");
                checkArgument(Objects.equals(method.getReturnType().getCEntryType(), apiClass.getNativeType().getCEntryType()), "Constructors must return their containing class type");
            }

            // Custom method overloads
            for (var overload : method.getCppOverloads()) {
                methodDesc.addCppOverloads(overload);
            }
            method.getPythonOverload().ifPresent(methodDesc::setPythonOverload);
            method.getMatlabOverload().ifPresent(methodDesc::setMatlabOverload);
            method.getCppBody().ifPresent(methodDesc::setCppBody);

            for (AnnotatedClibType parameter : method.getParameters()) {
                // Isolates are handled by the C wrapper layer
                if (parameter.isIsolateOrThread()) continue;

                // Only add non-isolates
                methodDesc.getMutableParameters().next()
                        .copyFrom(parameter.toDescription())
                        .setParamName(parameter.getName())
                        .setLowerUnderscoreParamName(CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, parameter.getName()));
            }

        }

        for (AnnotatedCLibEnum nestedEnum : apiClass.getNestedEnums()) {
            description.addEnums(nestedEnum.toDescription());
        }

        for (CLibTypeDef typeDef : apiClass.getTypeDefs()) {
            description.getMutableTypedefs().next()
                    .setName(typeDef.name())
                    .setCDefinition(typeDef.cDefinition())
                    .setCppDefinition(typeDef.cppDefinition())
                    .setPythonDefinition(typeDef.pythonDefinition());
        }

    }

    public void finish() {
        var options = getOptions();
        List.of(
                new CocoaFeature(options),
                new LibraryInfoFeature(options)
        ).forEach(feature -> feature.saveToApiDescription(description));

        // Set global properties
        description.setName(options.getLibName());
        description.setVersion(options.getLibVersion());
        description.setBuild(options.getLibBuild());
        options.getDownloadUrl().ifPresent(description::setDownloadUrl);
        for (var namespace : options.getNamespace().split("\\.")) {
            description.getMutableNamespace().next().copyFrom(namespace);
        }
        if (options.enableGraphics()) description.setEnableGraphics(true);
        description.setCPrefix(options.getCPrefix());

        // Sort classes by name so the output-diffs are predictable
        ProtoSortUtil.sortClassDescriptions(description.getClasses());
        ProtoSortUtil.sortInPlace(description.getEnums(), Comparator.comparing(EnumDescription::getName));

        // Fill in children information
        List<TypeDescription> children = new ArrayList<>();
        for (ClassDescription parent : description.getClasses()) {
            children.clear();
            for (ClassDescription child : description.getClasses()) {
                if (child.getParent().equals(parent.getType())) {
                    children.add(child.getType());
                }
            }
            children.forEach(parent::addChildren);
        }
    }

    public void writeTo(BindingGenerator.Filer filer) throws IOException {
        var content = JsonSink.newPrettyInstance()
                .writeMessage(description)
                .toString();
        filer.createFile("..", "api.json", writer -> writer.write(content));
    }

    public LibraryDescription getDescription() {
        return description;
    }

    final LibraryDescription description = LibraryDescription.newInstance();

}
