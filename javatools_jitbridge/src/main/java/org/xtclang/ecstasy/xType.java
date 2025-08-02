package org.xtclang.ecstasy;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.Ctx;

/**
 * All Ecstasy `Type` types must extend this class.
 */
public abstract class xType
        extends xConst {

    public xType(long containerId, TypeConstant type) {
        super(containerId);
        $type = type;
    }

    public final TypeConstant $type;

    public abstract xObj alloc(Ctx ctx);

    @Override public xType $type() {
        return (xType) $type.ensureXType($ctx().container);
    }

    @Override public boolean $isA(xType t) {
        return $type.isA(t.$type);
    }
}
