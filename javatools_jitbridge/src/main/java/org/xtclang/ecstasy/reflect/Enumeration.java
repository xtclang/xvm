package org.xtclang.ecstasy.reflect;

import org.xtclang.ecstasy.nEnum;

import org.xtclang.ecstasy.text.String;

import org.xvm.javajit.Ctx;

/**
 * All Ecstasy `Class<Enum>` types must extend this class.
 */
public abstract class Enumeration extends Class {
    public Enumeration(Ctx ctx) {
        super(ctx);
    }

    public abstract long count$get$p();
    public abstract String[] names$get();   // TODO GG Array$Object
    public abstract nEnum[] values$get();   // TODO GG Array$Object
}
