package org.xvm.javajit.builders;

import java.lang.classfile.ClassBuilder;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.TypeSystem;

/**
 * The builder for native types that uses an existing Java class to augment with the Ecstasy natural
 * code.
 */
public class IntrinsicBuilder extends CommonBuilder {

    public IntrinsicBuilder(TypeSystem typeSystem, TypeConstant type) {
        super(typeSystem, type);
    }

    @Override
    public void assembleImplClass(String className, ClassBuilder classBuilder) {
        // IntrinsicBuilder uses the native class attributes
    }

    @Override
    protected void assembleImplMethods(String className, ClassBuilder classBuilder) {
        // super.assembleImplMethods(className, classBuilder);
        // TODO: only add methods that don't exist at the native class
    }

    @Override
    public void assemblePure(String className, ClassBuilder builder) {
    }
}
