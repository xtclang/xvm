package org.xvm.javajit.annotations;

import java.lang.annotation.Retention;

import java.lang.annotation.RetentionPolicy;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassElement;

import org.xvm.javajit.builders.AugmentingBuilder;

/**
 * Marks a bridge class with JIT member annotations or a member whose code the JIT generates.
 * <p>
 * Use bare {@code @JitGenerated} on a class or interface so the {@link AugmentingBuilder} knows
 * to look for member annotations. A class with JIT member annotations must have this marker.
 * <p>
 * On a method, bare {@code @JitGenerated} compiles the corresponding Ecstasy body. Use
 * {@code @JitGenerated(MyGenerator.class)} to supply a custom generator instead.
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface JitGenerated {
    /**
     * The default {@link NaturalCode} tells the JIT to compile the corresponding Ecstasy body.
     * The value is ignored on a class-level marker.
     *
     * @return the generator class for the annotated member
     */
    Class<? extends Generator<?>> value() default NaturalCode.class;

    /**
     * A Java code generator that handles customizations to classes processed by the
     * {@link AugmentingBuilder}.
     * <p>
     * Each instance of {@link Generator} is typically used to generate a specific piece of Java
     * code, for example customize the contents of a method.
     */
    interface Generator<E extends ClassElement> {
        /**
         * Generate the Java code for the specified element.
         *
         * @param builder       the current {@link AugmentingBuilder}
         * @param element       the element of the {@link Class} that this annotation applied to
         * @param classBuilder  the {@link ClassBuilder} to use to generate code
         */
        void generate(AugmentingBuilder builder, E element, ClassBuilder classBuilder);
    }

    /**
     * The default generator sentinel. The JIT should compile the Ecstasy body.
     */
    class NaturalCode implements Generator<ClassElement> {
        /**
         * A singleton instance of the {@link NaturalCode} sentinel.
         */
        public static final NaturalCode INSTANCE = new NaturalCode();

        @Override
        public void generate(AugmentingBuilder builder, ClassElement element, ClassBuilder classBuilder) {
        }
    }
}
