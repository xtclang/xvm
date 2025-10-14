package org.xtclang.ecstasy.collections;

import org.xvm.javajit.Ctx;

import org.xtclang.ecstasy.Exception;
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

    public Array(Ctx ctx) {
        super(ctx);
    }

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

    // ----- xObj API ------------------------------------------------------------------------------

    /**
     * Note: It's expected that a few subclasses will know their type implicitly, while most will
     *       need to add a field to hold the type.
     */
    @Override public abstract xType $type();

    /**
     * Note: Arrays are immutable iff their "mutability==Constant"
     */
    @Override public boolean $isImmut() {
        return $mut() == $CONSTANT;
    }

    // ----- xArray API ----------------------------------------------------------------------------

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
    protected abstract Array $delegate();

    /**
     * @return one of {@link #$MUTABLE}, {@link #$FIXED}, {@link #$PERSISTENT}, or {@link #$CONSTANT}
     */
    public int $mut() {
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
    public void $mut(int mutability) {
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
     * @return the current storage capacity of the array; this is the "delegate.capacity" value
     *         referred to by Array.x
     */
    public abstract long capacity$get$p(Ctx ctx);

    /**
     * @param cap the desired storage capacity for the array; this is the "delegate.capacity" value
     *            referred to by Array.x
     */
    public abstract void capacity$set$p(Ctx ctx, long cap);

    public long $capCfg() {
        long cap = $isImmut() ? 0 : ($hashEtc & $CAP_MASK);
        return cap == 0 ? $MIN_CAP : cap;
    }

    /**
     * @return `true` iff the array contains no elements
     */
    public boolean empty$p(Ctx ctx) {
        Array delegate = $delegate();
        return delegate == null ? ($sizeEtc & $SIZE_MASK) == 0 : delegate.empty$p(ctx);
    }

    /**
     * @return the length of the string in characters
     */
    public long size$p(Ctx ctx) {
        Array delegate = $delegate();
        return delegate == null ? ($sizeEtc & $SIZE_MASK) : delegate.size$p(ctx);
    }

    /**
     * Obtain the element at the specified index.
     *
     * @param index the element index
     *
     * @return the element value
     */
    public abstract xObj getElement$p(Ctx ctx, long index);

    /**
     * Store the specified element value at the specified index.
     *
     * @param index  the element index
     * @param value  the element value
     */
    public abstract void setElement$p(Ctx ctx, long index, xObj value);

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
     * Insert storage for the specified number of elements at the specified index, shifting all
     * subsequent elements up by `count` indexes. The inserted elements should be assumed to be
     * unassigned, and must be assigned by the caller.
     *
     * @param ctx    the XVM context
     * @param index  the element index
     * @param count  the number of elements to insert
     */
    public abstract void $insert(Ctx ctx, long index, long count);

    /**
     * Remove the element at the specified index, shifting all subsequent elements down by `count`
     * indexes.
     *
     * @param index  the element index
     * @param count  the number of elements to delete
     */
    public abstract void $delete(Ctx ctx, long index, long count);

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
    public abstract Array slice$p(Ctx ctx, long n1, long n2);

    // ----- debugging support ---------------------------------------------------------------------

    java.lang.String $mutDesc() {
        return switch ($mut()) {
            case $CONSTANT   -> "Constant";
            case $PERSISTENT -> "Persistent";
            case $FIXED      -> "Fixed";
            case $MUTABLE    -> "Mutable";
            default          -> throw new IllegalStateException();
        };
    }
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
        throw Exception.$oob(ctx, "index: " + index + " (size=" + size$p(ctx) + ")");
    }

    /**
     * @return (never returns)
     *
     * @throws Exception
     */
    protected xException $ro(Ctx ctx) {
        throw Exception.$ro(ctx, "array mutability=" + $mutDesc());
    }

    @Override public java.lang.String toString() {
        // TODO keep it small, show relevant info like size, type, a few elements ...
        return "";
    }
}
