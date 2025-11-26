package org.xtclang.ecstasy;

import java.lang.invoke.MethodHandle;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.Ctx;

/**
 * Native "Method" support.
 */
public class nMethod extends nObj {
    public nMethod(Ctx ctx, TypeConstant type, MethodHandle stdMethod, MethodHandle optMethod) {
        super(ctx);

        assert type.isMethod();

        this.type      = type;
        this.stdMethod = stdMethod;
        this.optMethod = optMethod;
    }

    public final TypeConstant type;
    public final MethodHandle stdMethod;
    public final MethodHandle optMethod;

    @Override
    public TypeConstant $xvmType(Ctx ctx) {
        return type;
    }

    @Override
    public boolean $isImmut() {
        return true;
    }
}
