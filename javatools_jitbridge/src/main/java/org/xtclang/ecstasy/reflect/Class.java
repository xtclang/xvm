package org.xtclang.ecstasy.reflect;

import org.xtclang.ecstasy.nConst;

import org.xvm.javajit.Ctx;

/**
 * All Ecstasy `Class<...>` types must extend this class.
 */
public abstract class Class
    extends nConst {

    public Class(Ctx ctx) {
        super(ctx);
    }
}
