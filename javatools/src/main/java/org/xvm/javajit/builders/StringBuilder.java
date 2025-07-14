package org.xvm.javajit.builders;

import java.lang.classfile.ClassBuilder;

import java.lang.constant.ClassDesc;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.TypeSystem;

/**
 * The builder for String type.
 */
public class StringBuilder extends IntrinsicBuilder {

    public StringBuilder(TypeSystem typeSystem, TypeConstant type) {
        super(typeSystem, type);
    }

    private static final ClassDesc CD_xStr =
        ClassDesc.of(org.xvm.javajit.intrinsic.xStr.class.getName());

    @Override
    protected void assembleImplMethods(String className, ClassBuilder classBuilder) {
        // super.assembleImplMethods(className, classBuilder);
        // TODO
    }
}
