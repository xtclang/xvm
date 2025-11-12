package org.xtclang.ecstasy;

import org.xtclang.ecstasy.numbers.Int64;
import org.xvm.javajit.Ctx;

public class RangeᐸInt64ᐳ
    extends Range {

    public RangeᐸInt64ᐳ(Ctx ctx) {
        super(ctx);
    }

    public long    $lowerBound;
    public boolean $lowerExclusive;
    public long    $upperBound;
    public boolean $upperExclusive;
    public boolean $descending;

    // ----- Range / Interval API ------------------------------------------------------------------

    long first$get$p(Ctx ctx) {
        return $descending ? $upperBound : $lowerBound;
    }

    /**
     * True iff the starting bound of the range is exclusive.
     */
    boolean firstExclusive$get$p(Ctx ctx) {
        return $descending ? $upperExclusive : $lowerExclusive;
    }

    /**
     * The ending bound of the range.
     */
    long last$get$p(Ctx ctx) {
        return $descending ? $lowerBound : $upperBound;
    }

    /**
     * True iff the ending bound of the range is exclusive.
     */
    boolean lastExclusive$get$p(Ctx ctx) {
        return $descending ? $lowerExclusive : $upperExclusive;
    }

    public long lowerBound$get$p$get$p(Ctx ctx) {
        return $lowerBound;
    }

    public boolean lowerExclusive$get$p(Ctx ctx) {
        return $lowerExclusive;
    }

    public long upperBound$get$p(Ctx ctx) {
        return $upperBound;
    }

    public boolean upperExclusive$get$p(Ctx ctx) {
        return $upperExclusive;
    }

    public boolean descending$get$p(Ctx ctx) {
        return $descending;
    }

    public long effectiveFirst$get$p(Ctx ctx) {
        return $descending ? effectiveUpperBound$get$p(ctx) : effectiveLowerBound$get$p(ctx);
    }

    public long effectiveLast$get$p(Ctx ctx) {
        return $descending ? effectiveLowerBound$get$p(ctx) : effectiveUpperBound$get$p(ctx);
    }

    public long effectiveLowerBound$get$p(Ctx ctx) {
        if ($lowerExclusive) {
            if ($lowerBound == Long.MAX_VALUE) {
                throw Exception.$oob(ctx, null);
            }
            return $lowerBound + 1;
        }
        return $lowerBound;
    }

    public long effectiveUpperBound$get$p(Ctx ctx) {
        if ($upperExclusive) {
            if ($upperBound == Long.MIN_VALUE) {
                throw Exception.$oob(ctx, null);
            }
            return $upperBound - 1;
        }
        return $lowerBound;
    }

    public boolean empty$get$p(Ctx ctx) {
        if ($upperBound - $lowerBound > 1) {
            return false;
        }

        if ($upperBound == $lowerBound) {
            return !$lowerExclusive & !$upperExclusive;
        }

        assert $upperBound == $lowerBound + 1;
        return !$lowerExclusive | !$upperExclusive;
    }

    public long size$get$p(Ctx ctx) {
        return Math.max(0, $upperBound - $lowerBound + 1 - ($lowerExclusive ? 1 : 0) - ($upperExclusive ? 1 : 0));
    }

    public long getElement$p(Ctx ctx, long index) {
        long result;
        if ($descending) {
            result = $upperBound - index - ($upperExclusive ? 1 : 0);
            if (result < effectiveLowerBound$get$p(ctx)) {
                throw Exception.$oob(ctx, null);
            }
        } else {
            result = $lowerBound + index + ($lowerExclusive ? 1 : 0);
            if (result > effectiveUpperBound$get$p(ctx)) {
                throw Exception.$oob(ctx, null);
            }
        }
        return result;
    }

    // TODO what parts of this can be / should be implemented natively?

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

    public static long $effectiveLowerBound(Ctx ctx, long n) {
        return $lowerExclusive(n) ? Int64.$next(ctx, $lowerBound(n)) : $lowerBound(n);
    }

    public static long $upperBound(long n) {
        return n << 32 >> 33;
    }

    public static boolean $upperExclusive(long n) {
        return (n & UE64) != 0;
    }

    public static long $effectiveUpperBound(Ctx ctx, long n) {
        return $upperExclusive(n) ? Int64.$prev(ctx, $upperBound(n)) : $upperBound(n);
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

    public static long $effectiveFirst(Ctx ctx, long n) {
        return $descending(n) ? $effectiveUpperBound(ctx, n) : $effectiveLowerBound(ctx, n);
    }

    public static long $last(long n) {
        return $descending(n) ? $lowerBound(n) : $upperBound(n);
    }

    public static boolean $lastExclusive(long n) {
        return $descending (n) ? $lowerExclusive(n) : $upperExclusive(n);
    }

    public static long $effectiveLast(Ctx ctx, long n) {
        return $descending(n) ? $effectiveLowerBound(ctx, n) : $effectiveUpperBound(ctx, n);
    }

    public static boolean $empty(Ctx ctx, long n) {
        return $effectiveLowerBound(ctx, n) > $effectiveUpperBound(ctx, n);
    }

    public static long $size(Ctx ctx, long n) {
        long lo = $effectiveLowerBound(ctx, n);
        long hi = $effectiveUpperBound(ctx, n);
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
     *
     * @return `true` iff the `Range` value was encoded into two Java `long` values
     */
    public boolean $rangeTo128(Ctx ctx) {
        return $rangeTo128(ctx, first$get$p(ctx), last$get$p(ctx), firstExclusive$get$p(ctx), lastExclusive$get$p(ctx));
    }

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

    public static long $effectiveLowerBound(Ctx ctx, long n1, long n2) {
        return $lowerExclusive(n1, n2) ? Int64.$next(ctx, $lowerBound(n1, n2)) : $lowerBound(n1, n2);
    }

    public static long $upperBound(long n1, long n2) {
        return n2 >> 1;
    }

    public static boolean $upperExclusive(long n1, long n2) {
        return (n2 & E128) != 0;
    }

    public static long $effectiveUpperBound(Ctx ctx, long n1, long n2) {
        return $upperExclusive(n1, n2) ? Int64.$prev(ctx, $upperBound(n1, n2)) : $upperBound(n1, n2);
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

    public static long $effectiveFirst(Ctx ctx, long n1, long n2) {
        return $descending(n1, n2) ? $effectiveUpperBound(ctx, n1, n2) : $effectiveLowerBound(ctx, n1, n2);
    }

    public static long $last(long n1, long n2) {
        return $descending(n1, n2) ? $lowerBound(n1, n2) : $upperBound(n1, n2);
    }

    public static boolean $lastExclusive(long n1, long n2) {
        return $descending (n1, n2) ? $lowerExclusive(n1, n2) : $upperExclusive(n1, n2);
    }

    public static long $effectiveLast(Ctx ctx, long n1, long n2) {
        return $descending(n1, n2) ? $effectiveLowerBound(ctx, n1, n2) : $effectiveUpperBound(ctx, n1, n2);
    }

    public static boolean $empty(Ctx ctx, long n1, long n2) {
        return $effectiveLowerBound(ctx, n1, n2) > $effectiveUpperBound(ctx, n1, n2);
    }

    public static long $size(Ctx ctx, long n1, long n2) {
        long lo = $effectiveLowerBound(ctx, n1, n2);
        long hi = $effectiveUpperBound(ctx, n1, n2);
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
