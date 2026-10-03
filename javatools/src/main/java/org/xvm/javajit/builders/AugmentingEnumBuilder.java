package org.xvm.javajit.builders;

import java.lang.classfile.ClassBuilder;

import java.lang.classfile.ClassModel;

import java.lang.constant.ClassDesc;

import org.xvm.javajit.TypeSystem;
import org.xvm.javajit.TypeSystem.Artifact;

/**
 * The augmenting builder for native Enum base types.
 *
 * <p>It overrides the {@link AugmentingBuilder} to do the following:
 *   - supply the xEnum class as a super class
 *   - implement the "enumeration" property
 */
public class AugmentingEnumBuilder
        extends AugmentingBuilder
        implements EnumBuilderSupport {
    /**
     * Create an {@link AugmentingEnumBuilder}.
     *
     * @param typeSystem  the {@link TypeSystem}
     * @param art         the {@link Artifact}
     * @param model       the {@link ClassModel} to augment, if {@code null} an empty model will
     *                    be used
     */
    public AugmentingEnumBuilder(TypeSystem typeSystem, Artifact art, ClassModel model) {
        super(typeSystem, art, model);
    }

    @Override
    public boolean assembleClass(ClassBuilder classBuilder) {
        assembleEnumClass(this, classBuilder);
        return true;
    }

    @Override
    public ClassDesc getSuperCD() {
        return CD_nEnum;
    }

    @Override
    protected void assembleProperties(ClassBuilder classBuilder) {
        assembleEnumProperties(this, classBuilder);
        super.assembleProperties(classBuilder);
    }

    @Override
    protected void assembleMethods(ClassBuilder classBuilder) {
        assembleEnumMethods(this, classBuilder);
        super.assembleMethods(classBuilder);
    }
}
