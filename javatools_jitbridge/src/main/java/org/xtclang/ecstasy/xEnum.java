package org.xtclang.ecstasy;

import org.xtclang.ecstasy.reflect.Enumeration;

import org.xtclang.ecstasy.text.String;

import org.xvm.javajit.Ctx;

/**
 * All Ecstasy `enum` types must extend this class.
 */
public abstract class xEnum extends xConst implements Object {
    public xEnum(Ctx ctx) {
        super(ctx);
    }

    @Override
    public xType $type() {
        return null; // TODO
    }

    abstract public Enumeration enumeration$get(Ctx ctx);
    abstract public String name$get(Ctx ctx);
    abstract public long ordinal$get$p(Ctx ctx);

    static public boolean equals$p(Ctx ctx, xType CompileType, xEnum o1, xEnum o2) {
        return o1.ordinal$get$p(ctx) == o2.ordinal$get$p(ctx);
    }

    static public long compare$p(Ctx ctx, xType CompileType, xEnum o1, xEnum o2) {
        return o1.ordinal$get$p(ctx) - o2.ordinal$get$p(ctx);
    }

    @Override
    public String toString(Ctx ctx) {
        return name$get(ctx);
    }

    @Override
    public java.lang.String toString() {
        return name$get(null).toString();
    }
}
