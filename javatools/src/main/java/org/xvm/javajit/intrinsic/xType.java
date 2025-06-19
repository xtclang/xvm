package org.xvm.javajit.intrinsic;

import org.xvm.asm.constants.TypeConstant;

public abstract class xType
        extends xConst {

    public xType(long containerId, TypeConstant type) {
        super(containerId);
        $type = type;
    }

    public final TypeConstant $type;

    public abstract xObj alloc(Ctx ctx);

    @Override public xType $type() {
        return $type.ensureXType($ctx().container);
    }

    @Override public boolean $isA(xType t) {
        return $type.isA(t.$type);
    }
}
