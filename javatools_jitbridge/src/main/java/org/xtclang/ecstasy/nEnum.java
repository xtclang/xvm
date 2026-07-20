package org.xtclang.ecstasy;

import org.xtclang.ecstasy.reflect.Enumeration;

import org.xtclang.ecstasy.text.String;

import org.xvm.asm.constants.TypeConstant;
import org.xvm.javajit.Ctx;

/**
 * All Ecstasy `enum` types must extend this class.
 *
 * Some methods here are defined by hand, even though they could be generated. This is necessary
 * because the file name implies "no modification" by the augmenting builder.
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

    // TODO since we're not augmenting this class (being nEnum), we need to make sure that
    //      prev()/next() are gen'd on actual enums

    /**
     * Native implementation of Enum.x
     * <pre>
     *     Int estimateStringLength() = name.size;
     * </pre>
     */
    public long estimateStringLength$p(Ctx ctx) {
        return name$get(ctx).size$get$p(ctx);
    }

    /**
     * Native implementation of Enum.x
     * <pre>
     *     Appender<Char> appendTo(Appender<Char> buf) = name.appendTo(buf);
     * </pre>
     */
    public AppenderᐸCharᐳ appendTo(Ctx ctx, AppenderᐸCharᐳ appender) {
        return name$get(ctx).appendTo(ctx, appender);
    }

    @Override
    public java.lang.String toString() {
        return name$get(null).toString();
    }
}
