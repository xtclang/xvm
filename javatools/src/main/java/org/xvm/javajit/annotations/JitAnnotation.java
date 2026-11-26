package org.xvm.javajit.annotations;

import java.lang.classfile.Annotation;
import java.lang.classfile.AnnotationElement;
import java.lang.classfile.AnnotationValue;
import java.lang.classfile.AnnotationValue.OfAnnotation;
import java.lang.classfile.AnnotationValue.OfArray;
import java.lang.classfile.AnnotationValue.OfBoolean;
import java.lang.classfile.AnnotationValue.OfClass;
import java.lang.classfile.AnnotationValue.OfConstant;
import java.lang.classfile.AnnotationValue.OfEnum;
import java.lang.classfile.AnnotationValue.OfInt;
import java.lang.classfile.AnnotationValue.OfString;
import java.lang.classfile.AttributedElement;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFileElement;

import java.lang.classfile.attribute.RuntimeVisibleAnnotationsAttribute;

import java.lang.constant.ClassDesc;

import java.lang.invoke.MethodHandles;

import java.lang.reflect.Constructor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import java.util.function.Function;

import org.xvm.javajit.Builder;

/**
 * An implementation of a Java annotation used to create annotation instances at runtime from a
 * Java class file API {@link Annotation} creating a simpler API to access native code annotation
 * values from the JIT.
 */
public class JitAnnotation
        implements java.lang.annotation.Annotation {
    /**
     * Create a {@link JitAnnotation} from a Java class file API {@link Annotation}.
     *
     * @param annotation  the wrapped {@link Annotation}
     */
    protected JitAnnotation(Annotation annotation) {
        Class<?> annotationType = annotationType();
        assert annotationType != null && annotationType.isAssignableFrom(this.getClass());
        ClassDesc cd = ClassDesc.of(annotationType.getName());
        assert annotation.classSymbol().equals(cd);

        this.annotation = annotation;
        this.elements = new HashMap<>();
        for (AnnotationElement element : annotation.elements()) {
            elements.put(element.name().stringValue(), element);
        }
    }

    /**
     * The wrapped {@link Annotation}.
     */
    protected final Annotation annotation;

    /**
     * The {@link Annotation} elements keyed by name.
     */
    protected final Map<String, AnnotationElement> elements;

    /**
     * The {@link Annotation} element values keyed by element name.
     */
    protected final Map<String, Object> values = new HashMap<>();

    /**
     * The {@link Annotation} factories used to create {@link JitAnnotation} subclasses, keyed by
     * the subclass {@link ClassDesc}.
     */
    protected static final Map<ClassDesc, Function<Annotation, JitAnnotation>> annotationFactories = new HashMap<>();

    @Override
    public Class<? extends java.lang.annotation.Annotation> annotationType() {
        assert annotation != null;
        return Builder.toClass(annotation.classSymbol());
    }

    /**
     * @return the wrapped {@link Annotation} {@link ClassDesc}
     */
    public ClassDesc annotationCD() {
        return annotation.classSymbol();
    }

    /**
     * @return the value of a named annotation element, or {@code null} if the element is not found
     */
    public <V> V getValue(String name) {
        return getValue(name, null);
    }

    /**
     * @return the value of a named annotation element, or the default value if the element is not
     * found
     */
    @SuppressWarnings("unchecked")
    public <V> V getValue(String name, V dflt) {
        return (V) values.computeIfAbsent(name, k -> {
            if (elements.isEmpty() && dflt != null) {
                return dflt;
            }
            AnnotationElement element = elements.get(name);
            if (element == null) {
                throw new IllegalStateException("annotation member \"" + name + "\" not found");
            }
            return valueFromAnnotationValue(element.value());
        });
    }

    /**
     * Return the value of a named annotation element.
     *
     * @param clz   the expected type of the element value
     * @param name  the name of the element
     * @param <V>   the expected type of the element value
     *
     * @return the value of a named annotation element
     *
     * @throws IllegalStateException if the named element is not found
     */
    public <V extends AnnotationValue> V assertValue(Class<V> clz, String name) {
        AnnotationElement element = elements.get(name);
        if (element == null) {
            return null;
        }
        AnnotationValue value = element.value();
        if (!clz.isInstance(value)) {
            throw new IllegalStateException("annotation member \"" + name
                    + "\" is not of type " + clz.getName());
        }
        return clz.cast(value);
    }

    /**
     * Return the boolean value of a named annotation element.
     *
     * @param name  the name of the element
     *
     * @return the boolean value of a named annotation element
     *
     * @throws IllegalStateException if the named element is not found
     */
    public boolean booleanValue(String name) {
        return booleanValue(name, null);
    }

    /**
     * Return the boolean value of a named annotation element, or the default value if the
     * element is not found.
     *
     * @param name  the name of the element
     * @param dflt  the default value
     *
     * @return the boolean value of a named annotation element, or the default value if the
     *         element is not found.
     */
    public boolean booleanValue(String name, Boolean dflt) {
        OfBoolean element = assertValue(OfBoolean.class, name);
        if (element == null) {
            if (dflt != null) {
                return dflt;
            }
            throw new IllegalStateException("annotation member \"" + name + "\" not found");
        }
        return element.booleanValue();
    }

    /**
     * Return the String value of a named annotation element.
     *
     * @param name  the name of the element
     *
     * @return the String value of a named annotation element
     *
     * @throws IllegalStateException if the named element is not found
     */
    public String stringValue(String name) {
        return stringValue(name, null);
    }

    /**
     * Return the String value of a named annotation element, or the default value if the
     * element is not found.
     *
     * @param name  the name of the element
     * @param dflt  the default value
     *
     * @return the String value of a named annotation element, or the default value if the
     *        element is not found.
     */
    public String stringValue(String name, String dflt) {
        OfString element = assertValue(OfString.class, name);
        if (element == null) {
            if (dflt != null) {
                return dflt;
            }
            throw new IllegalStateException("annotation member \"" + name + "\" not found");
        }
        return element.stringValue();
    }

    /**
     * Return the int value of a named annotation element.
     *
     * @param name  the name of the element
     *
     * @return the int value of a named annotation element
     *
     * @throws IllegalStateException if the named element is not found
     */
    public int intValue(String name) {
        return intValue(name, null);
    }

    /**
     * Return the int value of a named annotation element, or the default value if the
     * element is not found.
     *
     * @param name  the name of the element
     * @param dflt  the default value
     *
     * @return the String value of a named annotation element, or the default value if the
     *        element is not found.
     */
    public int intValue(String name, Integer dflt) {
        AnnotationValue.OfInt element = assertValue(OfInt.class, name);
        if (element == null) {
            if (dflt != null) {
                return dflt;
            }
            throw new IllegalStateException("annotation member \"" + name + "\" not found");
        }
        return element.intValue();
    }

    /**
     * Return the real constant value of an annotation element's value.
     *
     * @param value  the class file API annotation value
     * @param <V>    the expected type of the value
     *
     * @return the real constant value of an annotation element's value
     */
    @SuppressWarnings("unchecked")
    private <V> V valueFromAnnotationValue(AnnotationValue value) {
        return (V) switch (value) {
            case OfString     ofString     -> ofString.stringValue();
            case OfConstant   ofConstant   -> ofConstant.resolvedValue();
            case OfClass      ofClass      -> Builder.toClass(ofClass.classSymbol());
            case OfAnnotation ofAnnotation -> create(ofAnnotation.annotation());
            case OfArray      ofArray      -> toArray(ofArray);
            case OfEnum       ofEnum       -> toRuntimeEnum(ofEnum);
        };
    }

    /**
     * Convert an annotation enum value to a real enum value instance.
     *
     * @param ofEnum  the class file API enum value
     *
     * @return the real enum value the class file enum value represents
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private Enum<?> toRuntimeEnum(OfEnum ofEnum) {
        try {
            // Resolves the ClassDesc to a Class<?> using the current classloader
            Class<?> rawClass = ofEnum.classSymbol().resolveConstantDesc(MethodHandles.lookup());
            if (!rawClass.isEnum()) {
                throw new IllegalArgumentException(rawClass.getName() + " is not an enum");
            }

            String name = ofEnum.constantName().stringValue();
            return Enum.valueOf((Class<? extends Enum>) rawClass, name);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Convert an annotation array value to a real array value instance.
     *
     * @param ofArray  the class file API array value
     *
     * @return the real array value the class file array value represents
     */
    private List<?> toArray(OfArray ofArray) {
        return ofArray.values().stream()
                .map(this::valueFromAnnotationValue)
                .toList();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        JitAnnotation that = (JitAnnotation) o;
        return Objects.equals(annotation, that.annotation);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(annotation);
    }

    @Override
    public String toString() {
        return "JitAnnotation{" +
                "annotation=" + annotation +
                '}';
    }

    // ----- static helper methods -----------------------------------------------------------------

    /**
     * Return a map of all the annotations on a possibly annotated {@link ClassFileElement}.
     *
     * @param element  the element to obtain the annotations for
     *
     * @return a map of all the annotations on the {@link ClassFileElement} or an empty map if
     *         the element is not annotated.
     */
    public static Map<ClassDesc, JitAnnotation> getAnnotations(ClassFileElement element) {
        Map<ClassDesc, JitAnnotation> map = new HashMap<>();
        if (element instanceof AttributedElement attributedElement) {
            List<Annotation> annotations = attributedElement
                    .findAttribute(Attributes.runtimeVisibleAnnotations())
                    .map(RuntimeVisibleAnnotationsAttribute::annotations)
                    .orElse(List.of());

            for (Annotation annotation : annotations) {
                JitAnnotation jitAnnotation = JitAnnotation.create(annotation);
                if (jitAnnotation != null) {
                    map.put(jitAnnotation.annotationCD(), jitAnnotation);
                }
            }
        }
        return map;
    }

    /**
     * Return the specified {@link JitAnnotation} type from a possibly annotated
     * {@link ClassFileElement}
     *
     * @param element the class file API element to search for the annotation
     * @param cd      the {@link ClassDesc} of the annotation to find
     * @param <A>     the expected {@link JitAnnotation} type
     *
     * @return the {@link JitAnnotation} from the specified {@link ClassFileElement} or
     *         {@code null} if the element is not annotated with the requested annotation
     */
    public static <A extends JitAnnotation> A create(ClassFileElement element, ClassDesc cd) {
        Annotation annotation = findAnnotation(element, cd);
        return annotation != null ? create(annotation) : null;
    }

    /**
     * Create a {@link JitAnnotation} from the given class file API {@link Annotation}.
     *
     * @param annotation the class file API annotation
     * @param <A>        the type of {@link JitAnnotation} to create
     *
     * @return the {@link JitAnnotation} the class file API annotation represents
     */
    @SuppressWarnings("unchecked")
    public static <A extends JitAnnotation> A create(Annotation annotation) {
        ClassDesc cd  = annotation.classSymbol();
        Function<Annotation, JitAnnotation> factory
                = annotationFactories.computeIfAbsent(cd, JitAnnotation::createAnnotationFactory);
        return (A) factory.apply(annotation);
    }

    /**
     * Create a factory function that can be used to create {@link JitAnnotation} instances.
     * <p>
     * If the annotation type represented by the {@link ClassDesc} has an inner class named
     * {@code Literal} that has a constructor taking a single {@link Annotation} parameter, then
     * that {@code Literal} constructor will be used as the factory. Otherwise the plain
     * {@link JitAnnotation} constructor will be used.
     *
     * @param cd  the {@link ClassDesc} of the annotation
     *
     * @return the factory function
     */
    private static Function<Annotation, JitAnnotation> createAnnotationFactory(ClassDesc cd) {
        try {
            ClassDesc                            cdLit = cd.nested("Literal");
            Class<? extends JitAnnotation>       clz   = Builder.toClass(cdLit);
            Constructor<? extends JitAnnotation> con   = clz.getDeclaredConstructor(Annotation.class);
            return ann -> {
                try {
                    return con.newInstance(ann);
                } catch (ReflectiveOperationException e) {
                    throw new RuntimeException(e);
                }
            };
        } catch (Exception _) {}
        return JitAnnotation::new;
    }

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
}
