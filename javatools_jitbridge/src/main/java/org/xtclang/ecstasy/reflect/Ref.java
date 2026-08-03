package org.xtclang.ecstasy.reflect;

import org.xtclang.ecstasy.Object;

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
    boolean peek$p(Ctx ctx);
}
