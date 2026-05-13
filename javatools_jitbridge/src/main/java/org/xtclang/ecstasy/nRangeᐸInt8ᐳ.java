package org.xtclang.ecstasy;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.Ctx;

public class nRangeᐸInt8ᐳ
        extends Range {

    private nRangeᐸInt8ᐳ(Ctx ctx, int first, int last, boolean firstExclusive, boolean lastExclusive) {
        super(ctx);

        if (first > last) {
            $lowerBound     = last;
            $lowerExclusive = lastExclusive;
            $upperBound     = first;
            $upperExclusive = firstExclusive;
            $descending     = true;
        } else {
            $lowerBound     = first;
            $lowerExclusive = firstExclusive;
            $upperBound     = last;
            $upperExclusive = lastExclusive;
            $descending     = false;
        }
    }

    public final int     $lowerBound;
    public final boolean $lowerExclusive;
    public final int     $upperBound;
    public final boolean $upperExclusive;
    public final boolean $descending;


    // ----- Range / Interval API ------------------------------------------------------------------

    public static nRangeᐸInt8ᐳ $new$p(Ctx ctx, TypeConstant type, int first, int last,
                                      boolean firstExclusive, boolean _firstExclusive,
                                      boolean lastExclusive, boolean _lastExclusive) {
        return new nRangeᐸInt8ᐳ(ctx, first, last,
                !_firstExclusive && firstExclusive, !_lastExclusive && lastExclusive);
    }

    int first$get$p(Ctx ctx) {
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
    int last$get$p(Ctx ctx) {
        return $descending ? $lowerBound : $upperBound;
    }

    /**
     * True iff the ending bound of the range is exclusive.
     */
    boolean lastExclusive$get$p(Ctx ctx) {
        return $descending ? $lowerExclusive : $upperExclusive;
    }

    public int lowerBound$get$p(Ctx ctx) {
        return $lowerBound;
    }

    public boolean lowerExclusive$get$p(Ctx ctx) {
        return $lowerExclusive;
    }

    public int upperBound$get$p(Ctx ctx) {
        return $upperBound;
    }

    public boolean upperExclusive$get$p(Ctx ctx) {
        return $upperExclusive;
    }

    public boolean descending$get$p(Ctx ctx) {
        return $descending;
    }

    public int effectiveFirst$get$p(Ctx ctx) {
        return $descending ? effectiveUpperBound$get$p(ctx) : effectiveLowerBound$get$p(ctx);
    }

    public int effectiveLast$get$p(Ctx ctx) {
        return $descending ? effectiveLowerBound$get$p(ctx) : effectiveUpperBound$get$p(ctx);
    }

    public int effectiveLowerBound$get$p(Ctx ctx) {
        if ($lowerExclusive) {
            if ($lowerBound == Integer.MAX_VALUE) {
                throw Exception.$oob(ctx, null);
            }
            return $lowerBound + 1;
        }
        return $lowerBound;
    }

    public int effectiveUpperBound$get$p(Ctx ctx) {
        if ($upperExclusive) {
            if ($upperBound == Integer.MIN_VALUE) {
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

    public int size$get$p(Ctx ctx) {
        return Math.max(0, $upperBound - $lowerBound + 1 - ($lowerExclusive ? 1 : 0) - ($upperExclusive ? 1 : 0));
    }

    public int getElement$p(Ctx ctx, int index) {
        int result;
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
}
