package org.xtclang.ecstasy.numbers;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;

import org.xtclang.ecstasy.Comparable;
import org.xtclang.ecstasy.Orderable;
import org.xtclang.ecstasy.Ordered;
import org.xtclang.ecstasy.OutOfBounds;
import org.xtclang.ecstasy.nType;

import org.xvm.javajit.Ctx;

/**
 * Native Dec128 wrapper.
 */
public class Dec128 extends DecimalFPNumber {
    /**
     * The most significant 64-bits of a decimal value for positive zero.
     */
    public static final long $POS_ZERO_MS64BITS = 0x2208000000000000L;
    /**
     * The most significant 64-bits of a decimal value for positive zero.
     */
    public static final long $NEG_ZERO_MS64BITS = 0xA208000000000000L;
    /**
     * Zero, in a BigInteger format.
     */
    private static final BigInteger $BIGINT_ZERO = BigInteger.ZERO;
    /**
     * One thousand, in a BigInteger format.
     */
    private static final BigInteger $BIGINT_THOUSAND = BigInteger.valueOf(1000);
    /**
     * One million million (10^18), in a BigInteger format.
     */
    private static final BigInteger $BIGINT_10_TO_18TH = new BigInteger("1000000000000000000");

    /**
     * The sign bit for the high 64 bits of a 128-bit IEEE 754 decimal.
     */
    private static final long $SIGN_BIT = 1L << 63;

    /**
     * The amount to shift the G3 bit in the high 64 bits of a 128-bit IEEE 754 decimal.
     */
    private static final int $G3_SHIFT = 59;

    /**
     * The bit mask for the G0-G3 bits of the high 64 bits of a 128-bit IEEE 754 decimal.
     */
    private static final long $G0_G3_MASK = 0b1111L << $G3_SHIFT;

    /**
     * The amount to shift the G4 bit in the high 64 bits of a 128-bit IEEE 754 decimal
     */
    private static final int $G4_SHIFT = 58;

    /**
     * The value for the G0-G4 bits in the high 64 bits of a 128-bit IEEE 754 decimal that indicate
     * that the decimal value is infinite.
     */
    private static final long $G0_G4_INF = 0b11110L << $G4_SHIFT;

    /**
     * The value for the G0-G4 bits of the high 64 bits of a 128-bit IEEE 754 decimal that indicate
     * that the decimal value is "Not a Number" (NaN).
     */
    private static final long $G0_G4_NAN = 0b11111L << $G4_SHIFT;

    /**
     * The amount to shift the G5 bit in the high 64 bits of a 128-bit IEEE 754 decimal.
     */
    private static final int $G5_SHIFT = 57;

    /**
     * The value of the G5 bit in the high 64 bits of a 128-bit IEEE 754 decimal that indicates that
     * the decimal is a signaling NaN, if the decimal is a NaN.
     */
    private static final long $G5_SIGNAL = 1L << $G5_SHIFT;

    /**
     * The high 64 bits of the 128-bit decimal value for positive infinity.
     */
    public static final long $POS_INFINITY_HIGH = $G0_G4_INF;

    /**
     * The high 64 bits of the 128-bit decimal value for negative infinity.
     */
    public static final long $NEG_INFINITY_HIGH = $SIGN_BIT | $G0_G4_INF;

    /**
     * The decimal value for a "quiet" Not-A-Number (NaN).
     */
    public static final long $NaN_HIGH = $G0_G4_NAN;

    /**
     * The decimal value for a signaling Not-A-Number (NaN).
     */
    public static final long $SNaN_HIGH = $G0_G4_NAN | $G5_SIGNAL;

    /**
     * A 64-bit Java {@code long} containing the high 64 bits of an IEEE-754-2008 128-bit decimal
     */
    public final long $highBits;

    /**
     * A 64-bit Java {@code long} containing the low 64 bits of an IEEE-754-2008 128-bit decimal
     */
    public final long $lowBits;

    /**
     * Construct a decimal value from two Java {@code long} value whose format is that of an
     * IEEE-754-2008 128-bit decimal.
     *
     * @param lowBits   a 64-bit Java {@code long} containing the low 64 bits of an IEEE-754-2008
     *                  128-bit decimal
     * @param highBits  a 64-bit Java {@code long} containing the high 64 bits of an IEEE-754-2008
     *                  128-bit decimal
     */
    public Dec128(long lowBits, long highBits) {
        $highBits = highBits;
        $lowBits  = lowBits;
    }

    // ----- JIT methods ---------------------------------------------------------------------------

    /**
     * Obtain a Dec128 for two 64-bit "primitive" long values.
     *
     * @return a Dec128 reference
     */
    public static Dec128 $box(long lowBits, long highBits) {
        return new Dec128(lowBits, highBits);
    }

    /**
     * Determine whether two Dec128 primitives are equal.
     *
     * @param ctx    the context
     * @param low1   the low 64 bits of the first value
     * @param high1  the high 64 bits of the first value
     * @param low2   the low bits of the second value
     * @param high2  the high 64 bits of the second value
     *
     * @return {@code true} iff the two Dec128 primitives are equal
     */
    public static boolean equals$p(Ctx ctx, long low1, long high1, long low2, long high2) {
        return low1 == low2 && high1 == high2;
    }

    /**
     * Compare two Dec128 primitives.
     *
     * @param ctx    the context
     * @param low1   the low 64 bits of the first value
     * @param high1  the high 64 bits of the first value
     * @param low2   the low bits of the second value
     * @param high2  the high 64 bits of the second value
     *
     * @return a negative integer if the first Dec128 is lower than the second, zero if both Dec128
     *         values are equal, or a positive integer if the first Dec128 is greater than the
     *         second.
     */
    public static int compare$p(Ctx ctx, long low1, long high1, long low2, long high2) {
        return new Dec128(low1, high1).$compareForObjectOrder(new Dec128(low2, high2));
    }

    // ----- Op methods ----------------------------------------------------------------------------

    /**
     * Add two Dec128 values, each represented by an IEEE-754-2008 64-bit decimal packed into two
     * {@code long} values.
     * <p>
     * The low 64-bits of the result will be returned, the high 64-bits of the result will be set
     * into the {@link Ctx#i0} field of the passed in context.
     *
     * @param ctx    the current {@link Ctx}
     * @param low1   the low 64-bits of the target IEEE-754-2008 128-bit decimal value
     * @param high1  the high 64-bits of the target IEEE-754-2008 128-bit decimal value
     * @param low2   the low 64-bits of the IEEE-754-2008 128-bit decimal to add to the target value
     * @param high2  the high 64-bits of the IEEE-754-2008 128-bit decimal to add to the target value
     *
     * @return the low 64-bits of the resulting IEEE-754-2008 64-bit decimal packed into a
     *         {@code long} value
     */
    public static long $add(Ctx ctx, long low1, long high1, long low2, long high2) {
        int     leftSevenBits1 = $leftmost7Bits(low1, high1);
        int     leftSevenBits2 = $leftmost7Bits(low2, high2);
        boolean finite1        = $isFinite(leftSevenBits1);
        boolean finite2        = $isFinite(leftSevenBits2);
        if (finite1 && finite2) {
            return $toLongBits(ctx, $toBigDecimal(low1, high1).add($toBigDecimal(low2, high2)));
        }
        // if high1 is finite, so high2 is infinity or Nan, then return high2
        // else if high2 is finite, then high1 is infinity or Nan, so return high2
        // else both sides are infinity or Nan so, if high2 is Nan return Nan, else return high1
        ctx.i0 = finite1 ? high2
                : finite2 ? high1
                : $isNaN(leftSevenBits2) ? $NaN_HIGH : high1;
        return 0L;
    }

    /**
     * Divide two Dec128 values, each represented by an IEEE-754-2008 64-bit decimal packed into two
     * {@code long} values.
     * <p>
     * The low 64-bits of the result will be returned, the high 64-bits of the result will be set
     * into the {@link Ctx#i0} field of the passed in context.
     *
     * @param ctx    the current {@link Ctx}
     * @param low1   the low 64-bits of the target IEEE-754-2008 128-bit decimal dividend
     * @param high1  the high 64-bits of the target IEEE-754-2008 128-bit decimal dividend
     * @param low2   the low 64-bits of the IEEE-754-2008 128-bit decimal divisor
     * @param high2  the high 64-bits of the IEEE-754-2008 128-bit decimal divisor
     *
     * @return the low 64-bits of the resulting IEEE-754-2008 64-bit decimal packed into a
     *         {@code long} value
     */
    public static long $div(Ctx ctx, long low1, long high1, long low2, long high2) {
        int     leftSevenBits1 = $leftmost7Bits(high1);
        int     leftSevenBits2 = $leftmost7Bits(high2);
        boolean finite1        = $isFinite(leftSevenBits1);
        boolean finite2        = $isFinite(leftSevenBits2);

        if (finite1 && finite2) {
            if ($isZero(low2, high2)) {
                // ToDo this should be DivideByZero but that fails with a JVM verifier error
                throw new IllegalMath(ctx).$init(ctx, "divide by zero");
            }
            BigDecimal bd = $toBigDecimal(low1, high1);
            return $toLongBits(ctx, bd.divide($toBigDecimal(low2, high2), RoundingMode.DOWN));
        }

        if ($isNaN(leftSevenBits1) || $isNaN(leftSevenBits2)) {
            // either side is Nan, so return Nan
            ctx.i0 = $NaN_HIGH;
            return 0L;
        }

        boolean signed1 = $isSigned(leftSevenBits1);
        boolean signed2 = $isSigned(leftSevenBits2);
        if (signed1 != signed2) {
            // one side is positive, the other negative, either (-x * +inf == -inf)
            // or (+x * -inf == -inf)
            ctx.i0 = $NEG_INFINITY_HIGH;
            return 0L;
        } else {
            // both sides are positive, one is infinite (x * +inf == +inf),
            // or both sides are negative (-x * -inf == +inf)
            ctx.i0 = $POS_INFINITY_HIGH;
            return 0L;
        }
    }

    /**
     * Return the modulus of two Dec128 values, each represented by an IEEE-754-2008 64-bit decimal
     * packed into two {@code long} values.
     * <p>
     * The low 64-bits of the result will be returned, the high 64-bits of the result will be set
     * into the {@link Ctx#i0} field of the passed in context.
     *
     * @param ctx    the current {@link Ctx}
     * @param low1   the low 64-bits of the target IEEE-754-2008 128-bit decimal dividend
     * @param high1  the high 64-bits of the target IEEE-754-2008 128-bit decimal dividend
     * @param low2   the low 64-bits of the IEEE-754-2008 128-bit decimal divisor
     * @param high2  the high 64-bits of the IEEE-754-2008 128-bit decimal divisor
     *
     * @return the low 64-bits of the resulting IEEE-754-2008 64-bit decimal packed into a
     *         {@code long} value
     */
    public static long $mod(Ctx ctx, long low1, long high1, long low2, long high2) {
        int     leftSevenBits1 = $leftmost7Bits(high1);
        int     leftSevenBits2 = $leftmost7Bits(high2);
        boolean finite1        = $isFinite(leftSevenBits1);
        boolean finite2        = $isFinite(leftSevenBits2);

        if (finite1 && finite2) {
            BigDecimal target  = $toBigDecimal(low1, high1);
            BigDecimal divisor = $toBigDecimal(low2, high2);
            BigDecimal bigR = target.remainder(divisor, MathContext.DECIMAL128);
            return $toLongBits(ctx, bigR.signum() >= 0 ? bigR : bigR.add(divisor));
        }
        // either side is infinity or NaN so return NaN
        ctx.i0 = $NaN_HIGH;
        return 0L;
    }

    /**
     * Multiply two Dec128 values, each represented by an IEEE-754-2008 64-bit decimal packed into
     * two {@code long} values.
     * <p>
     * The low 64-bits of the result will be returned, the high 64-bits of the result will be set
     * into the {@link Ctx#i0} field of the passed in context.
     *
     * @param ctx    the current {@link Ctx}
     * @param low1   the low 64-bits of the target IEEE-754-2008 128-bit decimal value
     * @param high1  the high 64-bits of the target IEEE-754-2008 128-bit decimal value
     * @param low2   the low 64-bits of the IEEE-754-2008 128-bit decimal to multiply the target
     *               value by
     * @param high2  the high 64-bits of the IEEE-754-2008 128-bit decimal to multiply the target
     *               value by
     *
     * @return the low 64-bits of the resulting IEEE-754-2008 64-bit decimal packed into a
     *         {@code long} value
     */
    public static long $mul(Ctx ctx, long low1, long high1, long low2, long high2) {
        int     leftSevenBits1 = $leftmost7Bits(high1);
        int     leftSevenBits2 = $leftmost7Bits(high2);
        boolean finite1        = $isFinite(leftSevenBits1);
        boolean finite2        = $isFinite(leftSevenBits2);

        if (finite1 && finite2) {
            return $toLongBits(ctx, $toBigDecimal(low1, high1)
                        .multiply($toBigDecimal(low2, high2)));
        }
        if ($isNaN(leftSevenBits1) || $isNaN(leftSevenBits2)) {
            // either side is Nan, so return Nan
            ctx.i0 = $NaN_HIGH;
            return 0L;
        }
        // either side is infinity, if signs match return +Infinity else return -Infinity
        ctx.i0 = $isSigned(leftSevenBits1) == $isSigned(leftSevenBits2)
                    ? $POS_INFINITY_HIGH : $NEG_INFINITY_HIGH;
        return 0L;
    }

    /**
     * Negate a Dec128 value represented by an IEEE-754-2008 128-bit decimal packed into two
     * {@code long} values.
     * <p>
     * The low 64-bits of the result will be returned, the high 64-bits of the result will be set
     * into the {@link Ctx#i0} field of the passed in context.
     *
     * @param ctx   the current {@link Ctx}
     * @param low   the low 64-bits of the target IEEE-754-2008 128-bit decimal value
     * @param high  the high 64-bits of the target IEEE-754-2008 128-bit decimal value
     *
     * @return the low 64-bits of the resulting IEEE-754-2008 64-bit decimal packed into a
     *         {@code long}
     */
    public static long $neg(Ctx ctx, long low, long high) {
        int leftSevenBits = $leftmost7Bits(low, high);
        if ($isFinite(leftSevenBits)) {
            BigDecimal big = $toBigDecimal(low, high);
            return $toLongBits(ctx, big.negate());
        }
        if ($isNaN(leftSevenBits)) {
            ctx.i0 = high;
            return low;
        }
        ctx.i0 = $isSigned(leftSevenBits)? $POS_INFINITY_HIGH : $NEG_INFINITY_HIGH;
        return 0L;
    }

    /**
     * Subtract one Dec128 value from another, each represented by an IEEE-754-2008 64-bit decimal
     * packed into two {@code long} values.
     * <p>
     * The low 64-bits of the result will be returned, the high 64-bits of the result will be set
     * into the {@link Ctx#i0} field of the passed in context.
     *
     * @param ctx    the current {@link Ctx}
     * @param low1   the low 64-bits of the target IEEE-754-2008 128-bit decimal value
     * @param high1  the high 64-bits of the target IEEE-754-2008 128-bit decimal value
     * @param low2   the low 64-bits of the IEEE-754-2008 128-bit decimal to add to the target value
     * @param high2  the high 64-bits of the IEEE-754-2008 128-bit decimal to add to the target value
     *
     * @return the low 64-bits of the resulting IEEE-754-2008 64-bit decimal packed into a
     *         {@code long} value
     */
    public static long $sub(Ctx ctx, long low1, long high1, long low2, long high2) {
        int     leftSevenBits1 = $leftmost7Bits(high1);
        int     leftSevenBits2 = $leftmost7Bits(high2);
        boolean finite1        = $isFinite(leftSevenBits1);
        boolean finite2        = $isFinite(leftSevenBits2);
        if (finite1 && finite2) {
            return $toLongBits(ctx, $toBigDecimal(low1, high1)
                        .subtract($toBigDecimal(low2, high2)));
        }
        if ($isNaN(leftSevenBits1) || $isNaN(leftSevenBits2)) {
            // either side is NaN, so return NaN
            ctx.i0 = $NaN_HIGH;
            return 0L;
        }
        if (finite1) {
            // low1/high1 is finite, then low2/high2 is +/- infinity, so return the opposite
            // signed infinity
            ctx.i0 = $isSigned(leftSevenBits2) ? $POS_INFINITY_HIGH : $NEG_INFINITY_HIGH;
            return 0L;
        }
        if (finite2) {
            // low2/high2 is finite, then low1/high1 is +/- infinity so return low1/high1
            ctx.i0 = high1;
            return low1;
        }
        // both sides are +/- infinity, if both have the same sign return NaN,
        // else return low1/high1
        if ($isSigned(leftSevenBits1) == $isSigned(leftSevenBits2)) {
            ctx.i0 = $NaN_HIGH;
            return 0L;
        }
        ctx.i0 = high1;
        return low1;
    }

    // ----- conversion ----------------------------------------------------------------------------

    @Override
    public long toDec128$p(Ctx ctx) {
        ctx.i0 = $highBits;
        return $lowBits;
    }

    // ----- internal JIT support ------------------------------------------------------------------

    /**
     * The internal compare method for two Dec128 values called by the compare methods generated
     * in {@link org.xvm.javajit.builders.CommonBuilder#assembleCompareMethod}
     * and also in {@link TypeConstant#buildCompare}
     *
     * @param low1   the low 64 bits of the first value
     * @param high1  the high 64 bits of the first value
     * @param low2   the low bits of the second value
     * @param high2  the high 64 bits of the second value
     *
     * @return a negative integer if the first Dec128 is lower than the second, zero if both Dec128
     *         values are equal, or a positive integer if the first Dec128 is greater than the
     *         second.
     */
    public static int $compare(long low1, long high1, long low2, long high2) {
        return new Dec128(low1, high1).$compareForObjectOrder(new Dec128(low2, high2));
    }

    /**
     * The internal equals method for two Int128 values called by the equals methods generated
     * in {@link org.xvm.javajit.builders.CommonBuilder#assembleEqualsMethod} Method}
     * and also in {@link TypeConstant#buildCompare}
     *
     * @param low1   the low 64 bits of the first Int128
     * @param high1  the high 64 bits of the first Int128
     * @param low2   the low 64 bits of the second Int128
     * @param high2  the high 64 bits of the second Int128
     *
     * @return {@code true} if the two Int128 values are equal, {@code false} otherwise.
     */
    public static boolean $equals(long low1, long high1, long low2, long high2) {
        return high1 == high2 && low1 == low2;
    }

    // ----- accessors -----------------------------------------------------------------------------

    @Override
    public int $getByteLength() {
        return 16;
    }

    @Override
    public int $getByte(int i) {
        return $getByte(i, $lowBits, $highBits);
    }

    @Override
    protected int $leftmost7Bits() {
        return (int) ($highBits >>> 57);
    }

    @Override
    public boolean $isZero() {
        return $isZero($lowBits, $highBits);
    }

    /**
     * @return the significand of the decimal as a Java <tt>BigInteger</tt>
     */
    public BigInteger $getSignificand() {
        return $getSignificand($lowBits, $highBits);
    }

    /**
     * @return the significand of the decimal as a Java <tt>BigInteger</tt>
     */
    public static BigInteger $getSignificand(long lowBits, long highBits) {
        long nHBits = $ensureFiniteHighBits(highBits);
        long nLBits = lowBits;

        // get the first digit (most significant digit)
        int nToG4 = (int) (nHBits >>> $G4_SHIFT);
        int nD0   = (nToG4 & 0b011000) == 0b011000
                ? (nToG4 & 0b000001) + 8
                : (nToG4 & 0b000111);

        // keep only the T portion of the high bits (the low bits are all part of the T portion)
        nHBits &= $LS46BITS;

        // process the remainder of the T portion in the high bits (except for the last 6 bits that
        // overflowed from the low bits)
        long nHSig = nD0;
        if (nHSig != 0 || nHBits != 0) {
            for (int of = 36; of >= 0; of -= 10) {
                nHSig = nHSig * 1000 + $decletToInt((int) (nHBits >>> of));
            }
        }

        // process the T portion in the low bits (including the 6 LSBs of the high bits)
        long nLSig = 0;
        if (nHSig != 0 || nLBits != 0) {
            // grab the 6 bits from the 7th declet that overflowed to the "high bits" long, and
            // combine those with the highest 4 bits from the "low bits" long
            nHSig = nHSig * 1000 + $decletToInt((int) ((nHBits << 4) | (nLBits >>> 60)));

            for (int of = 50; of >= 0; of -= 10) {
                nLSig = nLSig * 1000 + $decletToInt((int) (nLBits >>> of));
            }
        }

        // put the digits from the low and high bits together to form the full significand
        BigInteger bintL = nLSig == 0 ? $BIGINT_ZERO : BigInteger.valueOf(nLSig);
        return nHSig == 0 ? bintL : BigInteger.valueOf(nHSig).multiply($BIGINT_10_TO_18TH).add(bintL);
    }

    /**
     * @return the exponent of the decimal as a Java <tt>int</tt>
     */
    public int $getExponent() {
        return $getExponent($highBits);
    }

    /**
     * Obtain the exponent of the decimal as a Java <tt>int</tt>
     *
     * @param highBits  the high 64-bits of a 128-bit IEEE-754-2008 decimal value
     *
     * @return the exponent of the decimal as a Java <tt>int</tt>
     */
    public static int $getExponent(long highBits) {
        // a combination field is 17 bits (from bit 46 to bit 62), including 12 "pure" exponent bits
        int nCombo = (int) ($ensureFiniteHighBits(highBits) >>> 46);
        int nExp   = (nCombo & 0b0_11000_000000000000) == 0b0_11000_000000000000
                ? (nCombo & 0b0_00110_000000000000) >>> 1
                : (nCombo & 0b0_11000_000000000000) >>> 3;

        // pull the rest of the exponent bits out of "pure" exponent section of the combo bits
        // section, and unbias the exponent
        return (nExp | nCombo & 0xFFF) - 6176;
    }

    // ----- conversions ---------------------------------------------------------------------------

    @Override
    public BigDecimal $toBigDecimal() {
        BigDecimal dec = $dec;
        if (dec == null && $isFinite()) {
            dec = new BigDecimal($getSignificand(), -$getExponent(), MathContext.DECIMAL128);
            $dec = dec = $isSigned() ? dec.negate() : dec;
        }
        return dec;
    }

    /**
     * Convert a 128-bit IEEE-754-2008 decimal value to a {@link BigDecimal}
     *
     * @param low   the low 64-bits of the IEEE-754-2008 decimal value
     * @param high  the high 64-bits of the IEEE-754-2008 decimal value
     *
     * @return a {@link BigDecimal} representation of the IEEE-754-2008 decimal value
     */
    public static BigDecimal $toBigDecimal(long low, long high) {
        int leftSevenBits = $leftmost7Bits(low, high);
        if ($isFinite(leftSevenBits)) {
            BigDecimal dec = new BigDecimal($getSignificand(low, high), -$getExponent(high),
                                            MathContext.DECIMAL128);
            return $isSigned(leftSevenBits) ? dec.negate() : dec;
        }
        throw new IllegalArgumentException("Not a finite value");
    }

    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode() {
        return ((int) ($highBits >>> 32)) ^ (int) $highBits ^ ((int) ($lowBits >>> 32)) ^ (int) $lowBits;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Dec128 that &&
                this.$highBits == that.$highBits && this.$lowBits == that.$lowBits;
    }

    // ----- helpers -------------------------------------------------------------------------------

    /**
     * Test the passed high 64 bits of a 128-bit decimal to ensure that they are finite; if they are
     * not, throw an exception.
     *
     * @param nHBits  the high 64 bits of a 128-bit IEEE-754-2008 decimal value
     *
     * @return the high 64 bits of a finite 128-bit IEEE-754-2008 decimal value
     *
     * @throws NumberFormatException if the decimal is either a NaN or an Infinity value
     */
    private static long $ensureFiniteHighBits(long nHBits) {
        if ((nHBits & $G0_G3_MASK) == $G0_G3_MASK) {
            throw new NumberFormatException("Not a finite value");
        }
        return nHBits;
    }

    /**
     * Convert a Java BigDecimal to an IEEE 754 128-bit decimal contained in two Java {@code long}s.
     * <p>
     * This method will set the most significant 64-bits of the IEEE 754 128-bit decimal into the
     * {@link Ctx#i0} field of the passed in context. The least significant 64-bits of the
     * IEEE 754 128-bit decimal will be returned.
     *
     * @return the least significant 64-bits of the IEEE 754 128-bit decimal contained in a Java
     *         {@code long} value.
     */
    public static long $toLongBits(Ctx ctx, BigDecimal dec) {
        // get the sign
        boolean    fNeg;
        switch (dec.signum()) {
            case -1:
                fNeg = true;
                break;

            case 0:
                // this is cheating a little bit, but the value is zero, so just steal the bits from
                // the well-known zero value
                ctx.i0 = $POS_ZERO_MS64BITS;
                return 0L;

            case 1:
                fNeg = false;
                break;

            default:
                throw new IllegalStateException();
        }

        // get the raw significand
        BigInteger bint = dec.unscaledValue().abs();
        if (bint.bitLength() > 114) {
            // we have 113 bits for the significand, and the bitLength of a big integer includes an
            // extra bit for a sign, so we know if the big integer needs more than 114 bits, that
            // we can't translate that into a declet-based form that uses no more than 113 bits;
            // note that we could still overflow, but we'll detect that only when we're done making
            // all of the declets (because there should only be a single decimal digit value 0..9
            // left at that point)
            throw new OutOfBounds(ctx).$init(ctx, "significand is >34 digits: " + bint);
        }

        // get the biased exponent (the scale is basically a negative exponent)
        int nExp = 6176 - dec.scale();
        if (nExp < 0 || nExp >= 12288) {
            throw new OutOfBounds(ctx).$init(ctx,
                    "biased exponent is out of range [0,12288): " + nExp);
        }

        // now we're ready to produce the bits, starting with the 11 declets
        long nHBits = 0;
        long nLBits = 0;
        for (int i = 0; i < 11 && bint.signum() > 0; ++i) {
            BigInteger[] abintDivRem = bint.divideAndRemainder($BIGINT_THOUSAND);
            BigInteger   bintTriad   = abintDivRem[1];

            int nDeclet = $intToDeclet(bintTriad.intValue());
            if (i < 6) {
                nLBits |= ((long) nDeclet) << (i * 10);
            } else if (i == 6) {
                // split the declet across the high and low bits
                nLBits |= ((long) nDeclet) << 60;
                nHBits  = nDeclet >>> 4;            // rightmost 4 bits are in the "low bits" long
            } else {
                // declet 7 starts at bit 6 of the "high bits" long
                nHBits |= ((long) nDeclet) << ((i-7) * 10 + 6);
            }

            bint = abintDivRem[0];
        }

        // store the least significant 12 bits of the exponent into the combo field starting at G5
        nHBits |=  (nExp & 0xFFFL) << 46;

        // get remaining significand
        int nSigRem = bint.intValueExact();
        if (nSigRem > 9) {
            throw new OutOfBounds(ctx).$init(ctx,
                    "significand is >34 digits: " + dec.unscaledValue().abs());
        }

        // remaining significand of 8 or 9 is stored in G4 as 0 or 1, with remaining exponent stored
        // in G2-G3, and G0-G1 both set to 1; otherwise, remaining significand (3 bits) is stored in
        // G2-G4 with remaining exponent stored in G0-G1
        int nGBits = nSigRem >= 8                                   // G01234
                ? (0b11000 | (nSigRem & 0b00001) | ((nExp & 0b110000000_00000) >>> 11))
                : (          (nSigRem & 0b00111) | ((nExp & 0b110000000_00000) >>>  9));
        nHBits |= ((long) nGBits) << $G4_SHIFT;

        // store the sign bit
        if (fNeg) {
            nHBits |= $SIGN_BIT;
        }

        ctx.i0 = nHBits;
        return nLBits;
    }
}
