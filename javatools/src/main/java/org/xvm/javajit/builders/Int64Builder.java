package org.xvm.javajit.builders;

import java.lang.classfile.ClassModel;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.TypeSystem;

/**
 * The builder for Int64 type.
 */
public class Int64Builder extends AugmentingBuilder {

    public Int64Builder(TypeSystem typeSystem, TypeConstant type, ClassModel model) {
        super(typeSystem, type, model);
    }

}
