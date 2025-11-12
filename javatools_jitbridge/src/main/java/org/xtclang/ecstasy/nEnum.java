package org.xtclang.ecstasy;

import org.xtclang.ecstasy.reflect.Enumeration;

import org.xtclang.ecstasy.text.String;

import org.xvm.asm.constants.TypeConstant;
import org.xvm.javajit.Ctx;

/**
 * All Ecstasy `enum` types must extend this class.
 */
public abstract class nEnum
    extends nConst
    implements Object {
    public nEnum(Ctx ctx) {
        super(ctx);
    }

    @Override
    public TypeConstant $xvmType(Ctx ctx) {
        throw new UnsupportedOperationException("Must be generated");
    }

    abstract public Enumeration enumeration$get(Ctx ctx);
    abstract public String name$get(Ctx ctx);
    abstract public long ordinal$get$p(Ctx ctx);

    static public boolean equals$p(Ctx ctx, nType CompileType, nEnum o1, nEnum o2) {
        return o1.ordinal$get$p(ctx) == o2.ordinal$get$p(ctx);
    }

    static public long compare$p(Ctx ctx, nType CompileType, nEnum o1, nEnum o2) {
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
