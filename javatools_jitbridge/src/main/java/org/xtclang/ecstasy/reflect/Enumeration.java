package org.xtclang.ecstasy.reflect;

import org.xtclang.ecstasy.xClass;
import org.xtclang.ecstasy.xEnum;

import org.xtclang.ecstasy.text.String;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.Ctx;

/**
 * All Ecstasy `Class<Enum>` types must extend this class.
 */
public abstract class Enumeration extends xClass {
    public Enumeration(Ctx ctx, TypeConstant type) {
        super(ctx, type);
    }

    public abstract long count$get$p();
    public abstract String[] names$get();   // TODO GG Array$Object
    public abstract xEnum[] values$get();   // TODO GG Array$Object
}
