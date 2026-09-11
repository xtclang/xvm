package org.xtclang.ecstasy.reflect;

import org.xtclang.ecstasy.collections.Array;
import org.xtclang.ecstasy.collections.ArrayᐸObjectᐳ;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.Ctx;

/**
 * All Ecstasy `Class<Enum>` types must extend this class.
 */
public abstract class Enumeration extends Class {
    protected Enumeration(Ctx ctx, TypeConstant publicType) {
        super(ctx, publicType);
    }

    /**
     * The native implementation of the Enumeration.count property.
     */
    public abstract long count$get$p(Ctx ctx);

    /**
     * The native implementation of the Enumeration.names property.
     */
    public abstract ArrayᐸObjectᐳ names$get(Ctx ctx);

    /**
     * The native implementation of the Enumeration.values property.
     */
    public abstract ArrayᐸObjectᐳ values$get(Ctx ctx);
}
