package org.xtclang.ecstasy;

import org.xtclang.ecstasy.collections.ArrayᐸObjectᐳ;

import org.xtclang.ecstasy.reflect.Enumeration;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.Ctx;
import org.xvm.javajit.ModuleLoader;

/**
 * Native Enumeration<Boolean>.
 */
public class eBoolean extends Enumeration {
    private eBoolean(Ctx ctx, TypeConstant type) {
        super(ctx, type);
    }

    static {
        Ctx          ctx  = ((ModuleLoader) eBoolean.class.getClassLoader()).getCtx();
        TypeConstant type = ctx.pool().typeBoolean();
        $INSTANCE = new eBoolean(ctx, type);
        $names  = ArrayᐸObjectᐳ.$makeStringArray(ctx, Boolean.False.$name, Boolean.True.$name);
        $values = ArrayᐸObjectᐳ.$makeArray(ctx, type, Boolean.False, Boolean.True);
    }

    public static final eBoolean $INSTANCE;
    public static final ArrayᐸObjectᐳ $names;
    // TODO this must be ArrayᐸBooleanᐳ
    public static final ArrayᐸObjectᐳ $values;

    @Override
    public long count$get$p(Ctx ctx) {
        return 2;
    }

    // TODO this must return ArrayᐸBooleanᐳ but cannot because the super method returns
    // ArrayᐸObjectᐳ and ArrayᐸBooleanᐳ is not an instance of ArrayᐸObjectᐳ
    @Override
    public ArrayᐸObjectᐳ values$get(Ctx ctx) {
        return $values;
    }

    @Override
    public ArrayᐸObjectᐳ names$get(Ctx ctx) {
        return $names;
    }
}
