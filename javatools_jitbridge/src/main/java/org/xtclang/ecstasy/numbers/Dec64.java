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
 * Native Dec64 wrapper.
 */
public class Dec64 extends DecimalFPNumber {
    /**
     * The sign bit for a 64-bit IEEE 754 decimal.
     */
    private static final long $SIGN_BIT = 1L << 63;

    /**
     * The amount to shift the G3 bit of a 64-bit IEEE 754 decimal.
     */
    private static final int $G3_SHIFT = 59;

    /**
     * The bit mask for the G0-G3 bits of a 64-bit IEEE 754 decimal.
     */
    private static final long $G0_G3_MASK = 0b1111L << $G3_SHIFT;

    /**
     * The amount to shift the G4 bit of a 64-bit IEEE 754 decimal.
     */
    private static final int $G4_SHIFT = 58;

    /**
     * The amount to shift the G5 bit of a 64-bit IEEE 754 decimal.
     */
    private static final int $G5_SHIFT = 57;

    /**
     * The value of the G5 bit that indicates that a 64-bit IEEE 754 decimal is a signaling NaN, if
     * the decimal is a NaN.
     */
    private static final long $G5_SIGNAL = 1L << $G5_SHIFT;

    /**
     * The value for the G0-G4 bits of a 64-bit IEEE 754 decimal that indicate that the decimal
     * value is infinite.
     */
    private static final long $G0_G4_INF = 0b11110L << $G4_SHIFT;

    /**
     * The decimal value for positive infinity.
     */
    public static final long $POS_INFINITY = $G0_G4_INF;

    /**
     * The decimal value for negative infinity.
     */
    public static final long $NEG_INFINITY = $SIGN_BIT | $G0_G4_INF;

    /**
     * The value for the G0-G4 bits of a 64-bit IEEE 754 decimal that indicate that the decimal
     * value is "Not a Number" (NaN).
     */
    private static final long $G0_G4_NAN = 0b11111L << $G4_SHIFT;

    /**
     * The decimal value for a "quiet" Not-A-Number (NaN).
     */
    public static final long $NaN = $G0_G4_NAN;

    /**
     * The decimal value for a signaling Not-A-Number (NaN).
     */
    public static final long $SNaN = $G0_G4_NAN | $G5_SIGNAL;

    /**
     * The value of this IEEE-754-2008 64-bit decimal contained in a {@code long}.
     */
    public final long $bits;

    /**
     * Construct a decimal value from a Java {@code long} whose format is that of an IEEE-754-2008
     * 64-bit decimal.
     *
     * @param nBits  a 64-bit Java {@code long} containing the bits of an IEEE-754-2008 decimal
     */
    public Dec64(long nBits) {
        $bits = nBits;
    }

    // ----- JIT methods ---------------------------------------------------------------------------

    public static Dec64 $box(long value) {
        return new Dec64(value);
    }

    /**
     * Determine whether two Dec64 primitives are equal.
     *
     * @param ctx    the context
     * @param bits1  the bits of the first value
     * @param bits2  the bits of the second value
     *
     * @return {@code true} iff the two Dec64 primitives are equal
     */
    public static boolean equals$p(Ctx ctx, long bits1, long bits2) {
        return bits1 == bits2;
    }

    /**
     * Compare two Dec64 primitives.
     *
     * @param ctx    the context
     * @param bits1  the bits of the first value
     * @param bits2  the bits of the second value
     *
     * @return a negative integer if the first Dec64 is lower than the second, zero if both Dec64
     *         values are equal, or a positive integer if the first Dec64 is greater than the
     *         second.
     */
    public static int compare$p(Ctx ctx, long bits1, long bits2) {
        return new Dec64(bits1).$compareForObjectOrder(new Dec64(bits2));
    }

    // ----- Op methods ----------------------------------------------------------------------------

    /**
     * Add two Dec64 values, each represented by a IEEE-754-2008 64-bit decimal packed into a
     * {@code long}.
     *
     * @param ctx    the current {@link Ctx}
     * @param bits1  the target IEEE-754-2008 64-bit decimal value
     * @param bits2  the IEEE-754-2008 64-bit decimal to add to the target value
     *
     * @return the result of the addition as a IEEE-754-2008 64-bit decimal packed into
     *         a {@code long}
     */
    public static long $add(Ctx ctx, long bits1, long bits2) {
        int     leftSevenBits1 = $leftmost7Bits(bits1);
        int     leftSevenBits2 = $leftmost7Bits(bits2);
        boolean finite1        = $isFinite(leftSevenBits1);
        boolean finite2        = $isFinite(leftSevenBits2);
        if (finite1 && finite2) {
            return $toLongBits(ctx, $toBigDecimal(bits1).add($toBigDecimal(bits2)));
        }
        // if bits1 is finite, bits2 is infinity or Nan, so return bits2
        // else if bits2 is finite, then bits1 is infinity or Nan, so return bits1
        // else both sides are infinity or Nan so, if both signs are equal, return bits1
        // (+/- infinity), else return NaN
        return finite1 ? bits2
                : finite2 ? bits1
                : $isSigned(leftSevenBits1) == $isSigned(leftSevenBits2) ? bits1 : $NaN;
    }

    /**
     * Divide two Dec64 values, each represented by an IEEE-754-2008 64-bit decimal packed into a
     * {@code long}.
     *
     * @param ctx    the current {@link Ctx}
     * @param bits1  the target IEEE-754-2008 64-bit decimal dividend
     * @param bits2  the IEEE-754-2008 64-bit decimal divisor
     *
     * @return the result of the division as an IEEE-754-2008 64-bit decimal packed into a
     *         {@code long}
     */
    public static long $div(Ctx ctx, long bits1, long bits2) {
        int     leftSevenBits1 = $leftmost7Bits(bits1);
        int     leftSevenBits2 = $leftmost7Bits(bits2);
        boolean finite1        = $isFinite(leftSevenBits1);
        boolean finite2        = $isFinite(leftSevenBits2);

        if (finite1 && finite2) {
            if ($isZero(bits2)) {
                // ToDo this should be DivisionByZero but that fails with a JVM verifier error
                throw new IllegalMath(ctx).$init(ctx, "divide by zero", null);
            }
            BigDecimal bd = $toBigDecimal(bits1);
            return $toLongBits(ctx, bd.divide($toBigDecimal(bits2), RoundingMode.DOWN));
        }

        if ($isNaN(leftSevenBits1) || $isNaN(leftSevenBits2)) {
            // either side is NaN, so return Nan
            return $NaN;
        }

        boolean signed1 = $isSigned(leftSevenBits1);
        boolean signed2 = $isSigned(leftSevenBits2);
        if (signed1 != signed2) {
            // one side is positive, the other negative, either (-x * +inf == -inf)
            // or (+x * -inf == -inf)
            return $NEG_INFINITY;
        } else {
            // both sides are positive, one is infinite (x * +inf == +inf),
            // or both sides are negative (-x * -inf == +inf)
            return $POS_INFINITY;
        }
    }

    /**
     * Get the modulus of two Dec64 values, each represented by an IEEE-754-2008 64-bit decimal
     * packed into a {@code long}.
     *
     * @param ctx    the current {@link Ctx}
     * @param bits1  the target IEEE-754-2008 64-bit decimal value
     * @param bits2  the IEEE-754-2008 64-bit decimal dividend
     *
     * @return the result of the operation as an IEEE-754-2008 64-bit decimal packed into a
     *         {@code long}
     */
    public static long $mod(Ctx ctx, long bits1, long bits2) {
        int     leftSevenBits1 = $leftmost7Bits(bits1);
        int     leftSevenBits2 = $leftmost7Bits(bits2);
        boolean finite1        = $isFinite(leftSevenBits1);
        boolean finite2        = $isFinite(leftSevenBits2);

        if (finite1 && finite2) {
            BigDecimal target  = $toBigDecimal(bits1);
            BigDecimal divisor = $toBigDecimal(bits2);
            BigDecimal bigR = target.remainder(divisor, MathContext.DECIMAL64);
            return $toLongBits(ctx, bigR.signum() >= 0 ? bigR : bigR.add(divisor));
        }
        // either side is infinity or NaN so return NaN
        return $NaN;
    }

    /**
     * Multiply two Dec64 values, each represented by an IEEE-754-2008 64-bit decimal packed into a
     * {@code long}.
     *
     * @param ctx    the current {@link Ctx}
     * @param bits1  the target IEEE-754-2008 64-bit decimal value
     * @param bits2  the IEEE-754-2008 64-bit decimal to multiply the target value by
     *
     * @return the result of the multiplication as an IEEE-754-2008 64-bit decimal packed into
     *         a {@code long}
     */
    public static long $mul(Ctx ctx, long bits1, long bits2) {
        int     leftSevenBits1 = $leftmost7Bits(bits1);
        int     leftSevenBits2 = $leftmost7Bits(bits2);
        boolean finite1        = $isFinite(leftSevenBits1);
        boolean finite2        = $isFinite(leftSevenBits2);

        if (finite1 && finite2) {
            return $toLongBits(ctx, $toBigDecimal(bits1).multiply($toBigDecimal(bits2)));
        }
        if ($isNaN(leftSevenBits1) || $isNaN(leftSevenBits2)) {
            // either side is Nan, so return Nan
            return $NaN;
        }
        // either side is infinity, if signs match return +Infinity else return -Infinity
        return $isSigned(leftSevenBits1) == $isSigned(leftSevenBits2)
                ? $POS_INFINITY : $NEG_INFINITY;
    }

    /**
     * Negate a Dec64 value represented by an IEEE-754-2008 64-bit decimal packed into an
     * {@code long}.
     *
     * @param ctx   the current {@link Ctx}
     * @param bits  the target IEEE-754-2008 64-bit decimal value
     *
     * @return the negated IEEE-754-2008 64-bit decimal packed into an {@code long}
     */
    public static long $neg(Ctx ctx, int bits) {
        int leftSevenBits = $leftmost7Bits(bits);
        return $isFinite(leftSevenBits)
                ? $toLongBits(ctx, $toBigDecimal(bits).negate())
                : $isNaN(leftSevenBits)
                        ? $NaN
                        : $isSigned(leftSevenBits) ? $POS_INFINITY : $NEG_INFINITY;
    }

    /**
     * Subtract one Dec64 value from another, each represented by a IEEE-754-2008 64-bit decimal
     * packed into an {@code int}.
     *
     * @param ctx    the current {@link Ctx}
     * @param bits1  the target IEEE-754-2008 64-bit decimal value
     * @param bits2  the IEEE-754-2008 64-bit decimal to subtract from the target value
     *
     * @return the result of the subtraction as a IEEE-754-2008 64-bit decimal packed into
     *         an {@code int}
     */
    public static long $sub(Ctx ctx, long bits1, long bits2) {
        int     leftSevenBits1 = $leftmost7Bits(bits1);
        int     leftSevenBits2 = $leftmost7Bits(bits2);
        boolean finite1        = $isFinite(leftSevenBits1);
        boolean finite2        = $isFinite(leftSevenBits2);
        if (finite1 && finite2) {
            return $toLongBits(ctx, $toBigDecimal(bits1).subtract($toBigDecimal(bits2)));
        }
        if ($isNaN(leftSevenBits1) || $isNaN(leftSevenBits2)) {
            // either side is NaN, so return NaN
            return $NaN;
        }
        if (finite1) {
            // if bits1 is finite, then bits2 is +/- infinity, so return the opposite signed infinity
            return $isSigned(leftSevenBits2) ? $POS_INFINITY : $NEG_INFINITY;
        }
        if (finite2) {
            // if bits2 is finite, then bits1 is +/- infinity so return bits1
            return bits1;
        }
        // else both sides are infinity, if both have the same sign return NaN, else return bits1
        return $isSigned(leftSevenBits1) == $isSigned(leftSevenBits2) ? $NaN : bits1;
    }

    // ----- conversion ----------------------------------------------------------------------------

    @Override
    public long toDec64$p(Ctx ctx) {
        return $bits;
    }

    // we must override the method here otherwise the JIT will add its own implementation
    @Override
    public long toDec128$p(Ctx ctx) {
        return super.toDec128$p(ctx);
    }

    // ----- Orderable interface -------------------------------------------------------------------

    /**
     * The primitive implementation of:
     * <pre>
     * static <CompileType extends Orderable> Ordered compare(CompileType value1, CompileType value2);
     * </pre>
     */
    public static Ordered compare(Ctx ctx, nType type, Orderable value1, Orderable value2) {
        Dec64 decThis = (Dec64) value1;
        Dec64 decThat = (Dec64) value2;
        int   order   = decThis.$compareForObjectOrder(decThat);
        return order < 0  ? Ordered.Lesser.$INSTANCE
                : order == 0 ? Ordered.Equal.$INSTANCE
                : Ordered.Greater.$INSTANCE;
    }

    /**
     * The primitive implementation of:
     * <pre>
     *  static <CompileType extends Orderable> Boolean equals(CompileType value1, CompileType value2);
     * </pre>
     */
    public static Boolean equals(Ctx ctx, nType type, Comparable value1, Comparable value2) {
        long l1 = ((Dec64) value1).$bits;
        long l2 = ((Dec64) value2).$bits;
        return l1 == l2 ? Boolean.TRUE : Boolean.FALSE;
    }

    // ----- accessors -----------------------------------------------------------------------------

    @Override
    public int $getByteLength() {
        return 8;
    }

    @Override
    public int $getByte(int i) {
        return $getByte(i, $bits);
    }

    @Override
    protected int $leftmost7Bits() {
        return (int) ($bits >>> 57);
    }

    @Override
    public boolean $isZero() {
        return $isZero($bits);
    }

    /**
     * @return the significand of the decimal as a Java {@code long}
     */
    public long $getSignificand() {
        long nBits = $ensureFiniteBits($bits);
        int  nToG4 = (int) (nBits >>> $G4_SHIFT);
        long nSig  = (nToG4 & 0b011000) == 0b011000
                ? (nToG4 & 0b000001) + 8
                : (nToG4 & 0b000111);

        // unpack the digits from most significant declet to least significant declet
        for (int cShift = 40; cShift >= 0; cShift -= 10) {
            nSig = nSig * 1000 + $decletToInt((int) (nBits >>> cShift));
        }
        return nSig;
    }

    /**
     * @return the exponent of the decimal as a Java <tt>int</tt>
     */
    public int $getExponent() {
        // combination field is 13 bits (from bit 50 to bit 62), including 8 "pure" exponent bits
        int nCombo = (int) ($ensureFiniteBits($bits) >>> 50);
        int nExp   = (nCombo & 0b01100000000000) == 0b01100000000000
                ? (nCombo & 0b00011000000000) >>> 1
                : (nCombo & 0b01100000000000) >>> 3;

        // pull the rest of the exponent bits out of "pure" exponent section of the combo bits
        // section, and unbias the exponent
        return (nExp | nCombo & 0xFF) - 398;
    }


    @Override
    public BigDecimal $toBigDecimal() {
        BigDecimal dec = $dec;
        if (dec == null && $isFinite()) {
            $dec = dec = $toBigDecimal($bits);
        }
        return dec;
    }

    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode() {
        return ((int) ($bits >>> 32)) ^ (int) $bits;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Dec64 that && this.$bits == that.$bits;
    }

    // ----- helpers -------------------------------------------------------------------------------

    /**
     * Test the passed bits to ensure that they are finite; if they are not, throw an exception.
     *
     * @param nBits  the 64-bit IEEE-754-2008 decimal value
     *
     * @return a finite 64-bit IEEE-754-2008 decimal value
     *
     * @throws NumberFormatException if the decimal is either a NaN or an Infinity value
     */
    public static long $ensureFiniteBits(long nBits) {
        if ((nBits & $G0_G3_MASK) == $G0_G3_MASK) {
            throw new NumberFormatException("Not a finite value");
        }
        return nBits;
    }

    /**
     * Convert a Java BigDecimal to an IEEE 754 64-bit decimal contained in a Java {@code long}.
     *
     * @param ctx  the current context
     * @param dec  a Java BigDecimal value
     *
     * @return a Java {@code long} that contains a 64-bit IEEE 754 decimal value
     */
    public static long $toLongBits(Ctx ctx, BigDecimal dec) {
        dec = dec.round(MathContext.DECIMAL64);

        // obtain the significand
        long nSig = dec.unscaledValue().longValueExact();
        if (nSig < -9999999999999999L || nSig > 9999999999999999L) {
            throw new OutOfBounds(ctx).$init(ctx, "significand is >16 digits: " + nSig);
        }

        // bias the exponent (the scale is basically a negative exponent)
        int nExp = 398 - dec.scale();
        if (nExp < 0 || nExp >= 768) {
            throw new OutOfBounds(ctx).$init(ctx,
                    "biased exponent is out of range [0,768): " + nExp);
        }

        long nBits = 0;
        if (nSig < 0) {
            nBits = $SIGN_BIT;
            nSig  = -nSig;
        }

        // store the least significant 8 bits of the exponent into the combo field starting at G5
        // store the least significant 15 decimal digits of the significand in 5 10-bit declets in T
        int nLeft  = (int) (nSig / 1_000_000_000L);
        int nRight = (int) (nSig % 1_000_000_000L);
        nBits |=   (((long) (nExp & 0xFF)                         ) << 50)
                | (((long) $intToDeclet(nLeft  /     1_000 % 1000)) << 40)
                | (((long) $intToDeclet(nLeft              % 1000)) << 30)
                | (((long) $intToDeclet(nRight / 1_000_000 % 1000)) << 20)
                | (((long) $intToDeclet(nRight /     1_000 % 1000)) << 10)
                | (((long) $intToDeclet(nRight             % 1000))      );

        // remaining significand of 8 or 9 is stored in G4 as 0 or 1, with remaining exponent stored
        // in G2-G3, and G0-G1 both set to 1; otherwise, remaining significand (3 bits) is stored in
        // G2-G4 with remaining exponent stored in G0-G1
        int nSigRem = nLeft / 1_000_000;
        int nGBits  = nSigRem >= 8                              // G01234
                ? (0b11000 | (nSigRem & 0b00001) | ((nExp & 0b11000_00000) >>> 7))
                : (          (nSigRem & 0b00111) | ((nExp & 0b11000_00000) >>> 5));

        return nBits | ((long) nGBits) << $G4_SHIFT;
    }

    /**
     * Convert the bits of an IEEE 754 decimal to a Java BigDecimal.
     *
     * @param nBits  a 64-bit value containing an IEEE 754 decimal
     *
     * @return a Java BigDecimal
     */
    public static BigDecimal $toBigDecimal(long nBits) {
        $ensureFiniteBits(nBits);

        // combination field is 13 bits (from bit 50 to bit 62), including 8 "pure" exponent bits
        int  nCombo = (int) (nBits >>> 50);
        int  nExp   = nCombo & 0xFF;
        long nSig;

        // test G0 and G1
        if ((nCombo & 0b0_11000_00000000) == 0b0_11000_00000000) {
            // when the most significant five bits of G are 110xx or 1110x, the leading significand
            // digit d0 is 8+G4, a value 8 or 9, and the leading biased exponent bits are 2*G2 + G3,
            // a value of 0, 1, or 2
            nExp |= ((nCombo & 0b0_00110_00000000) >>> 1);   // shift right 9, but then shift left 8
            nSig  = ((nCombo & 0b0_00001_00000000) >>> 8) + 8;
        } else {
            // when the most significant five bits of G are 0xxxx or 10xxx, the leading significand
            // digit d0 is 4*G2 + 2*G3 + G4, a value in the range 0 through 7, and the leading
            // biased exponent bits are 2*G0 + G1, a value 0, 1, or 2; consequently if T is 0 and
            // the most significant five bits of G are 00000, 01000, or 10000, then the value is 0:
            //      v = (−1) S * (+0)
            nExp |= (nCombo & 0b0_11000_00000000) >>> 3;    // shift right 11, but then shift left 8
            nSig  = (nCombo & 0b0_00111_00000000) >>> 8;
        }

        // unbias the exponent
        nExp -= 398;

        // unpack the digits from most significant declet to least significant declet
        nSig = (((((nSig * 1000 + $decletToInt((int) (nBits >>> 40)))
                * 1000 + $decletToInt((int) (nBits >>> 30)))
                * 1000 + $decletToInt((int) (nBits >>> 20)))
                * 1000 + $decletToInt((int) (nBits >>> 10)))
                * 1000 + $decletToInt((int) (nBits       )))
                * (((nBits & $SIGN_BIT) >> 63) | 1);            // apply sign

        return new BigDecimal(BigInteger.valueOf(nSig), -nExp, MathContext.DECIMAL64);
    }
}
