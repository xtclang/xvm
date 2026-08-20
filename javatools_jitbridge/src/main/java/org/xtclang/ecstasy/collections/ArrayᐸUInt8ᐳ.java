package org.xtclang.ecstasy.collections;

import java.util.Arrays;

import org.xtclang.ecstasy.Iterable;
import org.xtclang.ecstasy.IteratorᐸUInt8ᐳ;
import org.xtclang.ecstasy.Object;
import org.xtclang.ecstasy.nType;

import org.xtclang.ecstasy.numbers.Int64;
import org.xtclang.ecstasy.numbers.UInt8;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.Ctx;

/**
 * Array of UInt8, stored in an array of Java longs, eight UInt8 per long.
 * <p>
 * Object header
 * xObj - 64 bits of flags
 * ---
 * Delegate - ref
 * Storage - ref
 */
public class ArrayᐸUInt8ᐳ
        extends nLongBasedArray<ArrayᐸUInt8ᐳ> {

    public ArrayᐸUInt8ᐳ(Ctx ctx, TypeConstant type) {
        super(ctx, type);
    }

    // ----- Array API -----------------------------------------------------------------------------

    /**
     * @see {@link Array#$new$p}
     */
    public static ArrayᐸUInt8ᐳ $new$p(Ctx ctx, TypeConstant type, long capacity, boolean _capacity) {
        assert !type.isImmutable();

        ctx.alloc(64); // REVIEW how big?
        ArrayᐸUInt8ᐳ array = new ArrayᐸUInt8ᐳ(ctx, type);
        array.$mut($MUTABLE);
        array.$capCfg(ctx, capacity);
        return array;
    }

    /**
     * @see {@link Array#$new$1$p}
     */
    public static ArrayᐸUInt8ᐳ $new$1$p(Ctx ctx, TypeConstant type, long size, Object supply) {
        if (supply instanceof UInt8 boxed) {
            ctx.alloc(size); // REVIEW + HEADER_SIZE?
            ArrayᐸUInt8ᐳ array = new ArrayᐸUInt8ᐳ(ctx, type);
            array.$mut($FIXED);

            long value = boxed.$value & 0xFF;
            long fill  = value == 0 ? 0 : value | (value << 8) | (value << 16) | (value << 24);
            fill |= (fill << 32);

            if (array.$growInPlace(ctx, size)) {
                Arrays.fill(array.$storage, fill);
                array.$size((int) size);
                return array;
            } else {
                throw array.$oob(ctx, size);
            }
        }
        // TODO
        throw new UnsupportedOperationException();
    }

    /**
     * @see {@link Array#$new$2}
     */
    public static ArrayᐸUInt8ᐳ $new$2$p(Ctx ctx, TypeConstant type, Mutability mutability, Iterable elements) {
        long size = elements.size$get$p(ctx);
        ctx.alloc(size); // REVIEW + HEADER_SIZE?
        ArrayᐸUInt8ᐳ array = new ArrayᐸUInt8ᐳ(ctx, type);
        array.$mut($MUTABLE);
        array.addAll(ctx, elements);
        array.$mut((int) mutability.ordinal$get$p(ctx));
        return array;
    }

    /**
     * @see {@link Array#$new$3}
     */
    public static ArrayᐸUInt8ᐳ $new$3$p(Ctx ctx, TypeConstant type, ArrayᐸUInt8ᐳ that) {
        // TODO
        throw new UnsupportedOperationException();
    }

    public UInt8 getElement(Ctx ctx, Int64 index) {
        return UInt8.$box(getElement$pi(ctx, index.$value));
    }

    public int getElement$p(Ctx ctx, long index) {
        return getElement$pi(ctx, index);
    }

    public int getElement$pi(Ctx ctx, long index) {
        return (int) $getElement$pi(ctx, index);
    }

    public void setElement(Ctx ctx, Int64 index, Object value) {
        setElement$pi(ctx, index.$value, ((UInt8) value).$value);
    }

    public void setElement$p(Ctx ctx, long index, int value) {
        setElement$pi(ctx, index, value);
    }

    public IteratorᐸUInt8ᐳ iterator(Ctx ctx) {
        return new nIterator(ctx);
    }

    @Override
    public ArrayᐸUInt8ᐳ add(Ctx ctx, Object element) {
        return add$p(ctx, ((UInt8) element).$value);
    }

    public ArrayᐸUInt8ᐳ add$p(Ctx ctx, int value) {
        return super.add$p(ctx, value);
    }

    public ArrayᐸUInt8ᐳ insert$p(Ctx ctx, long index, int value) {
        if (index < 0 || index > size$get$p(ctx)) {
            throw $oob(ctx, index);
        }
        $insert(ctx, index, 1);
        $set8bitElement(index, value);
        return this;
    }

    @Override
    public ArrayᐸUInt8ᐳ delete$p(Ctx ctx, long index) {
        if (index < 0 || index >= size$get$p(ctx)) {
            throw $oob(ctx, index);
        }
        $delete(ctx, index, 1);
        return this;
    }

    /**
     * Native implementation of: "Bit[] toBitArray(Mutability mutability = Constant)"
     */
    public ArrayᐸBitᐳ toBitArray(Ctx ctx, Mutability mutability) {
        long   byteCount = size$get$p(ctx);
        int    longCount = (int) ((byteCount + 7) >>> 3);
        long[] values;

        if ($delegate == null) {
            values = $storage == null
                    ? new long[longCount]
                    : Arrays.copyOf($storage, longCount);
        } else {
            values = new long[longCount];
            for (long index = 0; index < byteCount; index++) {
                values[(int) (index >>> 3)] |=
                        $getElement$pi(ctx, index) << ((7 - (index & 7)) << 3);
            }
        }

        return ArrayᐸBitᐳ.$fromLongs(ctx, mutability, byteCount << 3, values);
    }

    // ----- Array internals -----------------------------------------------------------------------

    @Override
    protected String $elementToString(Ctx ctx, long index) {
        UInt8 c = UInt8.$box(getElement$p(ctx, index));
        return c.toString(ctx).toString();
    }

    @Override
    protected long $storageCapacity() {
        return $storageCapacity8bit();
    }

    @Override
    protected long $getElement(Ctx ctx, long index) {
        return $get8bitUnsignedElement(index);
    }

    @Override
    protected void $setElement(Ctx ctx, long index, long value) {
        $set8bitElement(index, value);
    }

    @Override
    protected long $cap2len(long cap) {
        return $cap2len8bits(cap);
    }

    /**
     * Internal method to create a UInt8 array from a long array.
     *
     * @param bytes  the size of the array in bytes
     */
    public static ArrayᐸUInt8ᐳ $fromLongs(Ctx ctx, long bytes, long... values) {
        return $fromLongs(ctx, null, bytes, values);
    }

    // ----- helper methods ------------------------------------------------------------------------

    /**
     * Internal method to create a UInt8 array from a long array.
     *
     * @param bytes  the size of the array in bytes
     */
    public static ArrayᐸUInt8ᐳ $fromLongs(Ctx ctx, Mutability mutability, long bytes, long... values) {
        TypeConstant type  = ctx.container.typeSystem.pool().typeByteArray();
        ArrayᐸUInt8ᐳ array = $new$p(ctx, type, bytes, false);
        array.$mut(mutability == null ? $CONSTANT : (int) mutability.$ordinal);
        array.$storage = values;
        array.$size((int) bytes);
        return array;
    }

    /**
     * Read a 64-bit segment of this array.
     * Used by {@link org.xvm.javajit.builders.NumberBuilder#loadConstructorLong}.
     *
     * @param index      the index of the 64-bit segment
     * @param bitLength  the bit length of the Number being constructed
     *
     * @return the segment, with the first bit stored in its most significant position
     */
    public long $toLong(Ctx ctx, int index, int bitLength) {
        long start   = (long) index << 6;
        long count   = Math.min(bitLength - start, Long.SIZE);
        long padding = (size$get$p(ctx) << 3) - bitLength;
        long value   = 0;
        for (long offset = 0; offset < count; offset++) {
            long source = padding + start + offset;
            long octet  = getElement$pi(ctx, source >>> 3);
            value |= (octet >>> (7 - (source & 7)) & 1) << (63 - offset);
        }
        return value;
    }

    @Override
    protected long $calculateHash(Ctx ctx) {
        return $calculate8BitUnsignedHash(ctx);
    }

    @Override
    protected void $deleteElements(long index, long count) {
        $delete8bit(index, count);
    }

    @Override
    protected void $insertElements(long index, long count) {
        $insert8bit(index, count);
    }

    // ---- Iterator implementation ----------------------------------------------------------------

    private class nIterator extends nBaseIterator implements IteratorᐸUInt8ᐳ {
        public nIterator(Ctx ctx) {
            super(ctx);
        }

        @Override
        public nType Element$get(Ctx ctx) {
            return nType.$ensureType(ctx, ctx.container.typeSystem.pool().typeUInt8());
        }
    }
}
