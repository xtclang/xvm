package org.xtclang.ecstasy.reflect;

import org.xtclang.ecstasy.nConst;

import org.xvm.asm.ConstantPool;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.Ctx;

/**
 * All Ecstasy `Class<...>` types must extend this class.
 */
public class Class
        extends nConst {

    public Class(Ctx ctx, TypeConstant publicType) {
        super(ctx);

        $publicType = publicType;
    }

    public final TypeConstant $publicType;

    @Override public TypeConstant $xvmType(Ctx ctx) {
        ConstantPool pool = ctx.container.typeSystem.pool();
        return pool.ensureClassTypeConstant(pool.clzClass(), null, $publicType);
    }
}
