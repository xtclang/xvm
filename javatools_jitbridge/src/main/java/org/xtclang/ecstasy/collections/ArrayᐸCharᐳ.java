package org.xtclang.ecstasy.collections;

import java.util.Arrays;

import org.xtclang.ecstasy.Exception;
import org.xtclang.ecstasy.IterableᐸCharᐳ;
import org.xtclang.ecstasy.IteratorᐸCharᐳ;
import org.xtclang.ecstasy.Object;
import org.xtclang.ecstasy.nType;
import org.xtclang.ecstasy.nRangeᐸInt64ᐳ;

import org.xtclang.ecstasy.numbers.Int64;

import org.xtclang.ecstasy.text.Char;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.Ctx;

/**
 * Array of Unicode characters, stored in an array of Java longs, either 8x per long (0x00..0xFF) or
 * 3x per long (0x00..0x10FFFF).
 *
 * Object header
 * xObj - 64 bits of flags
 * ---
 * Delegate - ref
 * Storage - ref
 *
 * Size - 48 bits
 * Mutability - 2 bits
 * Format (8 vs 21) - 1 bit
 *
 * Capacity - 48 bits (pre storage)
 * Hash - 64 bits (only if mutability==Constant, requires storage != null)
 */
public class ArrayᐸCharᐳ
        extends nLongBasedArray<ArrayᐸCharᐳ> {

    public ArrayᐸCharᐳ(Ctx ctx, TypeConstant type) {
        super(ctx, type);
    }

    public ArrayᐸCharᐳ(Ctx ctx, TypeConstant type, long[] data, long size, boolean utf21) {
        super(ctx, type, data, size);
        this.$utf21 = utf21;
    }

    public boolean $utf21;      // REVIEW: this could just use a bit in (steal a bit from) $sizeEtc

    // ----- Array API -----------------------------------------------------------------------------

    /**
     * @see {@link Array#$new$p}
     */
    public static ArrayᐸCharᐳ $new$p(Ctx ctx, TypeConstant type, long capacity, boolean _capacity) {
        if (type.isImmutable()) {
            // an immutable array can use its own type to create a new array
            type = type.removeImmutable();
        }

        ctx.alloc(64); // REVIEW how big?
        ArrayᐸCharᐳ array = new ArrayᐸCharᐳ(ctx, type);
        array.$mut($MUTABLE);
        array.$capCfg(ctx, capacity);
        return array;
    }

    /**
     * @see {@link Array#$new$1$p}
     */
    public static ArrayᐸCharᐳ $new$1$p(Ctx ctx, TypeConstant type, long size, Object supply) {
        if (supply instanceof Char boxed) {
            int value = boxed.$value;
            if (value < 0x100) {
                ctx.alloc(size); // REVIEW + HEADER_SIZE?
                ArrayᐸCharᐳ array = new ArrayᐸCharᐳ(ctx, type);
                array.$mut($FIXED);
                array.$utf21 = false;

                long lval = value & 0xFF;
                long fill = lval == 0 ? 0 : lval | (lval << 8) | (lval << 16) | (lval << 24);
                fill |= (fill << 32);

                if (array.$growInPlace(ctx, size)) {
                    Arrays.fill(array.$storage, fill);
                    array.$size((int) size);
                    return array;
                } else {
                    throw array.$oob(ctx, size);
                }
            }
            // TODO handle utf21
        }
        // TODO
        throw new UnsupportedOperationException();
    }

    /**
     * @see {@link Array#$new$2}
     */
    public static ArrayᐸCharᐳ $new$2(Ctx ctx, TypeConstant type, Mutability mutability, IterableᐸCharᐳ elements) {
        long size = elements.size$get$p(ctx);
        ArrayᐸCharᐳ array = $new$p(ctx, type, size, false);
        array.$mut($MUTABLE);
        for (IteratorᐸCharᐳ it = elements.iterator(ctx); it.next$p(ctx);) {
            array.add$p(ctx, ctx.i0);
        }
        array.$mut((int) mutability.ordinal$get$p(ctx));
        return array;
    }

    /**
     * @see {@link Array#$new$3}
     */
    public static ArrayᐸCharᐳ $new$3(Ctx ctx, TypeConstant type, ArrayᐸCharᐳ that) {
        // TODO
        throw new UnsupportedOperationException();
    }

    public Char getElement(Ctx ctx, Int64 index) {
        return Char.$box(getElement$pi(ctx, index.$value));
    }

    public int getElement$p(Ctx ctx, long index) {
        return getElement$pi(ctx, index);
    }

    /**
     * Optimized "primitive intrinsic" form of the getElement() method. The JIT will use this method
     * in lieu of getElement$p() whenever it has enough information to do so.
     */
    public int getElement$pi(Ctx ctx, long index) {
        return (int) $getElement$pi(ctx, index);
    }

    public void setElement(Ctx ctx, Int64 index, Object value) {
        setElement$pi(ctx, index.$value, ((Char) value).$value);
    }

    public void setElement$p(Ctx ctx, long index, int value) {
        setElement$pi(ctx, index, value);
    }

    @Override
    public ArrayᐸCharᐳ add(Ctx ctx, Object element) {
        return add$p(ctx, ((Char) element).$value);
    }

    public ArrayᐸCharᐳ add$p(Ctx ctx, int ch) {
        return super.add$p(ctx, (long) ch);
    }

    public ArrayᐸCharᐳ insert$p(Ctx ctx, long index, int value) {
        if (index < 0 || index > size$get$p(ctx)) {
            throw $oob(ctx, index);
        }
        $insert(ctx, index, 1);
        $setElement(ctx, index, value);
        return this;
    }

    @Override
    public ArrayᐸCharᐳ delete$p(Ctx ctx, long index) {
        if (index < 0 || index >= size$get$p(ctx)) {
            throw $oob(ctx, index);
        }
        $delete(ctx, index, 1);
        return this;
    }

    public ArrayᐸCharᐳ addAll(Ctx ctx, IterableᐸCharᐳ values) {
        IteratorᐸCharᐳ iter = values.iterator(ctx);
        while (iter.next$p(ctx)) {
            add$p(ctx, ctx.i0);
        }
        return this;
    }

    @Override
    public ArrayᐸCharᐳ slice(Ctx ctx, nRangeᐸInt64ᐳ range) {
        return (ArrayᐸCharᐳ) super.slice(ctx, range);
    }

    @Override
    public ArrayᐸCharᐳ freeze$p(Ctx ctx, boolean inPlace, boolean inPlace$dflt) {
        return (ArrayᐸCharᐳ) super.freeze$p(ctx, inPlace, inPlace$dflt);
    }

    // ----- Array internals -----------------------------------------------------------------------

    // ToDo GG/JK remove this (and sub classes) when toString() can be generated by the JIT
    @Override
    protected String $elementToString(Ctx ctx, long index) {
        Char c = Char.$box(getElement$pi(ctx, index));
        return c.toString(ctx).toString();
    }

    @Override
    protected long $storageCapacity() {
        return $utf21 ? $storageCapacity21bit() : $storageCapacity8bit();
    }

    @Override
    protected long $getElement(Ctx ctx, long index) {
        return $utf21 ? $get21bitUnsignedElement(index)
                : $get8bitUnsignedElement(index);
    }

    @Override
    protected void $setElement(Ctx ctx, long index, long value) {
        if (!$utf21) {
            if (value < 0x100) {
                $set8bitElement(index, value);
                return;
            }
            // TODO convert from 8-bit format to 21-bit format
        }
        // 21-bit version of: $storage[(int) index] = value;
        throw Exception.$unsupported(ctx, null); // TODO
    }

    @Override
    protected long $cap2len(long cap) {
        return $utf21 ? $cap2len21bits(cap) : $cap2len8bits(cap);
    }


    @Override
    protected long $calculateHash(Ctx ctx) {
        return $utf21 ? $calculate21BitUnsignedHash(ctx)
                      : $calculate8BitUnsignedHash(ctx);
    }

    @Override
    protected void $deleteElements(long index, long count) {
        if ($utf21) {
            $delete21bit(index, count);
        } else {
            $delete8bit(index, count);
        }
    }

    @Override
    protected void $insertElements(long index, long count) {
        if ($utf21) {
            $insert21bit(index, count);
        } else {
            $insert8bit(index, count);
        }
    }

    public IteratorᐸCharᐳ iterator(Ctx ctx) {
        return new nIterator(ctx);
    }

    // ---- Iterator implementation ----------------------------------------------------------------

    private class nIterator extends nBaseIterator implements IteratorᐸCharᐳ {
        public nIterator(Ctx ctx) {
            super(ctx);
        }

        @Override
        public nType Element$get(Ctx ctx) {
            return nType.$ensureType(ctx, ctx.container.typeSystem.pool().typeChar());
        }
    }
}
