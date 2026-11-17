package org.xvm.javajit.annotations;

import java.lang.annotation.Retention;

import java.lang.annotation.RetentionPolicy;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassElement;

import org.xvm.javajit.builders.AugmentingBuilder;

/**
 * An annotation to indicate that the annotated source element is handled as a special case by
 * the {@link AugmentingBuilder}.
 * <p>
 * A class that contains elements annotated with {@link JitGenerated} elements must itself be
 * annotated with the {@link JitGenerated} annotation. Any generator class specified for the
 * class level annotation is ignored.
 * <p>
 * The default generator class {@link #value()} is the {@link NaturalCode} generator, which
 * indicates that the JIT will produce code using the corresponding natural Ecstasy code for the
 * annotated element.
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface JitGenerated {
    /**
     * The {@link Class} of the {@link Generator} to use to generate custom code.
     * <p>
     * The default value is the {@link NaturalCode} generator, which indicates that the JIT will
     * produce code using the corresponding natural Ecstasy code for the annotated element.
     *
     * @return the {@link Class} of the {@link Generator} to use to generate custom code for the
     * annotated element.
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
     * A no-op generator to indicate that the code should be produced from the natural code for
     * the annotated element.
     */
    class NaturalCode implements Generator<ClassElement> {
        /**
         * A singleton instance of the {@link NaturalCode} generator.
         */
        public static final NaturalCode INSTANCE = new NaturalCode();

        @Override
        public void generate(AugmentingBuilder builder, ClassElement element, ClassBuilder classBuilder) {
        }
    }
}
