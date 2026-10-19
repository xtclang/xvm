package org.xtclang._native.collections;

import org.xtclang.ecstasy.nService;

import org.xtclang.ecstasy.collections.HashCollector;

import org.xtclang.ecstasy.text.String;

import org.xvm.javajit.Ctx;

import org.xvm.util.ByteHashCollector;

public class BasicHashCollector
        extends nService
        implements HashCollector {

    public BasicHashCollector(Ctx ctx) {
        super(ctx);
        delegate = ctx.createHashCollector();
    }

    private final ByteHashCollector delegate;

    @Override
    public long compute$p(Ctx ctx) {
        return delegate.compute();
    }

    @Override
    public BasicHashCollector reset(Ctx ctx) {
        delegate.reset();
        return this;
    }

    @Override
    public String toString(Ctx ctx) {
        return String.of(ctx, "BasicHashCollector");
    }

    public HashCollector addByte$p(Ctx ctx, int n) {
        delegate.addByte((byte) (n & 0xFF));
        return this;
    }

    // ----- injection support ---------------------------------------------------------------------

    public static HashCollector $create(Ctx ctx) {
        return new BasicHashCollector(ctx);
    }
}
