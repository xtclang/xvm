package org.xtclang.ecstasy.collections;

import java.util.Arrays;

import org.xtclang.ecstasy.Boolean;
import org.xtclang.ecstasy.Iterable;
import org.xtclang.ecstasy.IteratorᐸBooleanᐳ;
import org.xtclang.ecstasy.Object;
import org.xtclang.ecstasy.nType;
import org.xtclang.ecstasy.nRangeᐸInt64ᐳ;

import org.xtclang.ecstasy.numbers.Int64;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.Ctx;

/**
 * Array of Boolean, stored in an array of Java longs, sixty-four Booleans per long.
 * <p>
 * Object header
 * xObj - 64 bits of flags
 * ---
 * Delegate - ref
 * Storage - ref
 */
public class ArrayᐸBooleanᐳ
        extends nLongBasedArray<ArrayᐸBooleanᐳ> {

    public ArrayᐸBooleanᐳ(Ctx ctx, TypeConstant type) {
        super(ctx, type);
    }

    // ----- Array API -----------------------------------------------------------------------------

    /**
     * @see {@link Array#$new$p}
     */
    public static ArrayᐸBooleanᐳ $new$p(Ctx ctx, TypeConstant type, long capacity, boolean _capacity) {
        assert !type.isImmutable();

        ctx.alloc(64); // REVIEW how big?
        ArrayᐸBooleanᐳ array = new ArrayᐸBooleanᐳ(ctx, type);
        array.$mut($MUTABLE);
        array.$capCfg(ctx, capacity);
        return array;
    }

    /**
     * @see {@link Array#$new$1$p}
     */
    public static ArrayᐸBooleanᐳ $new$1$p(Ctx ctx, TypeConstant type, long size, Object supply) {
        if (supply instanceof Boolean boxed) {
            ctx.alloc(size); // REVIEW + HEADER_SIZE?
            ArrayᐸBooleanᐳ array = new ArrayᐸBooleanᐳ(ctx, type);
            array.$mut($FIXED);

            long fill = boxed.$value ? -1L : 0L;

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
    public static ArrayᐸBooleanᐳ $new$2$p(Ctx ctx, TypeConstant type, Mutability mutability, Iterable elements) {
        long size = elements.size$get$p(ctx);
        ctx.alloc(size / 8); // REVIEW + HEADER_SIZE?
        ArrayᐸBooleanᐳ array = new ArrayᐸBooleanᐳ(ctx, type);
        array.$mut($MUTABLE);
        array.addAll(ctx, elements);
        array.$mut((int) mutability.ordinal$get$p(ctx));
        return array;
    }

    /**
     * @see {@link Array#$new$3}
     */
    public static ArrayᐸBooleanᐳ $new$3$p(Ctx ctx, TypeConstant type, ArrayᐸBooleanᐳ that) {
        // TODO
        throw new UnsupportedOperationException();
    }

    public Boolean getElement(Ctx ctx, Int64 index) {
        return Boolean.$box(getElement$pi(ctx, index.$value));
    }

    public boolean getElement$p(Ctx ctx, long index) {
        return getElement$pi(ctx, index);
    }

    public boolean getElement$pi(Ctx ctx, long index) {
        return $getElement$pi(ctx, index) != 0;
    }

    public void setElement(Ctx ctx, Int64 index, Object value) {
        setElement$pi(ctx, index.$value, ((Boolean) value).$value ? 1 : 0);
    }

    public void setElement$p(Ctx ctx, long index, boolean value) {
        setElement$pi(ctx, index, value ? 1 : 0);
    }

    public IteratorᐸBooleanᐳ iterator(Ctx ctx) {
        return new nIterator(ctx);
    }

    @Override
    public ArrayᐸBooleanᐳ add(Ctx ctx, Object element) {
        return add$p(ctx, ((Boolean) element).$value);
    }

    public ArrayᐸBooleanᐳ add$p(Ctx ctx, boolean value) {
        return super.add$p(ctx, value ? 1L : 0L);
    }

    public ArrayᐸBooleanᐳ insert$p(Ctx ctx, long index, boolean value) {
        if (index < 0 || index > size$get$p(ctx)) {
            throw $oob(ctx, index);
        }
        $insert(ctx, index, 1);
        $set1bitElement(index, value ? 1 : 0);
        return this;
    }

    @Override
    public ArrayᐸBooleanᐳ delete$p(Ctx ctx, long index) {
        if (index < 0 || index >= size$get$p(ctx)) {
            throw $oob(ctx, index);
        }
        $delete(ctx, index, 1);
        return this;
    }

    @Override
    public ArrayᐸBooleanᐳ slice(Ctx ctx, nRangeᐸInt64ᐳ range) {
        return (ArrayᐸBooleanᐳ) super.slice(ctx, range);
    }

    // ----- Array internals -----------------------------------------------------------------------

    @Override
    protected String $elementToString(Ctx ctx, long index) {
        Boolean c = Boolean.$box(getElement$pi(ctx, index));
        return c.toString(ctx).toString();
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

    @Override
    protected long $calculateHash(Ctx ctx) {
        return $calculate1BitHash(ctx);
    }

    @Override
    protected void $deleteElements(long index, long count) {
        $delete1bit(index, count);
    }

    @Override
    protected void $insertElements(long index, long count) {
        $insert1bit(index, count);
    }

    // ---- Iterator implementation ----------------------------------------------------------------

    private class nIterator extends nBaseIterator implements IteratorᐸBooleanᐳ {
        public nIterator(Ctx ctx) {
            super(ctx);
        }

        @Override
        public nType Element$get(Ctx ctx) {
            return nType.$ensureType(ctx, ctx.container.typeSystem.pool().typeBoolean());
        }
    }
}
