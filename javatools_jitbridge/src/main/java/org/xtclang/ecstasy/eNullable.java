package org.xtclang.ecstasy;

import org.xtclang.ecstasy.collections.ArrayᐸObjectᐳ;

import org.xtclang.ecstasy.reflect.Enumeration;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.Ctx;
import org.xvm.javajit.ModuleLoader;

/**
 * Native Enumeration<Nullable>.
 */
public class eNullable extends Enumeration {
    private eNullable(Ctx ctx, TypeConstant type) {
        super(ctx, type);
    }

    static {
        Ctx          ctx  = ((ModuleLoader) eBoolean.class.getClassLoader()).getCtx();
        TypeConstant type = ctx.pool().typeNullable();
        $INSTANCE = new eNullable(ctx, type);
        $names  = ArrayᐸObjectᐳ.$makeStringArray(ctx, Nullable.$name);
        $values = ArrayᐸObjectᐳ.$makeArray(ctx, type, Nullable.Null);
    }

    public static final eNullable $INSTANCE;
    public static final ArrayᐸObjectᐳ $names;
    public static final ArrayᐸObjectᐳ $values;

    @Override
    public long count$get$p(Ctx ctx) {
        return 1;
    }

    @Override
    public ArrayᐸObjectᐳ names$get(Ctx ctx) {
        return $names;
    }

    @Override
    public ArrayᐸObjectᐳ values$get(Ctx ctx) {
        return $values;
    }
}
