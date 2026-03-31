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

        DELEGATE_TYPE = pool().ensureEcstasyTypeConstant("collections.Array.ArrayDelegate");
        specialization = type.isParamsSpecified();
    }

    protected final TypeConstant DELEGATE_TYPE;
    protected final boolean specialization;

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
    protected boolean shouldAddInterface(TypeConstant type) {
        if (type.isA(DELEGATE_TYPE)) {
            // skip the ArrayDelegate
            return false;
        }
        return super.shouldAddInterface(type);
    }

    @Override
    protected void assembleImplMethods(String className, ClassBuilder classBuilder) {
        // don't create any methods for array specializations
        if (!specialization) {
            super.assembleImplMethods(className, classBuilder);
        }
    }

    @Override
    protected void assembleImplProperties(String className, ClassBuilder classBuilder) {
        // don't create any properties for array specializations
        if (!specialization) {
            super.assembleImplProperties(className, classBuilder);
        }
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
