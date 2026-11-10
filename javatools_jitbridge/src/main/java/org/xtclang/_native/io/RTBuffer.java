package org.xtclang._native.io;

import org.xtclang.ecstasy.collections.ArrayᐸUInt8ᐳ;

import org.xtclang.ecstasy.nObject;
import org.xvm.javajit.Ctx;

/**
 * The native implementation of {@code _native.io.RTBuffer}.
 */
public class RTBuffer extends nObject {

    protected RTBuffer() {
        super(null);
    }

    /**
     * The field for the property {@code private Byte[] rawBytes;}.
     */
    public ArrayᐸUInt8ᐳ rawBytes;

    /**
     * The primitive implementation of
     * {@code private void copyRawBytes(Int srcOffset, Byte[] bytes, Int dstOffset, Int count)}
     */
    public void copyRawBytes(Ctx ctx, long srcOffset, ArrayᐸUInt8ᐳ target, long dstOffset, long count) {
        rawBytes.$copyRawBytes(ctx, srcOffset, target, dstOffset, count);
    }
}
