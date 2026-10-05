package org.xvm.javajit.builders;

import java.lang.classfile.ClassBuilder;

import java.lang.constant.ClassDesc;

import org.xvm.javajit.TypeSystem;
import org.xvm.javajit.TypeSystem.Artifact;

/**
 * The builder for Enum base types.
 *
 * <p>It overrides the CommonBuilder to do the following:
 *   - supply the xEnum class as a super class
 *   - implement the "enumeration" property
 */
public class EnumBuilder
        extends CommonBuilder
        implements EnumBuilderSupport {
    /**
     * Create an {@link EnumBuilder}.
     *
     * @param typeSystem  the {@link TypeSystem}
     * @param art         the {@link Artifact}
     */
    public EnumBuilder(TypeSystem typeSystem, Artifact art) {
        super(typeSystem, art);
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
