package org.xtclang.ecstasy;

import org.xvm.javajit.Ctx;

/**
 * Native representation of `ecstasy.Iterator`.
 */
public interface Iterator extends Object {

    // xType Element$get(Ctx ctx);

    boolean next(Ctx ctx);
}
