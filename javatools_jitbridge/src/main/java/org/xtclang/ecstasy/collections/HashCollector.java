package org.xtclang.ecstasy.collections;

import org.xtclang.ecstasy.Object;

import org.xtclang.ecstasy.numbers.Int64;

import org.xvm.javajit.Ctx;

import org.xvm.javajit.annotations.JitGenerated;
import org.xvm.javajit.annotations.JitName;

/**
 * A native implementation of the HashCollector service.
 */
@JitGenerated
public interface HashCollector extends Object {

    /**
     * This is the native implementation of:
     *     Int compute();
     */
    default Int64 compute(Ctx ctx) {
        return Int64.$box(compute$p(ctx));
    }

    /**
     * This is the primitive implementation of:
     *     Int compute();
     */
    @JitName
    long compute$p(Ctx ctx);

    /**
     * This is the native implementation of:
     *     HashCollector reset();
     */
    @JitName
    HashCollector reset(Ctx ctx);
}
