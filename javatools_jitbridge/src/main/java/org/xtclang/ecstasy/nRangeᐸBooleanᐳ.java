package org.xtclang.ecstasy;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.Ctx;

public class nRangeᐸBooleanᐳ
        extends nLongRange {

    private nRangeᐸBooleanᐳ(Ctx ctx, int first, int last, boolean firstExclusive, boolean lastExclusive) {
        super(ctx, first, last, firstExclusive, lastExclusive);
    }

    // ----- Range / Interval API ------------------------------------------------------------------

    public static nRangeᐸBooleanᐳ $new$p(Ctx ctx, TypeConstant type, int first, int last,
                                         boolean firstExclusive, boolean _firstExclusive,
                                         boolean lastExclusive, boolean _lastExclusive) {
        return new nRangeᐸBooleanᐳ(ctx, first, last,
                !_firstExclusive && firstExclusive, !_lastExclusive && lastExclusive);
    }

    int first$get$p(Ctx ctx) {
        return (int) ($descending ? $upperBound : $lowerBound);
    }

    /**
     * The ending bound of the range.
     */
    int last$get$p(Ctx ctx) {
        return (int) ($descending ? $lowerBound : $upperBound);
    }

    public int lowerBound$get$p(Ctx ctx) {
        return (int) $lowerBound;
    }

    public int upperBound$get$p(Ctx ctx) {
        return (int) $upperBound;
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
            return (int) $lowerBound + 1;
        }
        return (int) $lowerBound;
    }

    public int effectiveUpperBound$get$p(Ctx ctx) {
        if ($upperExclusive) {
            if ($upperBound == Integer.MIN_VALUE) {
                throw Exception.$oob(ctx, null);
            }
            return (int) $upperBound - 1;
        }
        return (int) $lowerBound;
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
        return (int) Math.max(0, $upperBound - $lowerBound + 1 - ($lowerExclusive ? 1 : 0) - ($upperExclusive ? 1 : 0));
    }

    public int getElement$p(Ctx ctx, int index) {
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
        return (int) result;
    }

    // ----- JIT Support ---------------------------------------------------------------------------

    @Override
    protected long $firstAsLong(Ctx ctx) {
        return first$get$p(ctx);
    }

    @Override
    protected long $lastAsLong(Ctx ctx) {
        return last$get$p(ctx);
    }
}
