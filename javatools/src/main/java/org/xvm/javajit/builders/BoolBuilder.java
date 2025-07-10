package org.xvm.javajit.builders;

import java.lang.constant.ClassDesc;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.TypeSystem;


/**
 * The builder for Boolean type.
 */
public class BoolBuilder extends IntrinsicBuilder {

    public BoolBuilder(TypeSystem typeSystem, TypeConstant type) {
        super(typeSystem, type);
    }

    private static final ClassDesc CD_xBool =
        ClassDesc.of(org.xvm.javajit.intrinsic.xBool.class.getName());

}
