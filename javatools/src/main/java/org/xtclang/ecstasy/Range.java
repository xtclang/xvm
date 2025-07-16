package org.xtclang.ecstasy;

import org.xvm.javajit.Ctx;

import org.xtclang.ecstasy.numbers.Int64;

/**
 * Naotive implementation for `ecstasy.Range`.
 *
 * Supports the primitive form of the `Range<Int>` type.
 */
public class Range<Element extends xObj> extends xConst {

    public Range(long containerId) {
        super(containerId);
    }

    @Override
    public xType $type() {
        return null;
    }

    // ----- primitive single-long encoding --------------------------------------------------------

    private static final long Max30 = 0x3FFFFFFF;
    private static final long Min30 = -Max30 - 1;
    private static final long Max31 = 0x7FFFFFFF;
    private static final long Min31 = -Max31 - 1;

    private static final long D64  = 1L << 33;      // descending bit
    private static final long LE64 = 1L << 34;      // lower exclusive bit
    private static final long UE64 = 1L;            // upper exclusive bit

    /**
     * Attempt to encode the specified range into a 64-bit Java `long`.
     *
     * @param ctx             the context in which to return the primitive `Range` as {@link Ctx#i0}
     * @param first           start of the range/interval
     * @param last            end of the range/interval
     * @param firstExclusive  true iff the start value is NOT included in the range/interval
     * @param lastExclusive   true iff the emd value is NOT included in the range/interval
     *
     * @return `true` iff the `Range` value was encoded into a single Java `long`
     */
    public static boolean $rangeTo64(Ctx ctx, long first, long last, boolean firstExclusive, boolean lastExclusive) {
        if (last < first) {
            if (last < Min30 || last > Max30 || first < Min31 || first > Max31) {
                return false;
            }
            ctx.i0 = ((last  & Max30) << 34) | (lastExclusive  ? LE64 : 0) | D64
                   | ((first & Max31) << 1 ) | (firstExclusive ? UE64 : 0);
        } else {
            if (first < Min30 || first > Max30 || last < Min31 || last > Max31) {
                return false;
            }
            ctx.i0 = ((first & Max30) << 34) | (firstExclusive ? LE64 : 0)
                   | ((last  & Max31) << 1 ) | (lastExclusive  ? UE64 : 0);
        }
        return true;
    }

    /**
     * Attempt to encode the specified range into a 64-bit Java `long`.
     *
     * @param ctx  the context in which to return the `n` value of the 64-bit primitive `Range` as
     *             {@link Ctx#i0}
     * @param n1   the first part of the 128-bit primitive `Range`
     * @param n2   the second part of the 128-bit primitive `Range`
     *
     * @return `true` iff the `Range` value was encoded into a single Java `long`
     */
    public static boolean $rangeTo64(Ctx ctx, long n1, long n2) {
        long lo = $lowerBound(n1, n2);
        long hi = $upperBound(n1, n2);
        if (lo < Min30 || lo > Max30 || hi < Min31 || hi > Max31) {
            return false;
        }
        ctx.i0 = ((lo & Max30) << 34) | ($lowerExclusive(n1, n2) ? LE64 : 0) | ($descending(n1, n2) ? D64 : 0)
               | ((hi & Max31) << 1 ) | ($upperExclusive(n1, n2) ? UE64 : 0);
        return true;
    }

    // TODO how to do the opposite, i.e. return an xObj reference to an Ecstasy Range+Sequence object from 1 long value

    public static long $lowerBound(long n) {
        return n >> 34;
    }

    public static boolean $lowerExclusive(long n) {
        return (n & LE64) != 0;
    }

    public static long $effectiveLowerBound(long n) {
        return $lowerExclusive(n) ? Int64.$next($lowerBound(n)) : $lowerBound(n);
    }

    public static long $upperBound(long n) {
        return n << 32 >> 33;
    }

    public static boolean $upperExclusive(long n) {
        return (n & UE64) != 0;
    }

    public static long $effectiveUpperBound(long n) {
        return $upperExclusive(n) ? Int64.$prev($upperBound(n)) : $upperBound(n);
    }

    public static boolean $descending(long n) {
        return (n & D64) != 0;
    }

    public static long $first(long n) {
        return $descending(n) ? $upperBound(n) : $lowerBound(n);
    }

    public static boolean $firstExclusive(long n) {
        return $descending (n) ? $upperExclusive(n) : $lowerExclusive(n);
    }

    public static long $effectiveFirst(long n) {
        return $descending(n) ? $effectiveUpperBound(n) : $effectiveLowerBound(n);
    }

    public static long $last(long n) {
        return $descending(n) ? $lowerBound(n) : $upperBound(n);
    }

    public static boolean $lastExclusive(long n) {
        return $descending (n) ? $lowerExclusive(n) : $upperExclusive(n);
    }

    public static long $effectiveLast(long n) {
        return $descending(n) ? $effectiveLowerBound(n) : $effectiveUpperBound(n);
    }

    public static boolean $empty(long n) {
        return $effectiveLowerBound(n) > $effectiveUpperBound(n);
    }

    public static long $size(long n) {
        long lo = $effectiveLowerBound(n);
        long hi = $effectiveUpperBound(n);
        if (hi < lo) {
            return 0;
        }
        long size = hi - lo + 1;
        return size >= 0 ? size : Long.MAX_VALUE;

    }

    public static long $asAscending(long n) {
        return n & ~D64;
    }

    public static long $asDescending(long n) {
        return n | D64;
    }

    public static long $reversed(long n) {
        return $descending(n) ? $asAscending(n) : $asDescending(n);
    }


    // ----- primitive two-long encoding -----------------------------------------------------------

    private static final long Max62 = 0x3FFFFFFFFFFFFFFFL;
    private static final long Min62 = -Max62 - 1;
    private static final long Max63 = 0x7FFFFFFFFFFFFFFFL;
    private static final long Min63 = -Max63 - 1;

    private static final long D128 = 2L;        // descending bit (stored only on lower)
    private static final long E128 = 1L;        // exclusive bit (on both lower and upper)

    /**
     * Attempt to encode the specified range into two 64-bit Java `long` values.
     *
     * @param ctx             the context in which to return the primitive `Range` as {@link Ctx#i0}
     *                        and {@link Ctx#i1}
     * @param first           start of the range/interval
     * @param last            end of the range/interval
     * @param firstExclusive  true iff the start value is NOT included in the range/interval
     * @param lastExclusive   true iff the emd value is NOT included in the range/interval
     *
     * @return `true` iff the `Range` value was encoded into two Java `long` values
     */
    public static boolean $rangeTo128(Ctx ctx, long first, long last, boolean firstExclusive, boolean lastExclusive) {
        if (last < first) {
            if (last < Min62 || last > Max62 || first < Min63 || first > Max63) {
                return false;
            }
            ctx.i0 = (last  << 2) | (lastExclusive  ? E128 : 0) | D128;
            ctx.i1 = (first << 1) | (firstExclusive ? E128 : 0);
        } else {
            if (first < Min62 || first > Max62 || last < Min63 || last > Max63) {
                return false;
            }
            ctx.i0 = (first << 2) | (firstExclusive ? E128 : 0);
            ctx.i1 = (last  << 1) | (lastExclusive  ? E128 : 0);
        }
        return true;
    }

    /**
     * Encode the specified range into two 64-bit Java `long` values.
     *
     * @param ctx  the context in which to return the `n2` value of the 128-bit primitive `Range` as
     *             {@link Ctx#i0}
     * @param n    the primitive 64-bit `Range`
     *
     * @return the `n1` value of the primitive 128-bit `Range`
     */
    public static long $rangeTo128(Ctx ctx, long n) {
        ctx.i0 = ($upperBound(n) << 1) | ($upperExclusive(n) ? E128 : 0);
        return ($lowerBound(n) << 1) | ($lowerExclusive(n) ? E128 : 0) | ($descending(n) ? D128 : 0);
    }

    // TODO how to do the opposite, i.e. return an xObj reference to an Ecstasy Range+Sequence object from 2 long values

    public static long $lowerBound(long n1, long n2) {
        return n1 >> 2;
    }

    public static boolean $lowerExclusive(long n1, long n2) {
        return (n1 & E128) != 0;
    }

    public static long $effectiveLowerBound(long n1, long n2) {
        return $lowerExclusive(n1, n2) ? Int64.$next($lowerBound(n1, n2)) : $lowerBound(n1, n2);
    }

    public static long $upperBound(long n1, long n2) {
        return n2 >> 1;
    }

    public static boolean $upperExclusive(long n1, long n2) {
        return (n2 & E128) != 0;
    }

    public static long $effectiveUpperBound(long n1, long n2) {
        return $upperExclusive(n1, n2) ? Int64.$prev($upperBound(n1, n2)) : $upperBound(n1, n2);
    }

    public static boolean $descending(long n1, long n2) {
        return (n1 & D128) != 0;
    }

    public static long $first(long n1, long n2) {
        return $descending(n1, n2) ? $upperBound(n1, n2) : $lowerBound(n1, n2);
    }

    public static boolean $firstExclusive(long n1, long n2) {
        return $descending(n1, n2) ? $upperExclusive(n1, n2) : $lowerExclusive(n1, n2);
    }

    public static long $effectiveFirst(long n1, long n2) {
        return $descending(n1, n2) ? $effectiveUpperBound(n1, n2) : $effectiveLowerBound(n1, n2);
    }

    public static long $last(long n1, long n2) {
        return $descending(n1, n2) ? $lowerBound(n1, n2) : $upperBound(n1, n2);
    }

    public static boolean $lastExclusive(long n1, long n2) {
        return $descending (n1, n2) ? $lowerExclusive(n1, n2) : $upperExclusive(n1, n2);
    }

    public static long $effectiveLast(long n1, long n2) {
        return $descending(n1, n2) ? $effectiveLowerBound(n1, n2) : $effectiveUpperBound(n1, n2);
    }

    public static boolean $empty(long n1, long n2) {
        return $effectiveLowerBound(n1, n2) > $effectiveUpperBound(n1, n2);
    }

    public static long $size(long n1, long n2) {
        long lo = $effectiveLowerBound(n1, n2);
        long hi = $effectiveUpperBound(n1, n2);
        if (hi < lo) {
            return 0;
        }
        long size = hi - lo + 1;
        return size >= 0 ? size : Long.MAX_VALUE;

    }

    /**
     * @return the first long value representing the change (the second value is unchanged)
     */
    public static long $asAscending(long n1, long n2) {
        return n1 & ~D128;
    }

    /**
     * @return the first long value representing the change (the second value is unchanged)
     */
    public static long $asDescending(long n1, long n2) {
        return n1 | D128;
    }

    /**
     * @return the first long value representing the change (the second value is unchanged)
     */
    public static long $reversed(long n1, long n2) {
        return $descending(n1, n2) ? $asAscending(n1, n2) : $asDescending(n1, n2);
    }
}
