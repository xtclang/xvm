package org.xtclang.ecstasy.reflect;

import org.xtclang.ecstasy.xObj;
import org.xvm.javajit.Ctx;

/**
 * A readable/writable reference in Ecstasy.
 */
public interface Var extends Ref {
    /**
     * @param referent  the referent to store in this reference
     */
    void set(Ctx ctx, xObj referent);
}
