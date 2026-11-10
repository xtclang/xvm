package org.xtclang._native.io;

import org.xtclang.ecstasy.nService;
import org.xtclang.ecstasy.Object;

import org.xtclang.ecstasy.collections.ArrayᐸUInt8ᐳ;

import org.xtclang.ecstasy.numbers.Int64;

import org.xvm.javajit.Ctx;

/**
 * The native implementation of {@code _native.io.RawChannel}.
 */
public class RawChannel extends nService {

    public RawChannel(Ctx ctx) {
        super(ctx);
    }

    public long $getPreferredBufferSize(Ctx ctx) {
        return 8192L;
    }

    /**
     * The primitive property getter for {@code Boolean closed;}.
     */
    public boolean closed$get$p(Ctx ctx) {
        return false;
    }

    /**
     * The primitive property getter for {@code void close();}.
     */
    public void close(Ctx ctx) {
    }

    /**
     * The primitive implementation of {@code Byte[]|Int allocate(Boolean internal)}.
     */
    public Object allocate$p(Ctx ctx, boolean internal) {
        if (closed$get$p(ctx)) {
            return Int64.$box(-2L);
        }
        long capacity = $getPreferredBufferSize(ctx);
        return ArrayᐸUInt8ᐳ.$makeArray(ctx, capacity);
    }

    /**
     * The native implementation of {@code void incRefCount(Byte[] buffer)}.
     */
    public void incRefCount(ArrayᐸUInt8ᐳ buffer) {

    }

    /**
     * The native implementation of {@code void decRefCount(Byte[] buffer)}.
     */
    public void decRefCount(ArrayᐸUInt8ᐳ buffer) {

    }
}
