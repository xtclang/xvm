package org.xtclang.ecstasy;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.Ctx;

public class nRangeᐸFloat64ᐳ
        extends nLongRange {

    private nRangeᐸFloat64ᐳ(Ctx ctx, double first, double last, boolean firstExclusive, boolean lastExclusive) {
        super(ctx, Double.doubleToRawLongBits(first), Double.doubleToRawLongBits(last), firstExclusive, lastExclusive);
    }


    // ----- Range / Interval API ------------------------------------------------------------------

    public static nRangeᐸFloat64ᐳ $new$p(Ctx ctx, TypeConstant type, double first, double last,
                                         boolean firstExclusive, boolean _firstExclusive,
                                         boolean lastExclusive, boolean _lastExclusive) {
        return new nRangeᐸFloat64ᐳ(ctx, first, last, !_firstExclusive && firstExclusive,
                !_lastExclusive && lastExclusive);
    }

    double first$get$p(Ctx ctx) {
        return Double.longBitsToDouble($descending ? $upperBound : $lowerBound);
    }

    /**
     * The ending bound of the range.
     */
    double last$get$p(Ctx ctx) {
        return Double.longBitsToDouble($descending ? $lowerBound : $upperBound);
    }

    public double lowerBound$get$p(Ctx ctx) {
        return Double.longBitsToDouble($lowerBound);
    }

    public double upperBound$get$p(Ctx ctx) {
        return Double.longBitsToDouble($upperBound);
    }

    // ----- JIT Support ---------------------------------------------------------------------------

    @Override
    protected long $firstAsLong(Ctx ctx) {
        return Double.doubleToRawLongBits(first$get$p(ctx));
    }

    @Override
    protected long $lastAsLong(Ctx ctx) {
        return Double.doubleToRawLongBits(last$get$p(ctx));
    }
}
