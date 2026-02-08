package org.xvm.javajit.builders;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;

import java.lang.constant.ClassDesc;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.TypeSystem;

/**
 * The builder for Module types.
 */
public class ModuleBuilder
        extends CommonBuilder {

    public ModuleBuilder(TypeSystem typeSystem, TypeConstant type) {
        super(typeSystem, type);
    }

    @Override
    protected ClassDesc getSuperCD() {
        return CD_nModule;
    }

    @Override
    public boolean assembleImplClass(String className, ClassBuilder classBuilder) {
        classBuilder
            .withFlags(ClassFile.ACC_PUBLIC)
            .withSuperclass(CD_nModule)
            ;
        return true;
    }
}
