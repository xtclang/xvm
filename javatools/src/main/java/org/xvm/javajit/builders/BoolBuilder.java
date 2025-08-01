package org.xvm.javajit.builders;

import java.lang.classfile.ClassModel;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.TypeSystem;


/**
 * The builder for Boolean type.
 */
public class BoolBuilder extends AugmentingBuilder {

    public BoolBuilder(TypeSystem typeSystem, TypeConstant type, ClassModel model) {
        super(typeSystem, type, model);
    }
}
