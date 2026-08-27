package org.xtclang.ecstasy.collections;

import org.xtclang.ecstasy.Iterable;
import org.xtclang.ecstasy.Iterator;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.Ctx;

/**
 * Base implementation for arrays whose elements occupy two Java {@code long} values.
 */
public abstract class nLongLongBasedArray<ArrayType extends nLongLongBasedArray<ArrayType>>
        extends nLongBasedArray<ArrayType> {
    protected nLongLongBasedArray(Ctx ctx, TypeConstant type) {
        super(ctx, type);
    }

    public long getElement$p(Ctx ctx, long index) {
        return $getElement$pi(ctx, index);
    }

    public long getElement$pi(Ctx ctx, long index) {
        return $getElement$pi(ctx, index);
    }

    public void setElement$p(Ctx ctx, long index, long lowValue, long highValue) {
        setElement$pi(ctx, index, lowValue, highValue);
    }

    public void setElement$pi(Ctx ctx, long index, long lowValue, long highValue) {
        ctx.i0 = highValue;
        setElement$pi(ctx, index, lowValue);
    }

    @Override
    public Array addAll(Ctx ctx, Iterable values) {
        Iterator iterator = values.iterator(ctx);
        while (iterator.next$p(ctx)) {
            $add128(ctx, ctx.i0, ctx.i1);
        }
        return this;
    }

    protected void $fill128(Ctx ctx, long size, long lowValue, long highValue) {
        if (!$growInPlace(ctx, size)) {
            throw $oob(ctx, size);
        }

        long[] storage = $storage;
        for (int i = 0, len = (int) size * 2; i < len; i += 2) {
            storage[i]   = lowValue;
            storage[i+1] = highValue;
        }
        $size((int) size);
    }

    protected ArrayType $add128(
            Ctx ctx, long lowValue, long highValue) {
        ctx.i0 = highValue;
        return super.add$p(ctx, lowValue);
    }

    @SuppressWarnings("unchecked")
    protected ArrayType $insert128(Ctx ctx, long index, long lowValue, long highValue) {
        if (index < 0 || index > size$get$p(ctx)) {
            throw $oob(ctx, index);
        }
        $insert(ctx, index, 1);
        $set128bitElement(index, lowValue, highValue);
        return (ArrayType) this;
    }

    @SuppressWarnings("unchecked")
    protected ArrayType $delete128(Ctx ctx, long index) {
        if (index < 0 || index >= size$get$p(ctx)) {
            throw $oob(ctx, index);
        }
        $delete(ctx, index, 1);
        return (ArrayType) this;
    }

    @Override
    protected long $storageCapacity() {
        return $storageCapacity128bit();
    }

    @Override
    protected long $getElement(Ctx ctx, long index) {
        return $get128bitElement(ctx, index);
    }

    @Override
    protected void $setElement(Ctx ctx, long index, long value) {
        $set128bitElement(index, value, ctx.i0);
    }

    @Override
    protected long $cap2len(long cap) {
        return $cap2len128bits(cap);
    }

    @Override
    protected long $calculateHash(Ctx ctx) {
        return $calculate128BitHash(ctx);
    }

    @Override
    protected void $deleteElements(long index, long count) {
        $delete128bit(index, count);
    }

    @Override
    protected void $insertElements(long index, long count) {
        $insert128bit(index, count);
    }

    // ----- Iterator implementation ---------------------------------------------------------------

    protected abstract class n128BitIterator
            extends nBaseIterator {
        protected n128BitIterator(Ctx ctx) {
            super(ctx);
        }

        @Override
        public boolean next$p(Ctx ctx) {
            if (index < size$get$p(ctx)) {
                long low = $getElement$pi(ctx, index++);
                // high is already in ctx.i0
                ctx.i1 = ctx.i0;
                ctx.i0 = low;
                return true;
            }
            return false;
        }
    }
}
