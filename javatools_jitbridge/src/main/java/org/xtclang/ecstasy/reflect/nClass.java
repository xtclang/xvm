package org.xtclang.ecstasy.reflect;

import org.xtclang.ecstasy.nConst;
import org.xtclang.ecstasy.nType;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.Ctx;

/**
 * All Ecstasy `Class<...>` types must extend this class.
 */
public abstract class nClass
    extends nConst {

    public nClass(Ctx ctx, TypeConstant type) {
        super(ctx);

        $type = type;
    }

    public final TypeConstant $type;

    @Override public nType $type(Ctx ctx) {
        return null; // TODO
    }
}
