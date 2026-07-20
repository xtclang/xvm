package org.xtclang.ecstasy.collections;

import org.xtclang.ecstasy.Exception;
import org.xtclang.ecstasy.IteratorᐸInt64ᐳ;
import org.xtclang.ecstasy.Object;

import java.util.Arrays;

import org.xtclang.ecstasy.Iterable;
import org.xtclang.ecstasy.nException;
import org.xtclang.ecstasy.nFunction;
import org.xtclang.ecstasy.nRangeᐸInt64ᐳ;

import org.xtclang.ecstasy.nType;
import org.xtclang.ecstasy.numbers.Int64;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.Ctx;

/**
 * Array of Int64, stored in an array of Java longs.
 * <p>
 * Object header
 * xObj - 64 bits of flags
 * ---
 * Delegate - ref
 * Storage - ref
 */
public class ArrayᐸInt64ᐳ
        extends nLongBasedArray<ArrayᐸInt64ᐳ> {

    public ArrayᐸInt64ᐳ(Ctx ctx, TypeConstant type) {
        super(ctx, type);
    }

    // ----- Array API -----------------------------------------------------------------------------

    /**
     * @see {@link Array#$new$p}
     */
    public static ArrayᐸInt64ᐳ $new$p(Ctx ctx, TypeConstant type, long capacity, boolean _capacity) {
        assert !type.isImmutable();

        ctx.alloc(64); // REVIEW how big?
        ArrayᐸInt64ᐳ array = new ArrayᐸInt64ᐳ(ctx, type);
        array.$mut($MUTABLE);
        array.$capCfg(ctx, capacity);
        return array;
    }

    /**
     * @see {@link Array#$new$1$p}
     */
    public static ArrayᐸInt64ᐳ $new$1$p(Ctx ctx, TypeConstant type, long size, Object supply) {
        if (supply instanceof Int64 boxed) {
            ctx.alloc(size * 8); // REVIEW + HEADER_SIZE?
            ArrayᐸInt64ᐳ array = new ArrayᐸInt64ᐳ(ctx, type);
            array.$mut($FIXED);

            long fill = boxed.$value;

            if (array.$growInPlace(ctx, size)) {
                Arrays.fill(array.$storage, fill);
                array.$size((int) size);
                return array;
            } else {
                throw array.$oob(ctx, size);
            }
        }

        if (supply instanceof nFunction fn) {
            ctx.alloc(size * 8); // REVIEW + HEADER_SIZE?
            ArrayᐸInt64ᐳ array = new ArrayᐸInt64ᐳ(ctx, type);
            array.$mut($FIXED);

            if (!array.$growInPlace(ctx, size)) {
                throw array.$oob(ctx, size);
            }

            try {
                if (fn.optMethod == null) {
                    for (int i = 0; i < size; i++) {
                        array.$storage[i] = ((Int64) fn.stdMethod.invoke(ctx, Int64.$box(i))).$value;
                    }
                } else {
                    for (int i = 0; i < size; i++) {
                        array.$storage[i] = (long) fn.optMethod.invoke(ctx, (long) i);
                    }
                }
            } catch (nException e) {
                throw e;
            } catch (Throwable e) {
                // method handles expose signature mismatches as Throwable; convert to an XTC exception
                throw Exception.$typeMismatch(ctx, e.getMessage());
            }

            array.$size((int) size);
            return array;
        }

        throw new UnsupportedOperationException();
    }

    /**
     * @see {@link Array#$new$2}
     */
    public static ArrayᐸInt64ᐳ $new$2$p(Ctx ctx, TypeConstant type, Mutability mutability, Iterable elements) {
        long size = elements.size$get$p(ctx);
        ctx.alloc(size * 8); // REVIEW + HEADER_SIZE?
        ArrayᐸInt64ᐳ array = new ArrayᐸInt64ᐳ(ctx, type);
        array.$mut($MUTABLE);
        array.addAll(ctx, elements);
        array.$mut((int) mutability.ordinal$get$p(ctx));
        return array;
    }

    /**
     * @see {@link Array#$new$3}
     */
    public static ArrayᐸInt64ᐳ $new$3$p(Ctx ctx, TypeConstant type, ArrayᐸInt64ᐳ that) {
        // TODO
        throw new UnsupportedOperationException();
    }

    public Int64 getElement(Ctx ctx, Int64 index) {
        return Int64.$box(getElement$pi(ctx, index.$value));
    }

    public long getElement$p(Ctx ctx, long index) {
        return getElement$pi(ctx, index);
    }

    public long getElement$pi(Ctx ctx, long index) {
        return $getElement$pi(ctx, index);
    }

    public void setElement(Ctx ctx, Int64 index, Object value) {
        setElement$pi(ctx, index.$value, ((Int64) value).$value);
    }

    public void setElement$p(Ctx ctx, long index, long value) {
        setElement$pi(ctx, index, value);
    }

    public IteratorᐸInt64ᐳ iterator(Ctx ctx) {
        return new nIterator(ctx);
    }

    @Override
    public ArrayᐸInt64ᐳ add(Ctx ctx, Object element) {
        return add$p(ctx, ((Int64) element).$value);
    }

    // this method must be here even though all is does is call super, otherwise the JIT
    // will not be able to find this method as it uses "invokevirtual" for the invocation
    @Override
    public ArrayᐸInt64ᐳ add$p(Ctx ctx, long value) {
        return super.add$p(ctx, value);
    }

    public ArrayᐸInt64ᐳ insert$p(Ctx ctx, long index, long value) {
        if (index < 0 || index > size$get$p(ctx)) {
            throw $oob(ctx, index);
        }
        $insert(ctx, index, 1);
        $storage[(int) index] = value;
        return this;
    }

    @Override
    public ArrayᐸInt64ᐳ delete$p(Ctx ctx, long index) {
        if (index < 0 || index >= size$get$p(ctx)) {
            throw $oob(ctx, index);
        }
        $delete(ctx, index, 1);
        return this;
    }

    @Override
    public ArrayᐸInt64ᐳ slice(Ctx ctx, nRangeᐸInt64ᐳ range) {
        return (ArrayᐸInt64ᐳ) super.slice(ctx, range);
    }

    // ----- Array internals -----------------------------------------------------------------------

    // ToDo GG/JK remove this (and sub classes) when toString() can be generated by the JIT
    @Override
    protected java.lang.String $elementToString(Ctx ctx, long index) {
        Int64 n = Int64.$box(getElement$pi(ctx, index));
        return n.toString(ctx).toString();
    }

    @Override
    protected long $storageCapacity() {
        return $storage.length;
    }

    @Override
    protected long $getElement(Ctx ctx, long index) {
        return $storage[(int) index];
    }

    @Override
    protected void $setElement(Ctx ctx, long index, long value) {
        $storage[(int) index] = value;
    }

    @Override
    protected long $cap2len(long cap) {
        return cap;
    }

    @Override
    protected long $calculateHash(Ctx ctx) {
        return $calculate64BitHash(ctx);
    }

    @Override
    protected void $deleteElements(long index, long count) {
        $delete64bit(index, count);
    }

    @Override
    protected void $insertElements(long index, long count) {
        $insert64bit(index, count);
    }

    // ---- Iterator implementation ----------------------------------------------------------------

    private class nIterator extends nBaseIterator implements IteratorᐸInt64ᐳ {
        public nIterator(Ctx ctx) {
            super(ctx);
        }

        @Override
        public nType Element$get(Ctx ctx) {
            return nType.$ensureType(ctx, ctx.container.typeSystem.pool().typeInt64());
        }
    }
}
