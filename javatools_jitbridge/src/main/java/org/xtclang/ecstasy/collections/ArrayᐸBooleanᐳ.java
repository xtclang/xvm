package org.xtclang.ecstasy.collections;

import java.util.Arrays;

import org.xtclang.ecstasy.Boolean;
import org.xtclang.ecstasy.Iterable;
import org.xtclang.ecstasy.Object;
import org.xtclang.ecstasy.nRangeᐸInt64ᐳ;

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
        // TODO
        throw new UnsupportedOperationException();
    }

    /**
     * @see {@link Array#$new$3}
     */
    public static ArrayᐸBooleanᐳ $new$3$p(Ctx ctx, TypeConstant type, ArrayᐸBooleanᐳ that) {
        // TODO
        throw new UnsupportedOperationException();
    }

    @Override public Boolean getElement$p(Ctx ctx, long index) {
        return Boolean.$box(getElement$pi(ctx, index));
    }

    public boolean getElement$pi(Ctx ctx, long index) {
        return $getElement$pi(ctx, index) != 0;
    }

    @Override public void setElement$p(Ctx ctx, long index, Object value) {
        setElement$pb(ctx, index, ((Boolean) value).$value);
    }

    public void setElement$pb(Ctx ctx, long index, boolean value) {
        setElement$pi(ctx, index, value ? 1 : 0);
    }

    @Override
    public ArrayᐸBooleanᐳ add(Ctx ctx, Object element) {
        return add$p(ctx, ((Boolean) element).$value);
    }

    public ArrayᐸBooleanᐳ add$p(Ctx ctx, boolean value) {
        return super.add$p(ctx, value ? 1L : 0L);
    }

    @Override
    public ArrayᐸBooleanᐳ slice(Ctx ctx, nRangeᐸInt64ᐳ range) {
        return (ArrayᐸBooleanᐳ) super.slice(ctx, range);
    }

    // ----- Array internals -----------------------------------------------------------------------

    @Override
    protected java.lang.String $elementToString(Ctx ctx, long index) {
        Boolean c = getElement$p(ctx, index);
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
}
