package org.xtclang.ecstasy;

import org.xvm.javajit.Ctx;

/**
 * Native representation of `ecstasy.IterableᐸCharᐳ`.
 */
public interface IterableᐸCharᐳ extends Iterable {

    @Override long size$get$p(Ctx ctx);
    @Override IteratorᐸCharᐳ iterator(Ctx ctx);
}
