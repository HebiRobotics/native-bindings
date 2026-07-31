package us.hebi.graalvm.bindings.generator.generators;

import com.google.common.base.Predicate;
import us.hebi.graalvm.bindings.model.ClassDescription;
import us.hebi.graalvm.bindings.model.MethodDescription;
import us.hebi.quickbuf.ProtoMessage;
import us.hebi.quickbuf.RepeatedMessage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * @author Florian Enner
 * @since 04 Aug 2025
 */
public class ProtoSortUtil {

    /**
     * Sorts methods such that property getters/setters are first,
     * followed by everything else in their standard order.
     */
    public static void sortPropertyMethods(RepeatedMessage<MethodDescription> methods) {
        sortInPlace(methods, (a, b) -> {
            // Constructors first
            if (a.getIsConstructor() != b.getIsConstructor()) {
                return a.getIsConstructor() ? -1 : 1;
            }

            // For non-properties, keep the original declaration order
            boolean aIsProp = a.hasPropertyName();
            boolean bIsProp = b.hasPropertyName();
            if (!aIsProp && !bIsProp) {
                return 0;
            }

            // Move properties up top
            if (aIsProp != bIsProp) {
                return aIsProp ? -1 : 1;
            }

            // Sort properties alphabetically
            int nameComp = a.getPropertyName().compareTo(b.getPropertyName());
            if (nameComp != 0) {
                return nameComp;
            }

            // For the same property, move the getter before the setter
            // Logic: Getters have 0 parameters, Setters have > 0
            int aParams = a.getParameters().length();
            int bParams = b.getParameters().length();
            return Integer.compare(aParams, bParams);

        });
    }

    /**
     * Sorts such that classes are alphabetically sorted, inheritance trees are together, and utility
     * classes are on the bottom
     *
     * @param classes
     */
    public static void sortClassDescriptions(RepeatedMessage<ClassDescription> classes) {
        // Sort alphabetically first
        sortInPlace(classes, Comparator.comparing(o -> o.getType().getTypeName()));

        // Split into instantiable and util classes
        List<ClassDescription> list = new ArrayList<>();
        List<ClassDescription> utilities = new ArrayList<>();
        for (ClassDescription classDescription : classes) {
            if (classDescription.getInstantiable()) {
                list.add(classDescription.clone());
            } else {
                utilities.add(classDescription.clone());
            }
        }

        // Then sort such that the hierarchy is defined in order
        List<ClassDescription> result = new ArrayList<>();
        buildHierarchyTree(list, result);
        result.addAll(utilities);

        // Overwrite original message contents
        classes.clearQuick();
        for (ClassDescription classDescription : result) {
            sortPropertyMethods(classDescription.getMethods());
            classes.next().copyFrom(classDescription);
        }

    }

    private static void buildHierarchyTree(List<ClassDescription> source, List<ClassDescription> target) {
        Predicate<ClassDescription> mayBeAdded = clazz -> {
            if (!clazz.hasParent()) return true;
            for (ClassDescription classDescription : target) {
                if (clazz.getType().equals(classDescription.getType())) {
                    return true;
                }
            }
            return false;
        };

        while (source.size() != target.size()) {
            for (ClassDescription next : source) {
                if (mayBeAdded.apply(next) && !target.contains(next)) {
                    target.add(next);
                    addAllDirectChildren(next, source, target);
                }
            }
        }

    }

    private static void addAllDirectChildren(ClassDescription parent, List<ClassDescription> source, List<ClassDescription> target) {
        for (ClassDescription next : source) {
            if (next.getParent().equals(parent.getType()) && !target.contains(next)) {
                target.add(next);
                addAllDirectChildren(next, source, target);
            }
        }
    }

    public static <MessageType extends ProtoMessage<MessageType>> void sortInPlace(RepeatedMessage<MessageType> input, Comparator<MessageType> comparator) {
        // Sort a temporary copy
        var tempList = new ArrayList<MessageType>();
        for (MessageType msg : input) {
            tempList.add(msg.clone());
        }
        tempList.sort(comparator);

        // Copy into original list
        input.clearQuick();
        for (MessageType msg : tempList) {
            input.next().copyFrom(msg);
        }
    }

}
