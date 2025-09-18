package org.xtclang.ecstasy;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.Ctx;

/**
 * All Ecstasy `Class<...>` types must extend this class.
 */
public abstract class xClass
        extends xConst {

    public xClass(Ctx ctx, TypeConstant type) {
        super(ctx);

        $type = type;
    }

    public final TypeConstant $type;

    @Override public xType $type() {
        return null; // TODO
    }
}
