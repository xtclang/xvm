package org.xtclang.ecstasy.numbers;

import org.xtclang.ecstasy.xConst;
import org.xtclang.ecstasy.Exception;

/**
 * Native Int64 wrapper.
 */
public class Int64 extends xConst {
    /**
     * Construct an Ecstasy Int64 object.
     *
     * @param value  the 64-bit signed integer value
     */
    public Int64(long value) {
        super(-1);
        $value = value;
    }

    public final long $value;

    private static final int     SMALL_CACHE_OFFSET = 512;  // number of cached negative values
    private static final int     SMALL_CACHE_SIZE   = 8192; // must be power of 2
    private static final Int64[] SMALL_CACHE        = new Int64[SMALL_CACHE_SIZE];

    public static final Int64 ZERO    = $box(0);
    public static final Int64 ONE     = $box(1);
    public static final Int64 NEG_ONE = $box(-1);
    public static final Int64 MIN     = $box(Long.MIN_VALUE);
    public static final Int64 MAX     = $box(Long.MAX_VALUE);

    /**
     * Obtain an xInt64 for a 64-bit "primitive" int (a Java "long" value).
     *
     * @param value  a 64-bit "primitive" int
     *
     * @return an xInt64 reference
     */
    public static Int64 $box(long value) {
        long key = value + SMALL_CACHE_OFFSET;
        if ((key & -SMALL_CACHE_SIZE) == 0) {
            Int64 ref = SMALL_CACHE[(int) key];
            if (ref == null) {
                SMALL_CACHE[(int) key] = ref = new Int64(value);
            }
            return ref;
        }
        // TODO consider using a cache on the context (to avoid CPU cache line collisions)
        return new Int64(value);
    }

    // ----- primitive helpers ---------------------------------------------------------------------

    public static long $next(long n) {
        if (n == Long.MAX_VALUE) {
            throw Exception.$oob("64-bit max value exceeded", null);
        }
        return n + 1;
    }

    public static long $prev(long n) {
        if (n == Long.MIN_VALUE) {
            throw Exception.$oob("64-bit min value exceeded", null);
        }
        return n - 1;
    }

    // ----- debugging support ---------------------------------------------------------------------

    @Override public java.lang.String toString() {
        return "Int64:" + $value;
    }
}
