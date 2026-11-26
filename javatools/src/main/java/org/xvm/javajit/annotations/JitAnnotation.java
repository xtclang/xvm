package org.xvm.javajit.annotations;

import java.lang.classfile.Annotation;
import java.lang.classfile.AnnotationElement;
import java.lang.classfile.AnnotationValue;
import java.lang.classfile.AttributedElement;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFileElement;

import java.lang.classfile.attribute.RuntimeVisibleAnnotationsAttribute;

import java.lang.constant.ClassDesc;

import java.util.List;

/**
 * Utilities for looking up JIT annotations in native class files.
 */
public class JitAnnotation {
    private JitAnnotation() {}

    /**
     * @return {@code true} if the specified element is annotated with the specified annotation
     */
    public static boolean isAnnotated(ClassFileElement element, ClassDesc annotation) {
        return findAnnotation(element, annotation) != null;
    }

    /**
     * @return if the element is annotated with the specified annotation, then return it, otherwise
     *         return {@code null}
     */
    public static Annotation findAnnotation(ClassFileElement element, ClassDesc annotation) {
        if (element instanceof AttributedElement attributedElement) {
            List<Annotation> annotations = attributedElement
                    .findAttribute(Attributes.runtimeVisibleAnnotations())
                    .map(RuntimeVisibleAnnotationsAttribute::annotations)
                    .orElse(null);

            if (annotations != null) {
                return annotations.stream()
                        .filter(ann -> ann.classSymbol().equals(annotation))
                        .findFirst()
                        .orElse(null);
            }
        }
        return null;
    }

    /**
     * @return the explicitly specified {@code value} element's value, or {@code null} if omitted
     */
    public static AnnotationValue findValue(Annotation annotation) {
        for (AnnotationElement element : annotation.elements()) {
            if (element.name().equalsString("value")) {
                return element.value();
            }
        }
        return null;
    }
}
