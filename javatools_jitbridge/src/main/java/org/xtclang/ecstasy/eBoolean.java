package org.xtclang.ecstasy;

import org.xtclang.ecstasy.reflect.Enumeration;
import org.xtclang.ecstasy.text.String;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.Ctx;

/**
 * Native Enumeration<Boolean>.
 */
public class eBoolean extends Enumeration {
    private eBoolean() {
        super(null);
    }

    public static final eBoolean $INSTANCE = new eBoolean();

    public static final String[]  $names  = new String[] {Boolean.False.$name, Boolean.True.$name};
    public static final Boolean[] $values = new Boolean[] {Boolean.False, Boolean.True};

    @Override public TypeConstant $xvmType(Ctx ctx) {
        ConstantPool pool = ctx.container.typeSystem.pool();
        return pool.ensureClassTypeConstant(pool.clzClass(), null, pool.typeBoolean());
    }

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
