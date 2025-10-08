package org.xtclang.ecstasy;

import org.xtclang.ecstasy.reflect.Enumeration;

import org.xtclang.ecstasy.text.String;

import org.xvm.asm.ConstantPool;

import org.xvm.javajit.Ctx;

/**
 * Native Enumeration<Boolean>.
 */
public class Boolean$Enumeration extends Enumeration {
    private Boolean$Enumeration(Ctx ctx) {
        ConstantPool pool = ctx.container.typeSystem.pool();

        super(ctx, pool.ensureClassTypeConstant(pool.clzClass(), null, pool.typeBoolean()));
    }

    public static final Boolean$Enumeration $INSTANCE = new Boolean$Enumeration(Ctx.get());

    public static final String[] $names   = new String[] {Boolean.False.$name, Boolean.True.$name};
    public static final Boolean[] $values = new Boolean[] {Boolean.False, Boolean.True};

    @Override
    public long count$get$p() {
        return 2;
    }
    @Override
    public Boolean[] values$get() {
        return $values;
    }
    @Override
    public String[] names$get() {
        return $names;
    }
}
