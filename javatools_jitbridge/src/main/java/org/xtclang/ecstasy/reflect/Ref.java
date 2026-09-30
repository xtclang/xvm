package org.xtclang.ecstasy.reflect;

import org.xtclang.ecstasy.Object;
import org.xtclang.ecstasy.nType;

import org.xvm.javajit.Ctx;

/**
 * A read-only reference in Ecstasy.
 */
public interface Ref extends Object {
    /**
     * @return true iff this reference has a referent
     */
    boolean assigned$get$p(Ctx ctx);

    /**
     * @return the referent of this reference
     */
    Object get(Ctx ctx);

    /**
     * @return true iff this reference has a referent; the referent is returned in the context
     */
    default boolean peek$p(Ctx ctx) {
        if (assigned$get$p(ctx)) {
            ctx.o0 = get(ctx);
            return true;
        }
        return false;
    }

    /**
     * Native implementation of: "static <CompileType extends Ref> Boolean equals(CompileType
     * value1, CompileType value2)".
     */
    static boolean equals$p(Ctx ctx, nType CompileType, Ref value1, Ref value2) {
        return nRef.equals$p(ctx, CompileType, value1, value2);
    }
}
