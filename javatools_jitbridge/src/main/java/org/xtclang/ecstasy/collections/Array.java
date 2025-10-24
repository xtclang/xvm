package org.xtclang.ecstasy.collections;

import org.xtclang.ecstasy.Range;
import org.xtclang.ecstasy.reflect.Enumeration;
import org.xtclang.ecstasy.text.String;
import org.xtclang.ecstasy.xEnum;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.Container;
import org.xvm.javajit.Ctx;

import org.xtclang.ecstasy.Exception;
import org.xtclang.ecstasy.Iterable;
import org.xtclang.ecstasy.Range$Int64;
import org.xtclang.ecstasy.xException;
import org.xtclang.ecstasy.xObj;
import org.xtclang.ecstasy.xType;
import org.xtclang.ecstasy.reflect.Var;

/**
 * Abstract native base class implementation for all types of `ecstasy.collections.Array`. Actual
 * storage for elements is based on the element type, and huge and sliced arrays require a separate
 * implementation as well, so concrete subclasses will exist for each combination of these aspects.
 *
 * The expected "hot" calls, in order of importance; these are -- by far -- the calls to optimize:
 * * element get by index -- assume 85%+ of the overall array usage
 * * element set by index -- assume 10%
 * * element count (size and empty properties) -- assume 2%
 * * hash code and equals -- assume 1%
 * As a result, optimizing array element access and modification by index is pretty much the only
 * thing that matters from a performance perspective, and making sure that there are no virtual
 * calls involved in either of those two operations is the goal.
 */
public abstract class Array
        extends xObj {

    protected Array(Ctx ctx, TypeConstant type) {
        super(ctx);
    }

    // ----- constants --------------------------------------------------------------------------------

    protected static final int  $CONSTANT     = 0;     // Array.Mutability.Constant
    protected static final int  $PERSISTENT   = 1;     // Array.Mutability.Persistent
    protected static final int  $FIXED        = 2;     // Array.Mutability.Fixed
    protected static final int  $MUTABLE      = 3;     // Array.Mutability.Mutable

    protected static final int  $SIZE_MASK    = -1 >>> 2;
    protected static final int  $MUT_MASK     = ~$SIZE_MASK;
    protected static final int  $MUT_SHIFT    = 30;
    protected static final int  $INPLACE_MASK = 0b10 << $MUT_SHIFT;    // i.e. either Fixed or Mutable
    protected static final int  $MIN_CAP      = 8;
    protected static final long $CAP_MASK     = 0x0000FFFFFFFFFFFFL;

    // ----- fields --------------------------------------------------------------------------------

    /**
     * A 32-bit value containing a 30-bit Ecstasy element count (aka array size) and a 2-bit
     * Mutability enum.
     */
    protected int $sizeEtc;

    /**
     * A 64-bit value containing the cached hash code iff non-zero and mutability==Constant.
     * Otherwise, if storage is not allocated, the lower 48 bits are the configured capacity.
     */
    protected long $hashEtc;

    public static class Mutability extends xEnum {
        private Mutability(long ordinal, String name) {
            super(null);

            Container    container = Ctx.get().container;
            ConstantPool pool      = container.typeSystem.pool();

            $type    = (xType) (pool.ensureTerminalTypeConstant(pool.ensureEcstasyClassConstant(
                        "collections.Array.Mutability." + name))).ensureXType(container);
            $ordinal = ordinal;
            $name    = name;
        }

        public final xType  $type;
        public final long   $ordinal;
        public final String $name;

        public static Mutability Constant   = new Mutability(0, String.of(null, "Constant"));
        public static Mutability Persistent = new Mutability(1, String.of(null, "Persistent"));
        public static Mutability Fixed      = new Mutability(2, String.of(null, "Fixed"));
        public static Mutability Mutable    = new Mutability(3, String.of(null, "Mutable"));

        @Override public xType $type() {
            return $type;
        }

        public Enumeration enumeration$get(Ctx ctx) {
            return Mutability$Enumeration.$INSTANCE;
        }

        @Override
        public String name$get(Ctx ctx) {
            return $name;
        }

        @Override
        public long ordinal$get$p(Ctx ctx) {
            return $ordinal;
        }

        // ----- debugging support ---------------------------------------------------------------------

        @Override
        public java.lang.String toString() {
            return "Mutability." + $name.toString();
        }
    }

    public static class Mutability$Enumeration extends Enumeration {
        private Mutability$Enumeration() {
            ConstantPool pool = Ctx.get().container.typeSystem.pool();
            super(null, pool.ensureClassTypeConstant(pool.clzClass(), null, pool.ensureTerminalTypeConstant(
                    pool.ensureEcstasyClassConstant("collections.Array.Mutability"))));
        }

        public static final Mutability$Enumeration $INSTANCE = new Mutability$Enumeration();

        public static final String[] $names = new String[] {
            Mutability.Constant.$name,
            Mutability.Persistent.$name,
            Mutability.Fixed.$name,
            Mutability.Mutable.$name,
        };

        public static final Mutability[] $values = new Mutability[] {
            Mutability.Constant,
            Mutability.Persistent,
            Mutability.Fixed,
            Mutability.Mutable,
        };

        @Override
        public long count$get$p() {
            return 4;
        }

        @Override
        public Mutability[] values$get() {
            return $values;
        }
        @Override
        public String[] names$get() {
            return $names;
        }
    }

    // ----- xObj API ------------------------------------------------------------------------------

    /**
     * Note: It's expected that some subclasses (e.g. "Int[]") will know their type implicitly,
     *       while others will need to add a field to hold the type (e.g. "Point[]").
     */
    @Override public xType $type() {
        throw new UnsupportedOperationException("TODO");
    }

    /**
     * Note: Arrays are immutable iff their "mutability==Constant"
     */
    @Override public boolean $isImmut() {
        return $mut() == $CONSTANT;
    }

    // ----- Array API -----------------------------------------------------------------------------

    /**
     * @return the current storage capacity of the array; this is the "delegate.capacity" value
     *         referred to by Array.x
     */
    public long capacity$get$p(Ctx ctx) {
        // reasonably efficient implementation at the base class level, but likely to be overridden
        // for hot classes
        Array delegate = $delegate();
        if (delegate != null) {
            return delegate.capacity$get$p(ctx);
        }

        Object storage = $storage();
        return storage == null
                ? $capCfg(ctx)
                : $elementCapacity(java.lang.reflect.Array.getLength(storage));
    }

    /**
     * @param cap the desired storage capacity for the array; this is the "delegate.capacity" value
     *            referred to by Array.x
     */
    public void capacity$set$p(Ctx ctx, long cap) {
        if ($isImmut()) {
            throw $ro(ctx);
        }

        Array delegate = $delegate();
        if (delegate != null) {
            delegate.capacity$set$p(ctx, cap);
            return;
        }

        // validate new capacity
        if (cap < 0 || cap > $CAP_MASK) {
            throw $oob(ctx, cap);
        }

        // before allocating storage, the desired capacity is only a plan
        Object storage = $storage();
        if (storage == null) {
            $capCfg(ctx, cap);
            return;
        }

        // a class with no delegate but with element storage is only responsible for non-huge sizes;
        // delegate the handling of huge sizes
        if (cap > $SIZE_MASK) {
            $growHuge(ctx, cap);
            return;
        }

        int smallCap = (int) cap;
        int existCap = (int) capacity$get$p(ctx);
        if (smallCap > existCap) {
            $growInPlace(ctx, smallCap);
        } else if (smallCap < existCap && smallCap == size$get$p(ctx)) {
            $shrinkToSize(ctx);
        }
    }

    /**
     * @return `true` iff the array contains no elements
     */
    public boolean empty$get$p(Ctx ctx) {
        Array delegate = $delegate();
        return delegate == null ? size$get$p(ctx) == 0 : delegate.empty$get$p(ctx);
    }

    /**
     * @return the length of the string in characters
     */
    public long size$get$p(Ctx ctx) {
        Array delegate = $delegate();
        return delegate == null ? ($sizeEtc & $SIZE_MASK) : delegate.size$get$p(ctx);
    }

    /**
     * Obtain the element at the specified index.
     *
     * @param index the element index
     *
     * @return the element value
     */
    public xObj getElement$p(Ctx ctx, long index) {
        throw new UnsupportedOperationException("TODO");
    }

    /**
     * Store the specified element value at the specified index.
     *
     * If the Array is immutable, this method must throw an exception without mutating the array.
     *
     * @param index  the element index
     * @param value  the element value
     */
    public void setElement$p(Ctx ctx, long index, xObj value) {
        throw new UnsupportedOperationException("TODO");
    }

    /**
     * Obtain the Var for the specified index in the array.
     *
     * @param index  the element index
     *
     * @return a Var object representing the storage for the element in the array
     */
    public Var elementAt$p(Ctx ctx, long index) {
        // TODO need a Var<T> impl, possibly a child class of Array
        throw $oob(ctx, index);
    }

    /**
     * Add:
     *
     *   Array add(Element element)
     */
    public Array add(Ctx ctx, xObj element) {
        throw new UnsupportedOperationException("TODO");
    }

    /*
     *
     *   Array addAll(Iterable<Element> values)
     *   Array insert(Int index, Element value)
     *   Array insertAll(Int index, Iterable<Element> values)
     *   Array delete(Int index)
     */

    /**
     * Delete multi:
     *
     *   Array deleteAll(Interval<Int> indexes)
     */
    public Array deleteAll(Range indexes) {
        throw new UnsupportedOperationException("TODO");
    }

    /**
     * Clear the array:
     *
     *   Array clear()
     */
    public Array clear(Ctx ctx) {
        throw new UnsupportedOperationException("TODO");
    }

    /**
     * Reify the array, i.e. make sure it's not a view of a different mutable array:
     *
     *   Array! reify(Mutability? mutability = Null) {
     *
     * @param mutability
     *
     * @return a reified array
     */
    public Array reify(xObj mutability) {
        throw new UnsupportedOperationException("TODO");
    }

    /**
     * Obtain a slice of this array.
     *
     * @param ctx    the XVM context
     * @param range  the range of indexes to slice
     *
     * @return the specified array slice
     */
    public Array slice(Ctx ctx, Range$Int64 range) {
        if (range.$rangeTo128(ctx)) {
            return slice$p(ctx, ctx.i0, ctx.i1);
        } else {
            long lower = range.effectiveLowerBound$get$p(ctx);
            throw $oob(ctx, lower < 0 ? lower : range.effectiveUpperBound$get$p(ctx));
        }
    }

    /**
     * Obtain a slice of this array.
     *
     * @param ctx  the XVM context
     * @param n1   the first part of a 128-bit primitive `Range<Int64>` representation
     * @param n2   the second part of a 128-bit primitive `Range<Int64>` representation
     *
     * @return the specified array slice
     */
    public Array slice$p(Ctx ctx, long n1, long n2) {
        throw new UnsupportedOperationException("TODO");
    }

    // ----- Array internals -----------------------------------------------------------------------

    /**
     * A delegate for handling all special situations and features.
     *
     * Subclasses aren't intended to use this accessor. Instead, this accessor simply enables each
     * subclass to expose its delegate to this base class, and the base class doesn't implement any
     * of the "hot" methods using this, so the virtual cost is irrelevant. Subclasses implement the
     * "hot" methods using that field directly, so when the array type is known at JIT time, the
     * code can be generated not against this Array class, but rather against a specific subclass,
     * relying on the JVM's inline cache to avoid virtual calls for the common case, and accepting
     * the virtual call cost for delegate arrays (slices and/or huge arrays).
     */
    protected Array $delegate() {
        throw new UnsupportedOperationException("TODO");
    }

    /**
     * The storage for the contents of this array. It should never be the case that an array has
     * both a non-null "$delegate()" and a non-null "$storage()".
     */
    protected Object $storage() {
        throw new UnsupportedOperationException("TODO");
    }

    /**
     * @return the configured capacity of this array; this value only has meaning up to the point
     *         where storage is allocated
     */
    protected long $capCfg(Ctx ctx) {
        long cap = $isImmut() ? 0 : ($hashEtc & $CAP_MASK);
        return cap == 0 ? $MIN_CAP : cap;
    }

    /**
     * Configure the size that will be pre-allocated for the array when storage is first allocated.
     *
     * If the Array is immutable, then this method should never be called.
     *
     * @param cap the capacity value to use when storage is first allocated
     */
    protected void $capCfg(Ctx ctx, long cap) {
        // this internal method must only be called when no storage has been allocated
        assert $delegate() == null && $storage() == null && !$isImmut();
        $hashEtc = ($hashEtc & ~$CAP_MASK) | (cap & $CAP_MASK);
    }

    protected int $elementCapacity(int javaArrayLength) {
        // base class assumes 1:1
        return javaArrayLength;
    }

    protected int $javaArrayLength(int elementCapacity) {
        // base class assumes 1:1
        return elementCapacity;
    }

    protected abstract boolean $growInPlace(Ctx ctx, long minCap);

    protected abstract void $growHuge(Ctx ctx, long cap);

    protected abstract void $shrinkToSize(Ctx ctx);

    /**
     * @return one of {@link #$MUTABLE}, {@link #$FIXED}, {@link #$PERSISTENT}, or {@link #$CONSTANT}
     */
    protected int $mut() {
        Array delegate = $delegate();
        return delegate == null ? ($sizeEtc >>> $MUT_SHIFT) : delegate.$mut();
    }

    /**
     * Store the mutability value for this Array. This implementation does not include any logic
     * from Array.x; it just stores the mutability value.
     *
     * @param mutability one of {@link #$MUTABLE}, {@link #$FIXED}, {@link #$PERSISTENT}, or
     *        {@link #$CONSTANT}
     */
    protected void $mut(int mutability) {
        Array delegate = $delegate();
        if (delegate == null) {
            $sizeEtc = ($sizeEtc & $SIZE_MASK) | (mutability << $MUT_SHIFT);
        } else {
            delegate.$mut(mutability);
        }
    }

    /**
     * @return true iff mutating array operations are in-place; false if the array is a "persistent"
     *         data structure, such that "mutating" operations result in a new array
     */
    protected final boolean $mutInPlace() {
        return $mut() >= $FIXED;
    }

    /**
     * @return true iff the array is "mutability==Fixed"
     */
    protected final boolean $mutFixed() {
        return $mut() == $FIXED;
    }

    /**
     * Insert storage for the specified number of elements at the specified index, shifting all
     * subsequent elements up by `count` indexes. The inserted elements should be assumed to be
     * unassigned, and must be assigned by the caller.
     *
     * If the Array is immutable, then this method should never be called.
     *
     * @param ctx    the XVM context
     * @param index  the element index
     * @param count  the number of elements to insert
     */
    protected abstract void $insert(Ctx ctx, long index, long count);

    /**
     * Remove the element at the specified index, shifting all subsequent elements down by `count`
     * indexes.
     *
     * If the Array is immutable, then this method should never be called.
     *
     * @param index  the element index
     * @param count  the number of elements to delete
     */
    protected abstract void $delete(Ctx ctx, long index, long count);

    // ----- exception helpers ---------------------------------------------------------------------

    /**
     * @param index an illegal index
     *
     * @return (never returns)
     *
     * @throws Exception
     */
    protected xException $oob(Ctx ctx, long index) {
        if (index < 0) {
            throw Exception.$oob(ctx, "negative index: " + index);
        }
        throw Exception.$oob(ctx, "index: " + index + " (size=" + size$get$p(ctx) + ")");
    }

    /**
     * @return (never returns)
     *
     * @throws Exception
     */
    protected xException $ro(Ctx ctx) {
        throw Exception.$ro(ctx, "array mutability=" + $mutDesc());
    }

    // ----- debugging support ---------------------------------------------------------------------

    public java.lang.String $mutDesc() {
        return switch ($mut()) {
            case $CONSTANT   -> "Constant";
            case $PERSISTENT -> "Persistent";
            case $FIXED      -> "Fixed";
            case $MUTABLE    -> "Mutable";
            default          -> throw new IllegalStateException();
        };
    }

    @Override public java.lang.String toString() {
        // TODO keep it small, show relevant info like size, type, a few elements ...
        return "";
    }
}
