package org.xvm.javajit.intrinsic;

import org.xvm.asm.constants.TypeConstant;

public abstract class xType
        extends xObj {

    public xType(long coontainerId, TypeConstant type) {
        super(coontainerId);
        $type = type;
    }

    public final TypeConstant $type;

    public abstract xObj alloc(Ctx ctx);

    @Override public xType $type() {
        return $type.ensureXType($ctx().container);
    }

    @Override public boolean $isImmut() {
        return true;
    }

    @Override public void $makeImmut() {}

    @Override public boolean $isA(xType t) {
        return $type.isA(t.$type);
    }
}
