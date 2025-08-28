package org.xtclang.ecstasy.collections;

import org.xvm.javajit.Ctx;

import org.xtclang.ecstasy.Exception;
import org.xtclang.ecstasy.xObj;

import org.xtclang.ecstasy.Range;
import org.xtclang.ecstasy.xType;

/**
 * An abstract base class for all Ecstasy `Array` types.
 *
 * constructors
 * no "delegate" field; delegate property should evaluate to "this"
 *
 * capacity (get and set)
 * getElement
 * setElement
 * elementAt
 * size
 * add
 * clear
 * reify
 *
 * - Array
 *    - xArray64 - supports Boolean and all the "small" (1..32) bit size numbers packed into longs,
 *                 64-bit values as longs, and 128-bit values as pairs of longs
 *                 - Boolean
 *                 - UInt1
 *                 - Int2
 *                 - UInt2
 *                 - Int4
 *                 - UInt4
 *                 - Float4e2
 *                 - Int8
 *                 - UInt8
 *                 - Float8e4
 *                 - Float8e5
 *                 - Int16
 *                 - UInt16
 *                 - Float16
 *                 - BFloat16
 *                 - Int32
 *                 - UInt32
 *                 - Float32
 *                 - Dec32
 *                 - Int64
 *                 - UInt64
 *                 - Float64
 *                 - Dec64
 *                 - Int128
 *                 - UInt128
 *                 - Float128
 *                 - Dec128
 *      - xArraySlice64
 *      - xArrayExt64
 *      - xArrayView64 - issues include:
 *                       - last long can be partially "empty" (and if this is from a slice, there
 *                          may be data there that we have to purposefully ignore)
 *                       - odd packed forms e.g. 21 bit can have space at the end of the long that
 *                         we must pretend doesn't exist
 *                         - could add a new helper that allows such an array to be viewed
 *                           (read-only)  as longs with that extra space in it
 */
public abstract class Array<Element extends xObj>
        extends xObj {

    public Array(Ctx ctx) {
        super(ctx);
    }

    // ----- fields --------------------------------------------------------------------------------

    /**
     * A 32-bit value containing a 30-bit array size and a 2-bit Mutability enum.
     */
    protected int sizeEtc;

    protected static final int CONSTANT   = 0;     // Mutability.Constant
    protected static final int PERSISTENT = 1;     // Mutability.Persistent
    protected static final int FIXED      = 2;     // Mutability.Fixed
    protected static final int MUTABLE    = 3;     // Mutability.Mutable

    protected static final int SIZE_MASK    = -1 >>> 2;
    protected static final int MUT_MASK     = ~SIZE_MASK;
    protected static final int MUT_SHIFT    = 30;
    protected static final int INPLACE_MASK = 0b10 << MUT_SHIFT;    // i.e. either Fixed or Mutable



/* TODO move this stuff to the "special" impls?
public static final long SIZE_MASK = 0x0000FFFFFFFFFFFFL;
public static final long ETC_MASK  = ~SIZE_MASK;
public static final int REVERSE  = 63;
public static final int SLICE    = 62;
public static final int OVERFLOW = 61;
public static final int UNOWNED  = 60;
/**
 * Index of the element following the last element in the underlying array.
private int end;
*/

    // ----- xObj API ------------------------------------------------------------------------------

    @Override public abstract xType $type();

    @Override public boolean $isImmut() {
        return $mut() == CONSTANT;
    }

    @Override public abstract void $makeImmut();

    // ----- xArray API ----------------------------------------------------------------------------

    /**
     * A delegate for handling all special situations and features.
     */
    protected abstract Array<Element> $especial();

    /**
     * @return one of {@link #MUTABLE}, {@link #FIXED}, {@link #PERSISTENT}, or {@link #CONSTANT}
     */
    public int $mut() {
        Array<Element> especial = $especial();
        return especial == null ? (sizeEtc >>> MUT_SHIFT) : especial.$mut();
    }

    /**
     * @param mutability one of {@link #MUTABLE}, {@link #FIXED}, {@link #PERSISTENT}, or
     *        {@link #CONSTANT}
     */
    public void $mut(int mutability) {
        // this impl does not include any logic from Array.x; it just stores the mutability value
        Array<Element> especial = $especial();
        if (especial == null) {
            sizeEtc = (sizeEtc & SIZE_MASK) | (mutability << MUT_SHIFT);
        } else {
            especial.$mut(mutability);
        }
    }

//    protected final boolean $inPlace() {
//        return getMutability() >= FIXED;
//    }

//    protected final boolean $fixed() {
//        return getMutability() == FIXED;
//    }

    /**
     * @return the current storage capacity of the array; this is the "delegate.capacity" value
     *         referred to by Array.x
     */
    public abstract long $cap();

    /**
     * @param cap  the desired storage capacity for the array; this is the "delegate.capacity" value
     *             referred to by Array.x
     */
    public abstract void $cap(long cap);

    /**
     * @return `true` iff the array contains no elements
     */
    public boolean $empty() {
        Array<Element> especial = $especial();
        return especial == null ? (sizeEtc & SIZE_MASK) == 0 : especial.$empty();
    }

    /**
     * @return the length of the string in characters
     */
    public long $size() {
        Array<Element> especial = $especial();
        return especial == null ? (sizeEtc & SIZE_MASK) : especial.$size();
    }

    /**
     * Obtain the element at the specified index.
     *
     * @param index  the element index
     *
     * @return the element value
     */
    public abstract Element $get(long index);

    /**
     * Store the specified element value at the specified index.
     *
     * @param ctx    the XVM context
     * @param index  the element index
     * @param value  the element value
     */
    public abstract void $set(Ctx ctx, long index, Element value);

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
     * @param ctx  the XVM context
     * @param n    a 64-bit primitive `Range<Int64>` representation
     *
     * @return the specified array slice
     */
    public Array<Element> $slice(Ctx ctx, long n) {
//        if ($empty(n)) {
//            return TODO
//        }
        return $slice(ctx, Range.$first(n), Range.$size(n), Range.$descending(n));
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
    public Array<Element> $slice(Ctx ctx, long n1, long n2) {
//        if ($empty(n)) {
//            return TODO
//        }
        return $slice(ctx, Range.$first(n1, n2), Range.$size(n1, n2), Range.$descending(n1, n2));
    }

    public abstract Array<Element> $slice(Ctx ctx, long offset, long size, boolean descending);

    // ----- slice support -------------------------------------------------------------------------

//    /**
//     * @param slice  indicates the slice of this array
//     *
//     * @return `true` iff the array contains no elements
//     */
//    public boolean $empty(long slice) {
//        return slice == 0 ? $empty() : $empty() || sliceEmpty(slice);
//    }
//
//    /**
//     * @param slice  indicates the slice of this array
//     *
//     * @return the length of the string in characters
//     */
//    public long $size(long slice) {
//        return slice == 0 ? $size() : min($size(), sliceSize(slice));
//    }
//
//    public abstract Element $get(long slice, long index);

    // ----- debugging support ---------------------------------------------------------------------

    java.lang.String $mutDesc() {
        return switch ($mut()) {
            case CONSTANT   -> "Constant";
            case PERSISTENT -> "Persistent";
            case FIXED      -> "Fixed";
            case MUTABLE    -> "Mutable";
            default         -> throw new IllegalStateException();
        };
    }
    /**
     * @param index an illegal index
     *
     * @return (never returns)
     *
     * @throws Exception
     */
    protected Exception $oob(long index) {
        if (index < 0) {
            throw Exception.$oob("negative index: " + index, null);
        }
        throw Exception.$oob("index: " + index + " (size=" + $size() + ")", null);
    }

    /**
     * @return (never returns)
     *
     * @throws Exception
     */
    protected Exception $ro() {
        throw Exception.$ro("array mutability=" + $mutDesc(), null);
    }

    @Override public java.lang.String toString() {
        // TODO keep it small, show relevant info like size, type, a few elements ...
        return "";
    }
}
