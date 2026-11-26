package org.xtclang.ecstasy.reflect;

import org.xtclang.ecstasy.Object;
import org.xtclang.ecstasy.nType;

import org.xvm.javajit.Ctx;

import org.xvm.javajit.annotations.JitGenerated;
import org.xvm.javajit.annotations.JitName;

/**
 * A read-only reference in Ecstasy.
 */
@JitGenerated
public interface Ref extends Object {
    /**
     * @return true iff this reference has a referent
     */
    boolean assigned$get$p(Ctx ctx);

    /**
     * @return the referent of this reference
     */
    @JitName
    Object get(Ctx ctx);

    /**
     * @return true iff this reference has a referent; the referent is returned in the context
     */
    @JitName
    default boolean peek$p(Ctx ctx) {
        if (assigned$get$p(ctx)) {
            ctx.o0 = get(ctx);
            return true;
        }
        return false;
    }

    /**
     * Native implementation of: {@code static <CompileType extends Ref> Boolean
     * equals(CompileType value1, CompileType value2)}.
     */
    static boolean equals$p(Ctx ctx, nType CompileType, Ref value1, Ref value2) {
        return nRef.equals$p(ctx, CompileType, value1, value2);
    }
}
