package org.xtclang.ecstasy.collections;

import java.util.Arrays;

import org.xtclang.ecstasy.Iterable;
import org.xtclang.ecstasy.nObj;
import org.xtclang.ecstasy.nRangeᐸInt64ᐳ;

import org.xtclang.ecstasy.numbers.UInt8;

import org.xvm.asm.ConstantPool;

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
     * Array Constructor: construct(Int capacity = 0)
     */
    public static ArrayᐸUInt8ᐳ $new$p(Ctx ctx, TypeConstant type, long capacity, boolean _capacity) {
        assert !type.isImmutable();

        ctx.alloc(64); // REVIEW how big?
        ArrayᐸUInt8ᐳ array = new ArrayᐸUInt8ᐳ(ctx, type);
        array.$mut($MUTABLE);
        array.$capCfg(ctx, capacity);
        return array;
    }

    public static ArrayᐸUInt8ᐳ $new$1$p(Ctx ctx, TypeConstant type, long size, nObj supply) {
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

    public static ArrayᐸUInt8ᐳ $new$2$p(Ctx ctx, TypeConstant type, Mutability mutability, Iterable elements) {
        // TODO
        throw new UnsupportedOperationException();
    }

    public static ArrayᐸUInt8ᐳ $new$3$p(Ctx ctx, TypeConstant type, ArrayᐸUInt8ᐳ that) {
        // TODO
        throw new UnsupportedOperationException();
    }

    @Override public UInt8 getElement$p(Ctx ctx, long index) {
        return UInt8.$box(getElement$pi(ctx, index));
    }

    public int getElement$pi(Ctx ctx, long index) {
        return (int) $getElement$pi(ctx, index);
    }

    @Override public void setElement$p(Ctx ctx, long index, nObj value) {
        setElement$pi(ctx, index, ((UInt8) value).$value);
    }

    @Override
    public ArrayᐸUInt8ᐳ add(Ctx ctx, nObj element) {
        return add$p(ctx, ((UInt8) element).$value);
    }

    public ArrayᐸUInt8ᐳ add$p(Ctx ctx, int value) {
        return super.add$p(ctx, value);
    }

    @Override
    public ArrayᐸUInt8ᐳ slice(Ctx ctx, nRangeᐸInt64ᐳ range) {
        return (ArrayᐸUInt8ᐳ) super.slice(ctx, range);
    }

    // ----- Array internals -----------------------------------------------------------------------

    @Override
    protected TypeConstant $elementType(ConstantPool pool) {
        return pool.typeUInt8();
    }

    @Override
    protected String $elementToString(Ctx ctx, long index) {
        UInt8 c = getElement$p(ctx, index);
        return c.toString(ctx).toString();
    }

    @Override
    protected long $storageCapacity() {
        return $storageCapacity8bit();
    }

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
}
