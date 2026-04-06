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
 * Native Dec32 wrapper.
 */
public class Dec32 extends DecimalFPNumber {
    /**
     * The sign bit for a 32-bit IEEE 754 decimal.
     */
    private static final int $SIGN_BIT = 0x80000000;

    /**
     * The amount to shift the G3 bit of a 32-bit IEEE 754 decimal.
     */
    private static final int $G3_SHIFT = 27;

    /**
     * The bit mask for the G0-G3 bits of a 32-bit IEEE 754 decimal.
     */
    private static final int $G0_G3_MASK = 0b1111 << $G3_SHIFT;

    /**
     * The amount to shift the G4 bit of a 32-bit IEEE 754 decimal.
     */
    private static final int $G4_SHIFT = 26;

    /**
     * The value for the G0-G4 bits of a 32-bit IEEE 754 decimal that indicate that the decimal
     * value is infinite.
     */
    private static final int $G0_G4_INF = 0b11110 << $G4_SHIFT;

    /**
     * The amount to shift the G5 bit of a 32-bit IEEE 754 decimal.
     */
    private static final int $G5_SHIFT = 25;

    /**
     * The value of the G5 bit that indicates that a 32-bit IEEE 754 decimal is a signaling NaN if
     * the decimal is a NaN.
     */
    private static final int $G5_SIGNAL = 1 << $G5_SHIFT;

    /**
     * The decimal value for positive infinity.
     */
    public static final int $POS_INFINITY = $G0_G4_INF;

    /**
     * The decimal value for negative infinity.
     */
    public static final int $NEG_INFINITY = $SIGN_BIT | $G0_G4_INF;

    /**
     * The value for the G0-G4 bits of a 32-bit IEEE 754 decimal that indicate that the decimal
     * value is "Not a Number" (NaN).
     */
    private static final int $G0_G4_NAN = 0b11111 << $G4_SHIFT;

    /**
     * The decimal value for a "quiet" Not-A-Number (NaN).
     */
    public static final int $NaN = $G0_G4_NAN;

    /**
     * The decimal value for a signaling Not-A-Number (NaN).
     */
    public static final int $SNaN = $G0_G4_NAN | $G5_SIGNAL;

    /**
     * The value of this IEEE-754-2008 32-bit decimal contained in an {@code int}.
     */
    public final int $bits;

    /**
     * Construct a decimal value from a Java <tt>int</tt> whose format is that of an IEEE-754-2008
     * 32-bit decimal.
     *
     * @param bits  a 32-bit Java <tt>int</tt> containing the bits of an IEEE-754-2008 decimal
     */
    public Dec32(int bits) {
        $bits = bits;
    }

    public static Dec32 $box(int value) {
        return new Dec32(value);
    }

    /**
     * Determine whether two Dec32 primitives are equal.
     *
     * @param ctx    the context
     * @param bits1  the bits of the first value
     * @param bits2  the bits of the second value
     *
     * @return {@code true} iff the two Dec32 primitives are equal
     */
    public static boolean equals$p(Ctx ctx, int bits1, int bits2) {
        return bits1 == bits2;
    }

    /**
     * Compare two Dec32 primitives.
     *
     * @param ctx    the current {@link Ctx}
     * @param bits1  the bits of the first value
     * @param bits2  the bits of the second value
     *
     * @return a negative integer if the first Dec32 is lower than the second, zero if both Dec32
     *         values are equal, or a positive integer if the first Dec32 is greater than the
     *         second.
     */
    public static int compare$p(Ctx ctx, int bits1, int bits2) {
        return new Dec32(bits1).$compareForObjectOrder(new Dec32(bits2));
    }

    // ----- Op methods ----------------------------------------------------------------------------

    /**
     * Add two Dec32 values, each represented by an IEEE-754-2008 32-bit decimal packed into an
     * {@code int}.
     *
     * @param ctx    the current {@link Ctx}
     * @param bits1  the target IEEE-754-2008 32-bit decimal value
     * @param bits2  the IEEE-754-2008 32-bit decimal to add to the target value
     *
     * @return the result of the addition as an IEEE-754-2008 32-bit decimal packed into
     *         an {@code int}
     */
    public static int $add(Ctx ctx, int bits1, int bits2) {
        int     leftSevenBits1 = $leftmost7Bits(bits1);
        int     leftSevenBits2 = $leftmost7Bits(bits2);
        boolean finite1        = $isFinite(leftSevenBits1);
        boolean finite2        = $isFinite(leftSevenBits2);
        if (finite1 && finite2) {
            return $toIntBits(ctx, $toBigDecimal(bits1).add($toBigDecimal(bits2)));
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
     * Divide two Dec32 values, each represented by an IEEE-754-2008 32-bit decimal packed into an
     * {@code int}.
     *
     * @param ctx    the current {@link Ctx}
     * @param bits1  the target IEEE-754-2008 32-bit decimal value
     * @param bits2  the IEEE-754-2008 32-bit decimal to add to the target value
     *
     * @return the result of the multiplication as an IEEE-754-2008 32-bit decimal packed into
     *         an {@code int}
     */
    public static int $div(Ctx ctx, int bits1, int bits2) {
        int     leftSevenBits1 = $leftmost7Bits(bits1);
        int     leftSevenBits2 = $leftmost7Bits(bits2);
        boolean finite1        = $isFinite(leftSevenBits1);
        boolean finite2        = $isFinite(leftSevenBits2);

        if (finite1 && finite2) {
            if ($isZero(bits2)) {
                // ToDo this should be DivideByZero but that fails with a JVM verifier error
                throw new IllegalMath(ctx).$init(ctx, "divide by zero");
            }
            BigDecimal bd = $toBigDecimal(bits1);
            return $toIntBits(ctx, bd.divide($toBigDecimal(bits2), RoundingMode.DOWN));
        }

        if ($isNaN(leftSevenBits1) || $isNaN(leftSevenBits2)) {
            // either side is Nan, so return Nan
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
     * Get the modulus of two Dec32 values, each represented by an IEEE-754-2008 32-bit decimal
     * packed into an {@code int}.
     *
     * @param ctx    the current {@link Ctx}
     * @param bits1  the target IEEE-754-2008 32-bit decimal value
     * @param bits2  the IEEE-754-2008 32-bit decimal dividend
     *
     * @return the result of the operation as an IEEE-754-2008 32-bit decimal packed into an
     *         {@code int}
     */
    public static int $mod(Ctx ctx, int bits1, int bits2) {
        int     leftSevenBits1 = $leftmost7Bits(bits1);
        int     leftSevenBits2 = $leftmost7Bits(bits2);
        boolean finite1        = $isFinite(leftSevenBits1);
        boolean finite2        = $isFinite(leftSevenBits2);

        if (finite1 && finite2) {
            BigDecimal target  = $toBigDecimal(bits1);
            BigDecimal divisor = $toBigDecimal(bits2);
            BigDecimal bigR = target.remainder(divisor, MathContext.DECIMAL32);
            return $toIntBits(ctx, bigR.signum() >= 0 ? bigR : bigR.add(divisor));
        }
        // either side is infinity or NaN so return NaN
        return $NaN;
    }

    /**
     * Multiply two Dec32 values, each represented by an IEEE-754-2008 32-bit decimal packed into an
     * {@code int}.
     *
     * @param ctx    the current {@link Ctx}
     * @param bits1  the target IEEE-754-2008 32-bit decimal value
     * @param bits2  the IEEE-754-2008 32-bit decimal to add to the target value
     *
     * @return the result of the multiplication as an IEEE-754-2008 32-bit decimal packed into
     *         an {@code int}
     */
    public static int $mul(Ctx ctx, int bits1, int bits2) {
        int     leftSevenBits1 = $leftmost7Bits(bits1);
        int     leftSevenBits2 = $leftmost7Bits(bits2);
        boolean finite1        = $isFinite(leftSevenBits1);
        boolean finite2        = $isFinite(leftSevenBits2);

        if (finite1 && finite2) {
            return $toIntBits(ctx, $toBigDecimal(bits1).multiply($toBigDecimal(bits2)));
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
     * Negate a Dec32 value represented by an IEEE-754-2008 32-bit decimal packed into an
     * {@code int}.
     *
     * @param ctx   the current {@link Ctx}
     * @param bits  the target IEEE-754-2008 32-bit decimal value
     *
     * @return the negated IEEE-754-2008 32-bit decimal packed into an {@code int}
     */
    public static int $neg(Ctx ctx, int bits) {
        int leftSevenBits = $leftmost7Bits(bits);
        return $isFinite(leftSevenBits)
                ? $toIntBits(ctx, $toBigDecimal(bits).negate())
                : $isNaN(leftSevenBits)
                        ? $NaN
                        : $isSigned(leftSevenBits) ? $POS_INFINITY : $NEG_INFINITY;
    }

    /**
     * Subtract one Dec32 value from another, each represented by an IEEE-754-2008 32-bit decimal
     * packed into an {@code int}.
     *
     * @param ctx    the current {@link Ctx}
     * @param bits1  the target IEEE-754-2008 32-bit decimal value
     * @param bits2  the IEEE-754-2008 32-bit decimal to subtract from the target value
     *
     * @return the result of the subtraction as an IEEE-754-2008 32-bit decimal packed into
     *         an {@code int}
     */
    public static int $sub(Ctx ctx, int bits1, int bits2) {
        int     leftSevenBits1 = $leftmost7Bits(bits1);
        int     leftSevenBits2 = $leftmost7Bits(bits2);
        boolean finite1        = $isFinite(leftSevenBits1);
        boolean finite2        = $isFinite(leftSevenBits2);
        if (finite1 && finite2) {
            return $toIntBits(ctx, $toBigDecimal(bits1).subtract($toBigDecimal(bits2)));
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
    public int toDec32$p(Ctx ctx) {
        return $bits;
    }

    // ----- Orderable interface -------------------------------------------------------------------

    /**
     * The primitive implementation of:
     * <pre>
     * static <CompileType extends Orderable> Ordered compare(CompileType value1, CompileType value2);
     * </pre>
     */
    public static Ordered compare(Ctx ctx, nType type, Orderable value1, Orderable value2) {
        Dec32 decThis = (Dec32) value1;
        Dec32 decThat = (Dec32) value2;
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
        long l1 = ((Dec32) value1).$bits;
        long l2 = ((Dec32) value2).$bits;
        return l1 == l2 ? Boolean.TRUE : Boolean.FALSE;
    }

    // ----- helper methods ------------------------------------------------------------------------

    @Override
    public BigDecimal $toBigDecimal() {
        BigDecimal dec = $dec;
        if (dec == null && $isFinite()) {
            $dec = dec = $toBigDecimal($bits);
        }
        return dec;
    }

    @Override
    public int $getByteLength() {
        return 4;
    }

    @Override
    public int $getByte(int i) {
        return $getByte(i, $bits);
    }

    @Override
    protected int $leftmost7Bits() {
        return $bits >>> 25;
    }

    @Override
    public boolean $isZero() {
        return $isZero($bits);
    }

    /**
     * @return the significand of the decimal as an int
     */
    public int $getSignificand() {
        int nBits = $ensureFiniteBits($bits);
        int nToG4 = nBits >>> $G4_SHIFT;
        int nSig  = (nToG4 & 0b011000) == 0b011000
                ? (nToG4 & 0b000001) + 8
                : (nToG4 & 0b000111);

        // unpack the digits from the most significant declet to the least significant declet
        nSig = nSig * 1000 + $decletToInt(nBits >>> 10);
        return nSig * 1000 + $decletToInt(nBits);
    }

    /**
     * @return the exponent of the decimal as an int
     */
    public int $getExponent() {
        // a combination field is 11 bits (from bit 20 to bit 30), including 6 "pure" exponent bits
        int nCombo = $ensureFiniteBits($bits) >>> 20;
        int nExp   = (nCombo & 0b011000000000) == 0b011000000000
                ? (nCombo & 0b000110000000) >>> 1
                : (nCombo & 0b011000000000) >>> 3;

        // pull the rest of the exponent bits out of "pure" exponent section of the combo bits
        // section, and unbias the exponent
        return (nExp | nCombo & 0b111111) - 101;
    }

    /**
     * Convert the bits of an IEEE 754 decimal to a Java BigDecimal.
     *
     * @param nBits  a 32-bit value containing an IEEE 754 decimal
     *
     * @return a Java BigDecimal
     */
    public static BigDecimal $toBigDecimal(int nBits) {
        $ensureFiniteBits(nBits);

        // a combination field is 11 bits (from bit 20 to bit 30), including 6 "pure" exponent bits
        int nCombo = nBits >>> 20;
        int nExp   = nCombo & 0b111111;
        int nSig;

        // test G0 and G1
        if ((nCombo & 0b011000000000) == 0b011000000000) {
            // when the most significant five bits of G are 110xx or 1110x, the leading significand
            // digit d0 is 8+G4, a value 8 or 9, and the leading biased exponent bits are 2*G2 + G3,
            // a value of 0, 1, or 2
            nExp |= ((nCombo & 0b000110000000) >>> 1);    // shift right 7, but then shift left 6
            nSig  = ((nCombo & 0b000001000000) >>> 6) + 8;
        } else {
            // when the most significant five bits of G are 0xxxx or 10xxx, the leading significand
            // digit d0 is 4*G2 + 2*G3 + G4, a value in the range 0 through 7, and the leading
            // biased exponent bits are 2*G0 + G1, a value 0, 1, or 2; consequently if T is 0 and
            // the most significant five bits of G are 0b00000, 0b01000, or 0b10000, then the value
            // is 0:
            //      v = (−1) S * (+0)
            nExp |= (nCombo & 0b011000000000) >>> 3;    // shift right 9, but then shift left 6
            nSig  = (nCombo & 0b000111000000) >>> 6;
        }

        // unbias the exponent
        nExp -= 101;

        // unpack the digits from the most significant declet to the least significant declet
        nSig = ((nSig * 1000 + $decletToInt(nBits >>> 10))
                * 1000 + $decletToInt(nBits       ))
                * (((nBits & $SIGN_BIT) >> 31) | 1);       // apply sign

        return new BigDecimal(BigInteger.valueOf(nSig), -nExp, MathContext.DECIMAL32);
    }

    /**
     * Test the passed bits to ensure that they are finite; if they are not, throw an exception.
     *
     * @param nBits  the 32-bit IEEE-754-2008 decimal value
     *
     * @return a finite 32-bit IEEE-754-2008 decimal value
     *
     * @throws NumberFormatException if the decimal is either a NaN or an Infinity value
     */
    public static int $ensureFiniteBits(int nBits) {
        if ((nBits & $G0_G3_MASK) == $G0_G3_MASK) {
            throw new NumberFormatException("Not a finite value");
        }
        return nBits;
    }

    /**
     * Convert a Java BigDecimal to an IEEE 754 32-bit decimal contained in a Java {@code int}.
     *
     * @param ctx  the current context
     * @param dec  a Java BigDecimal value
     *
     * @return a Java {@code int} that contains a 32-bit IEEE 754 decimal value
     *
     * @throws OutOfBounds if the value is out of range
     */
    public static int $toIntBits(Ctx ctx, BigDecimal dec) {
        dec = dec.round(MathContext.DECIMAL32);

        // get the significand
        int nSig = dec.unscaledValue().intValueExact();
        if (nSig < -9999999 || nSig > 9999999) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "significand is >7 digits: " + nSig);
        }

        int nBits = 0;
        if (nSig < 0) {
            nBits = $SIGN_BIT;
            nSig  = -nSig;
        }

        // bias the exponent (the scale is basically a negative exponent)
        int nExp = 101 - dec.scale();
        if (nExp < 0 || nExp >= 192) {
            throw new OutOfBounds(ctx).$init(ctx,
                    "biased exponent is out of range [0,192): " + nExp);
        }

        // store the least significant 6 bits of the exponent into the combo field starting at G5
        // store the least significant 6 decimal digits of the significand in two 10-bit declets in T
        nBits |=  ((nExp & 0b111111              ) << 20)
                | ($intToDeclet(nSig / 1000 % 1000) << 10)
                | ($intToDeclet(nSig        % 1000)      );

        // the remaining significand of 8 or 9 is stored in G4 as 0 or 1, with the remaining
        // exponent stored in G2-G3, and G0-G1 both set to 1; otherwise, the remaining significand
        // (3 bits) is stored in G2-G4 with the remaining exponent stored in G0-G1
        nSig  /= 1000000;
        nBits |= nSig >= 8
                ? (0b11000 | (nSig & 0b00001) | ((nExp & 0b11000000) >>> 5)) << 26
                : (          (nSig & 0b00111) | ((nExp & 0b11000000) >>> 3)) << 26;

        return nBits;
    }

    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode() {
        return $bits;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Dec32 that && this.$bits == that.$bits;
    }
}
