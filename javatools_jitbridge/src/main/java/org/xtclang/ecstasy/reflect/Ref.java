package org.xtclang.ecstasy.reflect;

import org.xtclang.ecstasy.Object;
import org.xtclang.ecstasy.xObj;
import org.xvm.javajit.Ctx;

/**
 * A read-only reference in Ecstasy.
 */
public interface Ref extends Object {
    /**
     * @return the referent of this reference
     */
    xObj get(Ctx ctx);
}
