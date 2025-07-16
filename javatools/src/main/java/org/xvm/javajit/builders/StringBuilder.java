package org.xvm.javajit.builders;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassModel;

import java.lang.constant.ClassDesc;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.TypeSystem;

/**
 * The builder for String type.
 */
public class StringBuilder extends AugmentingBuilder {

    public StringBuilder(TypeSystem typeSystem, TypeConstant type, ClassModel model) {
        super(typeSystem, type, model);
    }

    private static final ClassDesc CD_xStr =
        ClassDesc.of(org.xvm.javajit.intrinsic.xStr.class.getName());

    @Override
    protected void assembleImplMethods(String className, ClassBuilder classBuilder) {
        // super.assembleImplMethods(className, classBuilder);
        // TODO
    }
}
