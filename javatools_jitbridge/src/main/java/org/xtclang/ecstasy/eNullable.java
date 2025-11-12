package org.xtclang.ecstasy;

import org.xtclang.ecstasy.reflect.Enumeration;

import org.xtclang.ecstasy.text.String;

import org.xvm.asm.ConstantPool;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.Ctx;

/**
 * Native Enumeration<Nullable>.
 */
public class eNullable extends Enumeration {
    private eNullable(Ctx ctx) {
        super(ctx);
    }

    @Override public TypeConstant $xvmType(Ctx ctx) {
        ConstantPool pool = ctx.container.typeSystem.pool();
        return pool.ensureClassTypeConstant(pool.clzClass(), null, pool.typeNullable());
    }

    public static final eNullable $INSTANCE = new eNullable(Ctx.get());

    public static final String[] $names = new String[] {Nullable.$name};
    public static final Nullable[] $values = new Nullable[] {Nullable.Null};

    @Override
    public long count$get$p() {
        return 1;
    }

    @Override
    public String[] names$get() {
        return $names;
    }

    @Override
    public Nullable[] values$get() {
        return $values;
    }
}
