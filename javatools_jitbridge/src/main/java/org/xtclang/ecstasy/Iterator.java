package org.xtclang.ecstasy;

import org.xvm.javajit.Ctx;

/**
 * Native representation of `ecstasy.Iterator`.
 */
public interface Iterator extends Object {

    nType Element$get(Ctx ctx);

    boolean next$p(Ctx ctx);
}
