package org.xtclang.ecstasy.collections;

import org.xtclang.ecstasy.Iterable;
import org.xtclang.ecstasy.nObj;
import org.xtclang.ecstasy.nRangeᐸInt64ᐳ;

import org.xtclang.ecstasy.numbers.Bit;

import org.xvm.asm.ConstantPool;

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
     * Array Constructor: construct(Int capacity = 0)
     */
    public static ArrayᐸBitᐳ $new$p(Ctx ctx, TypeConstant type, long capacity, boolean _capacity) {
        assert !type.isImmutable();

        ctx.alloc(64); // REVIEW how big?
        ArrayᐸBitᐳ array = new ArrayᐸBitᐳ(ctx, type);
        array.$mut($MUTABLE);
        array.$capCfg(ctx, capacity);
        return array;
    }

    public static ArrayᐸBitᐳ $new$1$p(Ctx ctx, TypeConstant type, long size, nObj supply) {
        // TODO
        throw new UnsupportedOperationException();
    }

    public static ArrayᐸBitᐳ $new$2$p(Ctx ctx, TypeConstant type, Mutability mutability, Iterable elements) {
        // TODO
        throw new UnsupportedOperationException();
    }

    public static ArrayᐸBitᐳ $new$3$p(Ctx ctx, TypeConstant type, ArrayᐸBitᐳ that) {
        // TODO
        throw new UnsupportedOperationException();
    }

    @Override public Bit getElement$p(Ctx ctx, long index) {
        return Bit.$box(getElement$pi(ctx, index));
    }

    public int getElement$pi(Ctx ctx, long index) {
        return (int) $getElement$pi(ctx, index);
    }

    @Override public void setElement$p(Ctx ctx, long index, nObj value) {
        setElement$pi(ctx, index, ((Bit) value).$value);
    }

    @Override
    public ArrayᐸBitᐳ add(Ctx ctx, nObj element) {
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
    protected TypeConstant $elementType(ConstantPool pool) {
        return pool.typeBit();
    }

    @Override
    protected String $elementToString(Ctx ctx, long index) {
        Bit c = getElement$p(ctx, index);
        return c.toString(ctx).toString();
    }

    @Override
    protected long $storageCapacity() {
        return $storageCapacity1bit();
    }

    protected long $getElement(Ctx ctx, long index) {
        return $get1bitElement(index);
    }

    @Override
    protected void $setElement(Ctx ctx, long index, long value) {
        $set1bitElement(index, value);
    }

    @Override
    protected int $cap2len(int cap) {
        return $cap2len1bit(cap);
    }
}
