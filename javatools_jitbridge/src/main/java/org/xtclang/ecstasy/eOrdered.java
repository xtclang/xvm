package org.xtclang.ecstasy;

import org.xtclang.ecstasy.collections.ArrayᐸObjectᐳ;

import org.xtclang.ecstasy.reflect.Enumeration;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.Ctx;
import org.xvm.javajit.ModuleLoader;

/**
 * Native Enumeration<Ordered>.
 */
public class eOrdered extends Enumeration {
    private eOrdered(Ctx ctx, TypeConstant type) {
        super(ctx, type);
    }

    static {
        Ctx          ctx  = ((ModuleLoader) eBoolean.class.getClassLoader()).getCtx();
        TypeConstant type = ctx.pool().typeOrdered();
        $INSTANCE = new eOrdered(ctx, type);

        $names  = ArrayᐸObjectᐳ.$makeStringArray(ctx, Ordered.Lesser.$INSTANCE.$name,
                                                       Ordered.Equal.$INSTANCE.$name,
                                                       Ordered.Greater.$INSTANCE.$name);

        $values = ArrayᐸObjectᐳ.$makeArray(ctx, type, Ordered.Lesser.$INSTANCE,
                                                       Ordered.Equal.$INSTANCE,
                                                       Ordered.Greater.$INSTANCE);
    }

    public static final eOrdered $INSTANCE;
    public static final ArrayᐸObjectᐳ $names;
    public static final ArrayᐸObjectᐳ $values;

    @Override
    public long count$get$p(Ctx ctx) {
        return 3;
    }

    @Override
    public ArrayᐸObjectᐳ values$get(Ctx ctx) {
        return $values;
    }

    @Override
    public ArrayᐸObjectᐳ names$get(Ctx ctx) {
        return $names;
    }
}
