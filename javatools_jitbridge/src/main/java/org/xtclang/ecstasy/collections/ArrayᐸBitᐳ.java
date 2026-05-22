package org.xtclang.ecstasy.collections;

import java.util.Arrays;

import org.xtclang.ecstasy.Iterable;
import org.xtclang.ecstasy.IteratorᐸBitᐳ;
import org.xtclang.ecstasy.Object;
import org.xtclang.ecstasy.nType;
import org.xtclang.ecstasy.nRangeᐸInt64ᐳ;

import org.xtclang.ecstasy.numbers.Bit;
import org.xtclang.ecstasy.numbers.Int64;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.Ctx;

/**
 * Array of Bit, stored in an array of Java longs, sixty-four Bits per long.
 * <p>
 * Object header
 * xObj - 64 bits of flags
 * ---
 * Delegate - ref
 * Storage - ref
 */
public class ArrayᐸBitᐳ
        extends nLongBasedArray<ArrayᐸBitᐳ> {

    public ArrayᐸBitᐳ(Ctx ctx, TypeConstant type) {
        super(ctx, type);
    }

    // ----- Array API -----------------------------------------------------------------------------

    /**
     * @see {@link Array#$new$p}
     */
    public static ArrayᐸBitᐳ $new$p(Ctx ctx, TypeConstant type, long capacity, boolean _capacity) {
        assert !type.isImmutable();

        ctx.alloc(64); // REVIEW how big?
        ArrayᐸBitᐳ array = new ArrayᐸBitᐳ(ctx, type);
        array.$mut($MUTABLE);
        array.$capCfg(ctx, capacity);
        return array;
    }

    /**
     * @see {@link Array#$new$1$p}
     */
    public static ArrayᐸBitᐳ $new$1$p(Ctx ctx, TypeConstant type, long size, Object supply) {
        if (supply instanceof Bit boxed) {
            ctx.alloc(size); // REVIEW + HEADER_SIZE?
            ArrayᐸBitᐳ array = new ArrayᐸBitᐳ(ctx, type);
            array.$mut($FIXED);

            long fill = boxed.$value == 0 ? 0 : -1L;

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
    public static ArrayᐸBitᐳ $new$2$p(Ctx ctx, TypeConstant type, Mutability mutability, Iterable elements) {
        // TODO
        throw new UnsupportedOperationException();
    }

    /**
     * @see {@link Array#$new$3}
     */
    public static ArrayᐸBitᐳ $new$3$p(Ctx ctx, TypeConstant type, ArrayᐸBitᐳ that) {
        // TODO
        throw new UnsupportedOperationException();
    }

    public Bit getElement(Ctx ctx, Int64 index) {
        return Bit.$box(getElement$pi(ctx, index.$value));
    }

    public int getElement$p(Ctx ctx, long index) {
        return getElement$pi(ctx, index);
    }

    public int getElement$pi(Ctx ctx, long index) {
        return (int) $getElement$pi(ctx, index);
    }

    public void setElement(Ctx ctx, Int64 index, Object value) {
        setElement$pi(ctx, index.$value, ((Bit) value).$value);
    }

    public void setElement$p(Ctx ctx, long index, int value) {
        setElement$pi(ctx, index, value);
    }

    public IteratorᐸBitᐳ iterator(Ctx ctx) {
        return new nIterator(ctx);
    }

    @Override
    public ArrayᐸBitᐳ add(Ctx ctx, Object element) {
        return add$p(ctx, ((Bit) element).$value);
    }

    public ArrayᐸBitᐳ add$p(Ctx ctx, int value) {
        return super.add$p(ctx, value);
    }

    @Override
    public ArrayᐸBitᐳ slice(Ctx ctx, nRangeᐸInt64ᐳ range) {
        return (ArrayᐸBitᐳ) super.slice(ctx, range);
    }

    // ----- Array internals -----------------------------------------------------------------------

    @Override
    protected String $elementToString(Ctx ctx, long index) {
        int i = getElement$pi(ctx, index);
        return String.valueOf(i);
    }

    @Override
    protected long $storageCapacity() {
        return $storageCapacity1bit();
    }

    @Override
    protected long $getElement(Ctx ctx, long index) {
        return $get1bitElement(index);
    }

    @Override
    protected void $setElement(Ctx ctx, long index, long value) {
        $set1bitElement(index, value);
    }

    @Override
    protected long $cap2len(long cap) {
        return $cap2len1bit(cap);
    }

    /**
     * Internal method to create a bit array from a long array.
     * <p>
     * This is called by various number types to return a bit array representation of the number.
     */
    public static ArrayᐸBitᐳ $fromLongs(Ctx ctx, Mutability mutability, long bits, long... values) {
        TypeConstant type  = ctx.container.typeSystem.pool().typeBitArray();
        ArrayᐸBitᐳ   array = $new$p(ctx, type, bits , false);
        array.$mut(mutability == null ? $CONSTANT : (int) mutability.$ordinal);
        array.$storage = values;
        array.$size((int) bits);
        return array;
    }

    @Override
    protected long $calculateHash(Ctx ctx) {
        return $calculate1BitHash(ctx);
    }

    // ---- Iterator implementation ----------------------------------------------------------------

    private class nIterator extends nBaseIterator implements IteratorᐸBitᐳ {
        public nIterator(Ctx ctx) {
            super(ctx);
        }

        @Override
        public nType Element$get(Ctx ctx) {
            return nType.$ensureType(ctx, ctx.container.typeSystem.pool().typeBit());
        }
    }
}
