package org.xtclang.ecstasy.collections;

import java.util.Arrays;

import org.xtclang.ecstasy.Iterable;
import org.xtclang.ecstasy.IteratorᐸNibbleᐳ;
import org.xtclang.ecstasy.Object;
import org.xtclang.ecstasy.nType;
import org.xtclang.ecstasy.nRangeᐸInt64ᐳ;

import org.xtclang.ecstasy.numbers.Int64;
import org.xtclang.ecstasy.numbers.Nibble;

import org.xvm.asm.ConstantPool;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.Ctx;

/**
 * Array of Nibble, stored in an array of Java longs, sixteen Nibbles per long.
 * <p>
 * Object header
 * xObj - 64 bits of flags
 * ---
 * Delegate - ref
 * Storage - ref
 */
public class ArrayᐸNibbleᐳ
        extends nLongBasedArray<ArrayᐸNibbleᐳ> {

    public ArrayᐸNibbleᐳ(Ctx ctx, TypeConstant type) {
        super(ctx, type);
    }

    // ----- Array API -----------------------------------------------------------------------------

    /**
     * Array Constructor: construct(Int capacity = 0)
     */
    public static ArrayᐸNibbleᐳ $new$p(Ctx ctx, TypeConstant type, long capacity, boolean _capacity) {
        assert !type.isImmutable();

        ctx.alloc(64); // REVIEW how big?
        ArrayᐸNibbleᐳ array = new ArrayᐸNibbleᐳ(ctx, type);
        array.$mut($MUTABLE);
        array.$capCfg(ctx, capacity);
        return array;
    }

    public static ArrayᐸNibbleᐳ $new$1$p(Ctx ctx, TypeConstant type, long size, Object supply) {
        if (supply instanceof Nibble boxed) {
            ctx.alloc(size); // REVIEW + HEADER_SIZE?
            ArrayᐸNibbleᐳ array = new ArrayᐸNibbleᐳ(ctx, type);
            array.$mut($FIXED);

            long value = boxed.$value & 0xF;
            long fill  = value == 0 ? 0 : value | (value << 4) | (value << 8) | (value << 12);
            fill |= (fill << 16);
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

    public static ArrayᐸNibbleᐳ $new$2$p(Ctx ctx, TypeConstant type, Mutability mutability, Iterable elements) {
        long size = elements.size$get$p(ctx);
        ctx.alloc(size / 2); // REVIEW + HEADER_SIZE?
        ArrayᐸNibbleᐳ array = new ArrayᐸNibbleᐳ(ctx, type);
        array.$mut($MUTABLE);
        array.addAll(ctx, elements);
        array.$mut((int) mutability.ordinal$get$p(ctx));
        return array;
    }

    public static ArrayᐸNibbleᐳ $new$3$p(Ctx ctx, TypeConstant type, ArrayᐸNibbleᐳ that) {
        // TODO
        throw new UnsupportedOperationException();
    }

    public Nibble getElement(Ctx ctx, Int64 index) {
        return Nibble.$box(getElement$pi(ctx, index.$value));
    }

    public int getElement$p(Ctx ctx, long index) {
        return getElement$pi(ctx, index);
    }

    public int getElement$pi(Ctx ctx, long index) {
        return (int) $getElement$pi(ctx, index);
    }

    public void setElement(Ctx ctx, Int64 index, Object value) {
        setElement$pi(ctx, index.$value, ((Nibble) value).$value);
    }

    public void setElement$p(Ctx ctx, long index, int value) {
        setElement$pi(ctx, index, value);
    }

    public IteratorᐸNibbleᐳ iterator(Ctx ctx) {
        return new nIterator(ctx);
    }

    @Override
    public ArrayᐸNibbleᐳ add(Ctx ctx, Object element) {
        return add$p(ctx, ((Nibble) element).$value);
    }

    public ArrayᐸNibbleᐳ add$p(Ctx ctx, int value) {
        return super.add$p(ctx, value);
    }

    public ArrayᐸNibbleᐳ insert$p(Ctx ctx, long index, int value) {
        if (index < 0 || index > size$get$p(ctx)) {
            throw $oob(ctx, index);
        }
        $insert(ctx, index, 1);
        $set4bitElement(index, value);
        return this;
    }

    @Override
    public ArrayᐸNibbleᐳ delete$p(Ctx ctx, long index) {
        if (index < 0 || index >= size$get$p(ctx)) {
            throw $oob(ctx, index);
        }
        $delete(ctx, index, 1);
        return this;
    }

    @Override
    public ArrayᐸNibbleᐳ slice(Ctx ctx, nRangeᐸInt64ᐳ range) {
        return (ArrayᐸNibbleᐳ) super.slice(ctx, range);
    }

    // ----- Array internals -----------------------------------------------------------------------

    @Override
    protected String $elementToString(Ctx ctx, long index) {
        Nibble c = Nibble.$box(getElement$p(ctx, index));
        return c.toString(ctx).toString();
    }

    @Override
    protected long $storageCapacity() {
        return $storageCapacity4bit();
    }

    @Override protected long $getElement(Ctx ctx, long index) {
        return $get4bitUnsignedElement(index);
    }

    @Override
    protected void $setElement(Ctx ctx, long index, long value) {
        $set4bitElement(index, value);
    }

    @Override
    protected long $cap2len(long cap) {
        return $cap2len4bits(cap);
    }

    /**
     * Internal method to create a nibble array from a long array.
     * <p>
     * This is called by various number types to return a nibble array representation of the number.
     */
    public static ArrayᐸNibbleᐳ $fromLongs(Ctx ctx, Mutability mutability, long bits, long... values) {
        ConstantPool  pool = ctx.container.typeSystem.pool();
        TypeConstant  type  = pool.ensureClassTypeConstant(pool.clzArray(), null, pool.typeNibble());
        long          size  = bits / 4;
        ArrayᐸNibbleᐳ array = $new$p(ctx, type, size, false);
        array.$mut(mutability == null ? $CONSTANT : (int) mutability.$ordinal);
        array.$storage = values;
        array.$size((int) size);
        return array;
    }

    @Override
    protected long $calculateHash(Ctx ctx) {
        return $calculate4BitUnsignedHash(ctx);
    }

    @Override
    protected void $deleteElements(long index, long count) {
        $delete4bit(index, count);
    }

    @Override
    protected void $insertElements(long index, long count) {
        $insert4bit(index, count);
    }

    // ---- Iterator implementation ----------------------------------------------------------------

    private class nIterator extends nBaseIterator implements IteratorᐸNibbleᐳ {
        public nIterator(Ctx ctx) {
            super(ctx);
        }

        @Override
        public nType Element$get(Ctx ctx) {
            return nType.$ensureType(ctx, ctx.container.typeSystem.pool().typeNibble());
        }
    }
}
