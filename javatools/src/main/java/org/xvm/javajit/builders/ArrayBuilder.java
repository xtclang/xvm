package org.xvm.javajit.builders;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassModel;

import java.lang.constant.ClassDesc;

import org.xvm.asm.constants.MethodInfo;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.JitMethodDesc;
import org.xvm.javajit.TypeSystem;

/**
 * The builder for Array types.
 */
public class ArrayBuilder extends AugmentingBuilder {

    public ArrayBuilder(TypeSystem typeSystem, TypeConstant type, ClassModel model) {
        super(typeSystem, type, model);
    }

    @Override
    public ClassDesc ensureClassDesc(TypeConstant type) {
        return type.isArray()
            ? CD_Array
            : super.ensureClassDesc(type);
    }

    @Override
    public String ensureJitClassName(TypeConstant type) {
        return type.isArray()
            ? N_Array
            : super.ensureJitClassName(type);
    }

    @Override
    protected void assembleMethod(String className, ClassBuilder classBuilder, MethodInfo method, String jitName, JitMethodDesc jmd) {
        // all constructors are native
        if (!method.isCtorOrValidator()) {
            super.assembleMethod(className, classBuilder, method, jitName, jmd);
        }
    }

    @Override
    protected void assembleNew(String className, ClassBuilder classBuilder,
                               MethodInfo constructor, String jitName, JitMethodDesc jmd) {
        // all constructors are native
    }
}
