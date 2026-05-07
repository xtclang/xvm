package org.xtclang.ecstasy.collections;

import org.xtclang.ecstasy.Object;

import org.xtclang.ecstasy.numbers.Int64;

import org.xvm.javajit.Ctx;

/**
 * A native implementation of the HashCollector service.
 */
public interface HashCollector extends Object {

    /**
     * This is the native implementation of Const.x:
     * <pre>
     *     @Abstract Int compute();
     * </pre>
     *
     * @return  the computed hash code as an {@link Int64}
     */
    default Int64 compute(Ctx ctx) {
        return Int64.$box(compute$p(ctx));
    }

    /**
     * This is the primitive implementation of Const.x:
     * <pre>
     *     @Abstract Int compute();
     * </pre>
     *
     * @return  the computed hash code
     */
    long compute$p(Ctx ctx);

    /**
     * Reset this {@link HashCollector}.
     * <p>
     * This is the native implementation of Const.x:
     * <pre>
     *     @Abstract HashCollector reset();
     * </pre>
     *
     * @return  this {@link HashCollector} instance
     */
    HashCollector reset(Ctx ctx);
}
