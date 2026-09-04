package org.xtclang.ecstasy.collections;

import org.xtclang.ecstasy.Iterable;
import org.xtclang.ecstasy.IteratorᐸDurationᐳ;
import org.xtclang.ecstasy.Object;
import org.xtclang.ecstasy.nType;

import org.xtclang.ecstasy.numbers.Int64;

import org.xtclang.ecstasy.temporal.Duration;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.Ctx;

/**
 * Array of Duration values stored as pairs of Java {@code long} values.
 */
public class ArrayᐸDurationᐳ
        extends nLongLongBasedArray<ArrayᐸDurationᐳ> {
    public ArrayᐸDurationᐳ(Ctx ctx, TypeConstant type) {
        super(ctx, type);
    }

    // ----- Array API -----------------------------------------------------------------------------

    /**
     * @see Array#$new$p
     */
    public static ArrayᐸDurationᐳ $new$p(
            Ctx ctx, TypeConstant type, long capacity, boolean _capacity) {
        assert !type.isImmutable();

        ctx.alloc(64);
        ArrayᐸDurationᐳ array = new ArrayᐸDurationᐳ(ctx, type);
        array.$mut($MUTABLE);
        array.$capCfg(ctx, capacity);
        return array;
    }

    /**
     * @see Array#$new$1$p
     */
    public static ArrayᐸDurationᐳ $new$1$p(
            Ctx ctx, TypeConstant type, long size, Object supply) {
        if (supply instanceof Duration boxed) {
            ctx.alloc(size * 16);
            ArrayᐸDurationᐳ array = new ArrayᐸDurationᐳ(ctx, type);
            array.$mut($FIXED);
            array.$fill128(ctx, size, boxed.picoseconds$0, boxed.picoseconds$1);
            return array;
        }
        throw new UnsupportedOperationException();
    }

    /**
     * @see Array#$new$2
     */
    public static ArrayᐸDurationᐳ $new$2$p(
            Ctx ctx, TypeConstant type, Mutability mutability, Iterable elements) {
        long size = elements.size$get$p(ctx);
        ctx.alloc(size * 16);
        ArrayᐸDurationᐳ array = new ArrayᐸDurationᐳ(ctx, type);
        array.$mut($MUTABLE);
        array.addAll(ctx, elements);
        array.$mut((int) mutability.ordinal$get$p(ctx));
        return array;
    }

    /**
     * @see Array#$new$3
     */
    public static ArrayᐸDurationᐳ $new$3$p(
            Ctx ctx, TypeConstant type, ArrayᐸDurationᐳ that) {
        throw new UnsupportedOperationException();
    }

    public Duration getElement(Ctx ctx, Int64 index) {
        long low = getElement$pi(ctx, index.$value);
        return Duration.$box(low, ctx.i0);
    }

    public void setElement(Ctx ctx, Int64 index, Object value) {
        Duration duration = (Duration) value;
        setElement$pi(ctx, index.$value, duration.picoseconds$0, duration.picoseconds$1);
    }

    public IteratorᐸDurationᐳ iterator(Ctx ctx) {
        return new nIterator(ctx);
    }

    @Override
    public ArrayᐸDurationᐳ add(Ctx ctx, Object element) {
        Duration duration = (Duration) element;
        return add$p(ctx, duration.picoseconds$0, duration.picoseconds$1);
    }

    public ArrayᐸDurationᐳ add$p(Ctx ctx, long lowPicos, long highPicos) {
        return $add128(ctx, lowPicos, highPicos);
    }

    public ArrayᐸDurationᐳ insert$p(
            Ctx ctx, long index, long lowPicos, long highPicos) {
        return $insert128(ctx, index, lowPicos, highPicos);
    }

    @Override
    public ArrayᐸDurationᐳ delete$p(Ctx ctx, long index) {
        return $delete128(ctx, index);
    }

    // ----- Array internals -----------------------------------------------------------------------

    @Override
    protected String $elementToString(Ctx ctx, long index) {
        long     low      = getElement$pi(ctx, index);
        Duration duration = Duration.$box(low, ctx.i0);
        return duration.toString(ctx).toString();
    }

    // ----- Iterator implementation ---------------------------------------------------------------

    private class nIterator
            extends n128BitIterator
            implements IteratorᐸDurationᐳ {
        public nIterator(Ctx ctx) {
            super(ctx);
        }

        @Override
        public nType Element$get(Ctx ctx) {
            return nType.$ensureType(ctx, ctx.container.typeSystem.pool().typeDuration());
        }
    }
}
