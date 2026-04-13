package org.xtclang.ecstasy.collections;

import java.util.Arrays;

import org.xtclang.ecstasy.Iterable;
import org.xtclang.ecstasy.nObj;
import org.xtclang.ecstasy.nRangeᐸInt64ᐳ;

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

    public static ArrayᐸNibbleᐳ $new$1$p(Ctx ctx, TypeConstant type, long size, nObj supply) {
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
        // TODO
        throw new UnsupportedOperationException();
    }

    public static ArrayᐸNibbleᐳ $new$3$p(Ctx ctx, TypeConstant type, ArrayᐸNibbleᐳ that) {
        // TODO
        throw new UnsupportedOperationException();
    }

    @Override public Nibble getElement$p(Ctx ctx, long index) {
        return Nibble.$box(getElement$pi(ctx, index));
    }

    public int getElement$pi(Ctx ctx, long index) {
        return (int) $getElement$pi(ctx, index);
    }

    @Override public void setElement$p(Ctx ctx, long index, nObj value) {
        setElement$pi(ctx, index, ((Nibble) value).$value);
    }

    @Override
    public ArrayᐸNibbleᐳ add(Ctx ctx, nObj element) {
        return add$p(ctx, ((Nibble) element).$value);
    }

    public ArrayᐸNibbleᐳ add$p(Ctx ctx, int value) {
        return super.add$p(ctx, value);
    }

    @Override
    public ArrayᐸNibbleᐳ slice(Ctx ctx, nRangeᐸInt64ᐳ range) {
        return (ArrayᐸNibbleᐳ) super.slice(ctx, range);
    }

    // ----- Array internals -----------------------------------------------------------------------

    @Override
    protected TypeConstant $elementType(ConstantPool pool) {
        return pool.typeNibble();
    }

    @Override
    protected String $elementToString(Ctx ctx, long index) {
        Nibble c = getElement$p(ctx, index);
        return c.toString(ctx).toString();
    }

    @Override
    protected long $storageCapacity() {
        return $storageCapacity4bit();
    }

    protected long $getElement(Ctx ctx, long index) {
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
}
