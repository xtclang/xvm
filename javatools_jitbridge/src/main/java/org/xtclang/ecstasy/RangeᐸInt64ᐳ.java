package org.xtclang.ecstasy;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.Ctx;

public class RangeᐸInt64ᐳ
        extends nLongRange {

    private RangeᐸInt64ᐳ(Ctx ctx, long first, long last, boolean firstExclusive, boolean lastExclusive) {
        super(ctx, first, last, firstExclusive, lastExclusive);
    }

    // ----- Range / Interval API ------------------------------------------------------------------

    public static RangeᐸInt64ᐳ $new$p(Ctx ctx, boolean async, TypeConstant type, long first,
            long last,
                                        boolean firstExclusive, boolean _firstExclusive,
                                        boolean lastExclusive, boolean _lastExclusive) {
        return new RangeᐸInt64ᐳ(ctx, first, last, !_firstExclusive && firstExclusive,
                !_lastExclusive && lastExclusive);
    }

    public long first$get$p(Ctx ctx, boolean async) {
        return $descending ? $upperBound : $lowerBound;
    }

    /**
     * The ending bound of the range.
     */
    public long last$get$p(Ctx ctx, boolean async) {
        return $descending ? $lowerBound : $upperBound;
    }

    public long lowerBound$get$p(Ctx ctx, boolean async) {
        return $lowerBound;
    }

    public long upperBound$get$p(Ctx ctx, boolean async) {
        return $upperBound;
    }

    public long effectiveFirst$get$p(Ctx ctx, boolean async) {
        return $descending ? effectiveUpperBound$get$p(ctx,
                false) : effectiveLowerBound$get$p(ctx, false);
    }

    public long effectiveLast$get$p(Ctx ctx, boolean async) {
        return $descending ? effectiveLowerBound$get$p(ctx,
                false) : effectiveUpperBound$get$p(ctx, false);
    }

    public long effectiveLowerBound$get$p(Ctx ctx, boolean async) {
        if ($lowerExclusive) {
            if ($lowerBound == Long.MAX_VALUE) {
                throw Exception.$oob(ctx, null);
            }
            return $lowerBound + 1;
        }
        return $lowerBound;
    }

    public long effectiveUpperBound$get$p(Ctx ctx, boolean async) {
        if ($upperExclusive) {
            if ($upperBound == Long.MIN_VALUE) {
                throw Exception.$oob(ctx, null);
            }
            return $upperBound - 1;
        }
        return $upperBound;
    }

    public boolean empty$get$p(Ctx ctx, boolean async) {
        if ($upperBound - $lowerBound > 1) {
            return false;
        }

        if ($upperBound == $lowerBound) {
            return !$lowerExclusive & !$upperExclusive;
        }

        assert $upperBound == $lowerBound + 1;
        return !$lowerExclusive | !$upperExclusive;
    }

    public long size$get$p(Ctx ctx, boolean async) {
        return Math.max(0, $upperBound - $lowerBound + 1 - ($lowerExclusive ? 1 : 0) - ($upperExclusive ? 1 : 0));
    }

    public long getElement$p(Ctx ctx, boolean async, long index) {
        long result;
        if ($descending) {
            result = $upperBound - index - ($upperExclusive ? 1 : 0);
            if (result < effectiveLowerBound$get$p(ctx, false)) {
                throw Exception.$oob(ctx, null);
            }
        } else {
            result = $lowerBound + index + ($lowerExclusive ? 1 : 0);
            if (result > effectiveUpperBound$get$p(ctx, false)) {
                throw Exception.$oob(ctx, null);
            }
        }
        return result;
    }

    // ----- JIT Support ---------------------------------------------------------------------------

    @Override
    protected long $firstAsLong(Ctx ctx) {
        return first$get$p(ctx, false);
    }

    @Override
    protected long $lastAsLong(Ctx ctx) {
        return last$get$p(ctx, false);
    }
}
