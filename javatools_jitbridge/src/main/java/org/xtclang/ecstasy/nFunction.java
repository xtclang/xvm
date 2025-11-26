package org.xtclang.ecstasy;

import java.lang.invoke.MethodHandle;

import org.xtclang.ecstasy.collections.Tuple;

import org.xtclang.ecstasy.reflect.Function;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.Ctx;

/**
 * Native "Function" support.
 */
public class nFunction extends nObj implements Function {
    public nFunction(Ctx ctx, TypeConstant type,
                     MethodHandle stdMethod, MethodHandle optMethod, boolean immmutable) {
        super(ctx);

        assert type.isFunction();

        this.type      = type;
        this.stdMethod = stdMethod;
        this.optMethod = optMethod;
        this.immutable = immmutable;
    }

    public final TypeConstant type;
    public final MethodHandle stdMethod;
    public final MethodHandle optMethod;
    public final boolean      immutable;

    @Override
    public TypeConstant $xvmType(Ctx ctx) {
        return type;
    }

    @Override
    public Tuple invoke(Ctx ctx, Tuple args) {
        // almost *never* called - reflection based
        // TODO
        return null;
    }

    @Override
    public boolean $isImmut() {
        return immutable;
    }
}
