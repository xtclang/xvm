package org.xtclang.ecstasy;

import org.xvm.javajit.Ctx;

/**
 * Native representation of `ecstasy.Iterable`.
 */
public interface Iterable extends Object {

    long size$get$p(Ctx ctx);
    Iterator iterator(Ctx ctx);
}
