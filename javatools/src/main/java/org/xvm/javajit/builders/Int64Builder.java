package org.xvm.javajit.builders;

import java.lang.constant.ClassDesc;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.TypeSystem;

/**
 * The builder for Int64 type.
 */
public class Int64Builder extends IntrinsicBuilder {

    public Int64Builder(TypeSystem typeSystem, TypeConstant type) {
        super(typeSystem, type);
    }

    private static final ClassDesc CD_xInt64 =
        ClassDesc.of(org.xvm.javajit.intrinsic.numbers.xInt64.class.getName());

}
