package org.xtclang.ecstasy.collections;

import java.util.Arrays;
import org.xtclang.ecstasy.Exception;
import org.xtclang.ecstasy.Range$Int64;
import org.xtclang.ecstasy.text.Char;
import org.xtclang.ecstasy.xObj;
import org.xtclang.ecstasy.xType;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.javajit.Ctx;

import static java.lang.Math.max;
import static java.lang.System.arraycopy;

/**
 * Array of Unicode characters, stored in an array of Java longs, either 8x per long (0x00..0xFF) or
 * 3x per long (0x00..0x10FFFF).
 *
 * Object header
 * xObj - 64 bits of flags
 * ---
 * Delegate - ref
 * Storage - ref
 *
 * Size - 48 bits
 * Mutability - 2 bits
 * Format (8 vs 21) - 1 bit
 *
 * Capacity - 48 bits (pre storage)
 * Hash - 64 bits (only if mutability==Constant, requires storage != null)
 */
public class xArray$Char
        extends Array {

    public xArray$Char(Ctx ctx, TypeConstant type) {
        super(ctx, type);
    }

    // REVIEW: to save space, we could combine the $storage and $delegate fields into a single field, e.g.
    //
    //  @Override protected Array$Char $delegate() {
    //      if ($storage instanceof Array$Char delegate) {
    //          return delegate;
    //      }
    //      return null;
    //  }
    public xArray$Char $delegate;
    public long[] $storage;
    public boolean $utf21;      // REVIEW: this could just use a bit in (steal a bit from) $sizeEtc

    // ----- xObj API ------------------------------------------------------------------------------

    @Override public xType $type() {
        return null; // TODO Type<Array<Char>>
    }

    // ----- Array API -----------------------------------------------------------------------------

    /**
     * Array Constructor: construct(Int capacity = 0)
     */
    public static xArray$Char $new$p(Ctx ctx, TypeConstant type, long capacity, boolean _capacity) {
        assert !type.isImmutable();

        ctx.alloc(64); // REVIEW how big?
        xArray$Char array = new xArray$Char(ctx, type);
        array.$mut($MUTABLE);
        array.$capCfg(ctx, capacity);
        return array;
    }

    public static xArray$Char $new$1$p(Ctx ctx, TypeConstant type, long size, xObj supply) {
        // TODO
        throw new UnsupportedOperationException();
    }

    public static xArray$Char $new$2$p(Ctx ctx, TypeConstant type, Mutability mutability, org.xtclang.ecstasy.Iterable elements) {
        // TODO
        throw new UnsupportedOperationException();
    }

    public static xArray$Char $new$3$p(Ctx ctx, TypeConstant type, xArray$Char that) {
        // TODO
        throw new UnsupportedOperationException();
    }

    @Override public long capacity$get$p(Ctx ctx) {
        return $delegate == null
                ? $storage == null ? $capCfg(ctx) : ((long) $storage.length * ($utf21 ? 3 : 8))
                : $delegate.capacity$get$p(ctx);
    }

    @Override public long size$get$p(Ctx ctx) {
        // for virtual calls coded against xArray$Char, this avoids the virtual field accessor
        return $delegate == null ? ($sizeEtc & $SIZE_MASK) : $delegate.size$get$p(ctx);
    }

    @Override public Char getElement$p(Ctx ctx, long index) {
        return Char.$box(getElement$pi(ctx, index));
    }

    /**
     * Optimized "primitive intrinsic" form of the getElement() method. The JIT will use this method
     * in lieu of getElement$p() whenever it has enough information to do so.
     */
    public int getElement$pi(Ctx ctx, long index) {
        if ($delegate != null) {
            return $delegate.getElement$pi(ctx, index);
        }

        if (index >= ($sizeEtc & $SIZE_MASK)) {
            throw $oob(ctx, index);
        }

        try {
            return $utf21
                    // frdc algorithm: https://arxiv.org/abs/1902.01961
                    ? (int) ($storage[(int) ((index *= 0x55555556L) >>> 32)] >>>
                            (21 * (2 - ((int) (((index & 0xFFFFFFFFL) * 3) >>> 32)))))
                    : (int) ($storage[(int) (index >>> 3)] >>> ((7 - ((int) (index & 0b111))) << 3) & 0xFF);
        } catch (ArrayIndexOutOfBoundsException e) {
            throw $oob(ctx, index);
        }
    }

    @Override public void setElement$p(Ctx ctx, long index, xObj value) {
        setElement$pi(ctx, index, ((Char) value).$value);
    }

    public void setElement$pi(Ctx ctx, long index, int value) {
        if ($delegate != null) {
            $delegate.setElement$pi(ctx, index, value);
            return;
        }

        int size = $sizeEtc & $SIZE_MASK;
        if (index >= size) {
            if (index == size && ($sizeEtc >>> $MUT_SHIFT) == $MUTABLE) {
                // index==size up to 2^30 is an append operation within this array's storage;
                // anything over that requires a transition to the "huge" model
                if (($storage == null || index >= $storage.length) && !$growInPlace(ctx, index+1)) {
                    // we just transitioned to the "huge model"
                    $delegate.setElement$pi(ctx, index, value);
                    return;
                }
            } else {
                throw $oob(ctx, index);
            }
        }

        try {
            if (!$utf21) {
                if (value < 0x100) {
                    int i = ((int) index) >>> 3;
                    int s = 8 - (((int) index) & 0x3);
                    $storage[i] = $storage[i] & ~(0xFFL << s) | ((value & 0xFFL) << s);
                    return;
                }
                // TODO convert from 8-bit format to 21-bit format
            }
            // 21-bit version of: $storage[(int) index] = value;
            throw Exception.$unsupported(ctx, null); // TODO
        } catch (ArrayIndexOutOfBoundsException e) {
            throw $oob(ctx, index);
        }
    }

    @Override
    public xArray$Char add(Ctx ctx, xObj element) {
        return add$p(ctx, ((Char) element).$value);
    }

    public xArray$Char add$p(Ctx ctx, int ch) {
        if ($delegate != null) {
            $delegate.add$p(ctx, ch);
            return this;
        }

        if (($sizeEtc >>> $MUT_SHIFT) != $MUTABLE) {
            // TODO: persistent
            throw $ro(ctx);
        }

        int size = $sizeEtc & $SIZE_MASK;
        if (($storage == null || $storage.length == size) && !$growInPlace(ctx, size+1)) {
            // we just transitioned to the "huge model"
            $delegate.add$p(ctx, ch);
            return this;
        }

        try {
            if (!$utf21) {
                if (ch < 0x100) {
                    int i = size >>> 3;
                    int s = (7 - (size & 0x3)) << 3;
                    $storage[i] = $storage[i] & ~(0xFFL << s) | ((ch & 0xFFL) << s);
                    $size(size + 1);
                    return this;
                }
                // TODO convert from 8-bit format to 21-bit format
            }
            // 21-bit version of: $storage[(int) index] = value;
            throw Exception.$unsupported(ctx, null); // TODO
        } catch (ArrayIndexOutOfBoundsException e) {
            throw $oob(ctx, size);
        }
    }

    @Override public xArray$Char slice$p(Ctx ctx, long n1, long n2) {
        // slice must be in-range
        // TODO check lower bound as well
        long upper = Range$Int64.$effectiveUpperBound(ctx, n1, n2);
        if (size$get$p(ctx) > upper) {
            throw $oob(ctx, upper);
        }

        // optimized empty slice
        if (Range$Int64.$empty(ctx, n1, n2)) {
            // return TODO empty Array$Object
        }

        // return TODO Array$Object$Slice
        throw $oob(ctx, upper);
    }

    // ----- Array internals -----------------------------------------------------------------------

    @Override protected xArray$Char $delegate() {
        return $delegate;
    }

    @Override protected Object $storage() {
        return $storage;
    }

    /**
     * Ensure the specified capacity.
     *
     * @param ctx     the runtime context
     * @param minCap  the minimum capacity requirement
     *
     * @return true iff this array has able to grow "in place" to support the capacity requirement;
     *         false indicates that the array transitioned to the "huge" model
     */
    @Override protected boolean $growInPlace(Ctx ctx, long minCap) {
        // the caller is responsible for passing valid args; this will not be checked at runtime
        assert minCap > 0 && minCap <= $SIZE_MASK+1 && ($storage == null || minCap > $storage.length);

        if ($storage == null) {
            long cap = max($capCfg(ctx), minCap);
            if (cap > $SIZE_MASK) {
                $growHuge(ctx, cap);
                return false;
            } else {
                $storage = new long[cap2len((int) cap)];
                return true;
            }
        }

        // calculate power of 2 growth
        long cap = max($MIN_CAP, Long.highestOneBit(-1 + minCap + minCap)); // TODO current cap, not requested cap
        if (cap > $SIZE_MASK) {
            $growHuge(ctx, cap);
            return false;
        }

        $storage = Arrays.copyOf($storage, cap2len((int) cap));
        return true;
    }

    /**
     * @param cap  the desired storage capacity
     *
     * @return the number of longs required
     */
    int cap2len(int cap) {
        return $utf21 ? ((int) cap + 2) / 3 : ((int) cap + 7) / 8;
    }

    @Override protected void $growHuge(Ctx ctx, long cap) {
        // TODO new Array$Object$Huge
        throw $oob(ctx, cap);
    }

    @Override protected void $shrinkToSize(Ctx ctx) {
        int size = $sizeEtc & $MUT_MASK;
        int min  = cap2len (size);
        if ($storage != null && $storage.length > min) {
             long[] newArray = new long[min];
             arraycopy($storage, 0, newArray, 0, min);
             $storage = newArray;
        }
    }

    @Override public void $insert(Ctx ctx, long index, long count) {
        if ($delegate != null) {
            $delegate.$insert(ctx, index, count);
            return;
        }

        // the caller is responsible for passing valid args; this will not be checked at runtime
        assert index >= 0 && index <= size$get$p(ctx) && count > 0;

        // only mutable arrays can insert
        if (($sizeEtc >>> $MUT_SHIFT) != $MUTABLE) {
            throw $ro(ctx);
        }

        // need to compare size and capacity to see if we can insert without running out of room
        int  size = $sizeEtc & $SIZE_MASK;
        long req  = size + count;
        if (($storage == null || req > $storage.length) && !$growInPlace(ctx, req)) {
            $delegate.$insert(ctx, index, count);
            return;
        }

        if (index < size) {
            // move elements [index..size)
            arraycopy($storage, (int) index, $storage, (int) (index+count), size-((int) index));
            throw Exception.$unsupported(ctx, null); // TODO
        }
    }

    @Override public void $delete(Ctx ctx, long index, long count) {
        if ($delegate != null) {
            $delegate.$delete(ctx, index, count);
            return;
        }

        // only mutable arrays can delete
        if (($sizeEtc >>> $MUT_SHIFT) != $MUTABLE) {
            throw $ro(ctx);
        }

        throw Exception.$unsupported(ctx, null); // TODO
    }
}
