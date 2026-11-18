package org.xtclang.ecstasy;

import org.xvm.javajit.Ctx;

/**
 * Native representation of `ecstasy.IterableᐸBFloat16ᐳ`.
 */
public interface IterableᐸBFloat16ᐳ extends Iterable {

    @Override long size$get$p(Ctx ctx, boolean async);
    @Override IteratorᐸBFloat16ᐳ iterator(Ctx ctx, boolean async);
}
