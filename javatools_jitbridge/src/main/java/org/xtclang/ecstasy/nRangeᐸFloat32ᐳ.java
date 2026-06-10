package org.xtclang.ecstasy;

import org.xvm.asm.constants.TypeConstant;
import org.xvm.javajit.Ctx;

public class nRangeᐸFloat32ᐳ
        extends nLongRange {

    private nRangeᐸFloat32ᐳ(Ctx ctx, float first, float last, boolean firstExclusive, boolean lastExclusive) {
        super(ctx, Float.floatToRawIntBits(first), Float.floatToRawIntBits(last), firstExclusive, lastExclusive);
    }


    // ----- Range / Interval API ------------------------------------------------------------------

    public static nRangeᐸFloat32ᐳ $new$p(Ctx ctx, TypeConstant type, float first, float last,
                                         boolean firstExclusive, boolean _firstExclusive,
                                         boolean lastExclusive, boolean _lastExclusive) {
        return new nRangeᐸFloat32ᐳ(ctx, first, last, !_firstExclusive && firstExclusive,
                !_lastExclusive && lastExclusive);
    }

    float first$get$p(Ctx ctx) {
        return Float.intBitsToFloat((int) ($descending ? $upperBound : $lowerBound));
    }

    /**
     * The ending bound of the range.
     */
    float last$get$p(Ctx ctx) {
        return Float.intBitsToFloat((int) ($descending ? $lowerBound : $upperBound));
    }

    public float lowerBound$get$p(Ctx ctx) {
        return Float.intBitsToFloat((int) $lowerBound);
    }

    public float upperBound$get$p(Ctx ctx) {
        return Float.intBitsToFloat((int) $upperBound);
    }

    // ----- JIT Support ---------------------------------------------------------------------------

    @Override
    protected long $firstAsLong(Ctx ctx) {
        return Float.floatToRawIntBits(first$get$p(ctx));
    }

    @Override
    protected long $lastAsLong(Ctx ctx) {
        return Float.floatToRawIntBits(last$get$p(ctx));
    }
}
