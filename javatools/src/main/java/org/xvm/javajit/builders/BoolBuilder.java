package org.xvm.javajit.builders;

import java.lang.classfile.ClassModel;
import java.lang.constant.ClassDesc;

import org.xtclang.ecstasy.Boolean;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.TypeSystem;


/**
 * The builder for Boolean type.
 */
public class BoolBuilder extends AugmentingBuilder {

    public BoolBuilder(TypeSystem typeSystem, TypeConstant type, ClassModel model) {
        super(typeSystem, type, model);
    }

    private static final ClassDesc CD_xBool =
        ClassDesc.of(Boolean.class.getName());

}
