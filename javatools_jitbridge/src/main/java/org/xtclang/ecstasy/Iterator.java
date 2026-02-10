package org.xtclang.ecstasy;

import org.xvm.javajit.Ctx;

/**
 * Native representation of `ecstasy.Iterator`.
 */
public interface Iterator extends Object {

    boolean next(Ctx ctx);
}
