package org.xvm.javajit.intrinsic;


import org.xvm.asm.constants.TypeConstant;


public class xType
        extends xObj {
    public xType(TypeConstant type) {
        $type = type;
    }

    public final TypeConstant $type;

    @Override public xType $type() {
        return null;
    }

    @Override public boolean $isImmut() {
        return false;
    }

    @Override public void $makeImmut() {

    }

    @Override public boolean $isA(xType t) {
        return false;
    }

    @Override public xContainer $container() {
        return null;
    }
}
