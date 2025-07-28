package org.xtclang.ecstasy.text;

import java.lang.constant.MethodTypeDesc;

import org.xtclang.ecstasy.xConst;

import static java.lang.constant.ConstantDescs.CD_int;

import static org.xvm.javajit.Builder.CD_Char;

/**
 * Native shell for "ecstasy.text.Char".
 */
public class Char extends xConst {
    public Char(long containerId) {
        super(containerId);
    }

    public static final MethodTypeDesc MD_box = MethodTypeDesc.of(CD_Char, CD_int);

}
