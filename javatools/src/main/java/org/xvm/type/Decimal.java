package org.xvm.type;


import java.io.DataOutput;
import java.io.IOException;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;


/**
 * A representation of an IEEE-754-2008 decimal.
 *
 * <p/>
 * Implementation notes:
 * <p/>
 * <tt><pre>
 * IEEE-754 decimal interchange format parameters (table 3.5):
 * parameter                    32-bit      64-bit      128-bit     decimal{k}
 * ---------------------------  ----------  ----------  ----------  ----------------
 * k, storage width in bits     32          64          128         multiple of 32
 * p, precision in digits       7           16          34          9 * (k / 32) - 2 (or: 3*(t/10)+1)
 * emax                         96          384         6144        3 * 2^(k/16+3) (or: 3*2^(w-1))
 * emin=1-emax                  -95         -383        -6143       1-emax
 * bias, E-q                    101         398         6176        emax + p - 2
 * sign bit                     1           1           1           1
 * w, exponent bits             6           8           12          k/16 + 4 (or: k-t-6)
 * w+5, combination field bits  11          13          17          k/16 + 9
 * t, trailing significand bits 20          50          110         15 * (k/16) - 10 (or: k-w-6)
 * k, storage width in bits     32          64          128         1 + 5 + w + t
 *
 * bits are (from left to right, i.e. MSB to LSB) (figure 3.2):
 *      S       sign bit
 *      G0      first combination field bit (bit #(k-2))
 *      G1      second combination field bit
 *      ...
 *      G[w+4]  final combination field bit (bit #(k-w-6))
 *      T       trailing significand bits
 *
 * value is:
 *      (-1)^s * b^e * m
 * where:
 *      b is radix of 10
 *      s is 0 or 1
 *      e is any integer emin <= e <= emax
 *      m is a string of digits, each d[i] such that 0 <= d[i] < b
 *          d0 "." d1 d2 ... d[p-1]
 *      (thus it also holds that 0 <= m < b)
 *
 * viewing the significand as an integer instead, the value is:
 *      (-1)^s * b^q * c
 * where:
 *      b is radix of 10
 *      s is 0 or 1
 *      q is any integer emin <= (q+p-1) <= emax
 *      c is a string of digits, each d[i] such that 0 <= d[i] < b
 *          d0 d1 d2 ... d[p-1]
 *      (thus it also holds that c is an integer with 0 <= c < b^p)
 *
 * transformations:
 *      e = q + p - 1
 *      m = c * b^(1-p)
 * </pre></tt>
 */
public abstract class Decimal
    {
    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return the size of the IEEE-754-2008 formatted decimal, in bits, generally expected to be 32
     *         or a multiple thereof
     */
    public int getBitLength()
        {
        return getByteLength() * 8;
        }

    /**
     * @return the size of the IEEE-754-2008 formatted decimal, in bytes.
     */
    public abstract int getByteLength();

    /**
     * @return the MathContext to use for math operations that can utilize it
     */
    public abstract MathContext getMathContext();

    /**
     * Obtain a byte of the IEEE-754-2008 formatted decimal.
     *
     * @param i  the index of the byte, where 0 is the most significant byte, and
     *           <tt>({@link #getBitLength()}-1)</tt> is the least significant byte
     * @return
     */
    public abstract int getByte(int i);

    /**
     * @return an int whose 7 LSBs are (left to right): S, G0, G1, G2, G3, G4, G5
     */
    protected int leftmost7Bits()
        {
        return getByte(0) >>> 1;
        }

    /**
     * @return true iff the decimal is zero, including both positive and negative zero
     */
    public abstract boolean isZero();

    /**
     * @return -1, 0, or 1, depending on if the value is less than zero, zero (regardless of sign),
     *         or greater than zero
     */
    public int getSignum()
        {
        if (isZero())
            {
            return 0;
            }

        return isSigned() ? -1 : 1;
        }

    /**
     * @return true iff the sign bit is set
     */
    public boolean isSigned()
        {
        // sign bit S must be 1
        // ignore G0..G5
        return (leftmost7Bits() & 0b1000000) == 0b1000000;
        }

    /**
     * @return true iff the value is neither an infinity nor a NaN
     */
    public boolean isFinite()
        {
        // ignore sign bit S
        // G0..G3 must all be 1 for Infinity or NaN
        // ignore G4..G5
        return (leftmost7Bits() & 0b0111100) != 0b0111100;
        }

    /**
     * @return true iff the value is an infinity value, regardless of sign
     */
    public boolean isInfinite()
        {
        // ignore sign bit S
        // G0..G3 must all be 1 for Infinity
        // G4 must be 0 for Infinity
        // ignore G5
        return (leftmost7Bits() & 0b0111110) == 0b0111100;
        }

    /**
     * @return true iff the value is a NaN value, regardless of sign
     */
    public boolean isNaN()
        {
        // ignore sign bit S
        // G0..G4 must all be 1 for NaN
        // ignore G5
        return (leftmost7Bits() & 0b0111110) == 0b0111110;
        }

    /**
     * @return true iff the value is a signaling NaN value
     */
    public boolean isSignalingNaN()
        {
        // ignore sign bit S
        // G0..G4 must all be 1 for NaN
        // G5 must be 1 for signaling
        return (leftmost7Bits() & 0b0111111) == 0b0111111;
        }

    /**
     * @return true iff the value is a quiet NaN value
     */
    public boolean isQuietNaN()
        {
        // ignore sign bit S
        // G0..G4 must all be 1 for NaN
        // G5 must be 0 for quiet
        return (leftmost7Bits() & 0b0111111) == 0b0111110;
        }

    /**
     * Write the bytes of the decimal to the specified output stream.
     *
     * @param out  a DataOutput stream
     *
     * @throws IOException if an error occurs writing the decimal to the stream
     */
    public void writeBytes(DataOutput out)
            throws IOException
        {
        for (int i = 0, c = getByteLength(); i < c; ++i)
            {
            out.writeByte(getByte(i));
            }
        }

    /**
     * Compare this decimal to another decimal for purposes of ordering. Note that this does not
     * strictly order by the numeric value of the decimal itself, since two decimals may have the
     * same numeric value, but differ in small ways, such as defined by section "3.5.1 Cohorts".
     *
     * @param that  another decimal
     *
     * @return a value that is negative, zero, or positive to indicate less than, equal, or greater
     */
    public int compareForObjectOrder(Decimal that)
        {
        if (this == that)
            {
            return 0;
            }

        BigDecimal bdecThis = this.toBigDecimal();
        BigDecimal bdecThat = that.toBigDecimal();
        if (bdecThis == null || bdecThat == null)
            {
            // sort NaN, -Infinity, finite, +Infinity
            int nThis = this.isNaN() ? -2 : this.isFinite() ? 0 : this.isSigned() ? -1 : 1;
            int nThat = that.isNaN() ? -2 : that.isFinite() ? 0 : that.isSigned() ? -1 : 1;
            return nThis - nThat;
            }

        return bdecThis.compareTo(bdecThat);
        }


    // ----- operations ----------------------------------------------------------------------------

    public Decimal abs()
        {
        if (isFinite())
            {
            BigDecimal big = toBigDecimal();
            return fromBigDecimal(big.abs());
            }
        return isSigned() ? infinity(false) : this;
        }

    public Decimal neg()
        {
        if (isFinite())
            {
            BigDecimal big = toBigDecimal();
            return fromBigDecimal(big.negate());
            }
        return infinity(!isSigned());
        }

    public Decimal floor()
        {
        if (isFinite())
            {
            BigDecimal big = toBigDecimal();
            return fromBigDecimal(big.setScale(0, RoundingMode.FLOOR));
            }
        return this;
        }

    public Decimal ceil()
        {
        if (isFinite())
            {
            BigDecimal big = toBigDecimal();
            return fromBigDecimal(big.setScale(0, RoundingMode.CEILING));
            }
        return this;
        }

    public Decimal exp()
        {
        if (isFinite())
            {
            BigDecimal big = toBigDecimal();
            return fromBigDecimal(BigDecimal.valueOf(Math.exp(big.doubleValue())));
            }
        return isSigned() ? zero(false) : this;
        }

    public Decimal log()
        {
        if (isFinite())
            {
            BigDecimal big = toBigDecimal();
            return fromBigDecimal(BigDecimal.valueOf(Math.log(big.doubleValue())));
            }
        return isSigned() ? nan() : this;
        }

    public Decimal log2()
        {
        if (isFinite())
            {
            BigDecimal big = toBigDecimal();
            return fromBigDecimal(new BigDecimal(Math.log10(big.doubleValue())*LOG2_10));
            }
        return isSigned() ? nan() : this;
        }

    public Decimal log10()
        {
        if (isFinite())
            {
            BigDecimal big = toBigDecimal();
            return fromBigDecimal(BigDecimal.valueOf(Math.log10(big.doubleValue())));
            }
        return isSigned() ? nan() : this;
        }

    public Decimal sqrt()
        {
        if (isFinite())
            {
            BigDecimal big = toBigDecimal();
            return fromBigDecimal(BigDecimal.valueOf(Math.sqrt(big.doubleValue())));
            }
        return isSigned() ? nan() : this;
        }

    public Decimal cbrt()
        {
        if (isFinite())
            {
            BigDecimal big = toBigDecimal();
            return fromBigDecimal(BigDecimal.valueOf(Math.cbrt(big.doubleValue())));
            }
        return this;
        }

    public Decimal sin()
        {
        if (isFinite())
            {
            BigDecimal big = toBigDecimal();
            return fromBigDecimal(BigDecimal.valueOf(Math.sin(big.doubleValue())));
            }
        return nan();
        }

    public Decimal tan()
        {
        if (isFinite())
            {
            BigDecimal big = toBigDecimal();
            return fromBigDecimal(BigDecimal.valueOf(Math.tan(big.doubleValue())));
            }
        return nan();
        }

    public Decimal asin()
        {
        if (isFinite())
            {
            BigDecimal big = toBigDecimal();
            return fromBigDecimal(BigDecimal.valueOf(Math.asin(big.doubleValue())));
            }
        return nan();
        }

    public Decimal acos()
        {
        if (isFinite())
            {
            BigDecimal big = toBigDecimal();
            return fromBigDecimal(BigDecimal.valueOf(Math.acos(big.doubleValue())));
            }
        return nan();
        }

    public Decimal atan()
        {
        if (isFinite())
            {
            BigDecimal big = toBigDecimal();
            return fromBigDecimal(BigDecimal.valueOf(Math.atan(big.doubleValue())));
            }
        return nan();
        }

    public Decimal sinh()
        {
        if (isFinite())
            {
            BigDecimal big = toBigDecimal();
            return fromBigDecimal(BigDecimal.valueOf(Math.sinh(big.doubleValue())));
            }
        return nan();
        }

    public Decimal cosh()
        {
        if (isFinite())
            {
            BigDecimal big = toBigDecimal();
            return fromBigDecimal(BigDecimal.valueOf(Math.cosh(big.doubleValue())));
            }
        return nan();
        }

    public Decimal tanh()
        {
        if (isFinite())
            {
            BigDecimal big = toBigDecimal();
            return fromBigDecimal(BigDecimal.valueOf(Math.tanh(big.doubleValue())));
            }
        return nan();
        }

    public Decimal asinh()
        {
        if (isFinite())
            {
            double d = toBigDecimal().doubleValue();
            return fromBigDecimal(BigDecimal.valueOf(Math.log(d + Math.sqrt(d * d + 1.0))));
            }
        return nan();
        }

    public Decimal acosh()
        {
        if (isFinite())
            {
            double d = toBigDecimal().doubleValue();
            return fromBigDecimal(BigDecimal.valueOf(Math.log(d + Math.sqrt(d * d - 1.0))));
            }
        return nan();
        }

    public Decimal atanh()
        {
        if (isFinite())
            {
            double d = toBigDecimal().doubleValue();
            return fromBigDecimal(BigDecimal.valueOf(0.5 * Math.log((d + 1.0) / (d - 1.0))));
            }
        return nan();
        }

    public Decimal deg2rad()
        {
        if (isFinite())
            {
            BigDecimal big = toBigDecimal();
            return fromBigDecimal(BigDecimal.valueOf(Math.toRadians(big.doubleValue())));
            }
        return this;
        }

    public Decimal rad2deg()
        {
        if (isFinite())
            {
            BigDecimal big = toBigDecimal();
            return fromBigDecimal(BigDecimal.valueOf(Math.toDegrees(big.doubleValue())));
            }
        return this;
        }

    public Decimal nextUp()
        {
        if (isFinite())
            {
            BigDecimal big = toBigDecimal();
            return fromBigDecimal(BigDecimal.valueOf(Math.nextUp(big.doubleValue())));
            }
        return this;
        }

    public Decimal nextDown()
        {
        if (isFinite())
            {
            BigDecimal big = toBigDecimal();
            return fromBigDecimal(BigDecimal.valueOf(Math.nextDown(big.doubleValue())));
            }
        return this;
        }

    public Decimal round(RoundingMode mode)
        {
        if (isFinite())
            {
            BigDecimal big = toBigDecimal();
            return fromBigDecimal(big.setScale(0, mode));
            }
        return this;
        }

    public Decimal add(Decimal that)
        {
        if (this.isFinite() && that.isFinite())
            {
            BigDecimal big1 = this.toBigDecimal();
            BigDecimal big2 = that.toBigDecimal();
            return fromBigDecimal(big1.add(big2, getMathContext()));
            }
        return isFinite()
            ? that
            : that.isFinite()
                ? this
                : this.isSigned() == that.isSigned()
                    ? this
                    : nan();
        }

    public Decimal subtract(Decimal that)
        {
        if (this.isFinite() && that.isFinite())
            {
            BigDecimal big1 = this.toBigDecimal();
            BigDecimal big2 = that.toBigDecimal();
            return fromBigDecimal(big1.subtract(big2, getMathContext()));
            }
        return isFinite()
            ? infinity(!that.isSigned())
            : that.isFinite()
                ? this
                : this.isSigned() == that.isSigned()
                    ? nan()
                    : this;
        }

    public Decimal multiply(Decimal that)
        {
        if (this.isFinite() && that.isFinite())
            {
            BigDecimal big1 = this.toBigDecimal();
            BigDecimal big2 = that.toBigDecimal();
            return fromBigDecimal(big1.multiply(big2, getMathContext()));
            }
        return infinity(that.isSigned() != this.isSigned());
        }

    public Decimal divide(Decimal that)
        {
        if (this.isFinite() && that.isFinite())
            {
            BigDecimal big1 = this.toBigDecimal();
            BigDecimal big2 = that.toBigDecimal();
            return fromBigDecimal(big1.divide(big2, getMathContext()));
            }
        return isFinite() ? zero(this.isSigned() != that.isSigned()) : nan();
        }

    public Decimal mod(Decimal that)
        {
        if (this.isFinite() && that.isFinite())
            {
            BigDecimal big1 = this.toBigDecimal();
            BigDecimal big2 = that.toBigDecimal();
            BigDecimal bigR = big1.remainder(big2, getMathContext());
            return fromBigDecimal(bigR.signum() >= 0 ? bigR : bigR.add(big2));
            }
        return nan();
        }

    public Decimal pow(Decimal that)
        {
        if (isFinite())
            {
            if (that.isFinite())
                {
                BigDecimal big1 = this.toBigDecimal();
                BigDecimal big2 = that.toBigDecimal();
                return fromBigDecimal(big1.pow(big2.intValue(), getMathContext()));
                }
            return nan();
            }
        return this;
        }

    public Decimal pow(int nPow)
        {
        if (isFinite())
            {
            BigDecimal big1 = toBigDecimal();
            return fromBigDecimal(big1.pow(nPow, getMathContext()));
            }
        return this;
        }

    public Decimal atan2(Decimal that)
        {
        if (isFinite())
            {
            if (that.isFinite())
                {
                BigDecimal big1 = this.toBigDecimal();
                BigDecimal big2 = that.toBigDecimal();
                return fromBigDecimal(
                    BigDecimal.valueOf(Math.atan2(big1.doubleValue(), big2.doubleValue())));
                }
            return that;
            }
        return this;
        }

    // ----- conversions ---------------------------------------------------------------------------

    /**
     * Obtain the decimal value as a Java BigDecimal, iff the decimal is finite.
     *
     * @return a BigDecimal, or null if the decimal is not finite
     */
    public abstract BigDecimal toBigDecimal();

    /**
     * Obtain the decimal value for the specified Java BigDecimal.
     *
     * @return a decimal
     */
    public abstract Decimal fromBigDecimal(BigDecimal big);

    /**
     * Obtain the decimal value for an infinity.
     *
     * @return a decimal representing the corresponding infinity value
     */
    public abstract Decimal infinity(boolean fSigned);

    /**
     * Obtain the decimal value for a zero.
     *
     * @return a decimal representing the corresponding zero value
     */
    public abstract Decimal zero(boolean fSigned);

    /**
     * Obtain the decimal value for a not-a-number
     *
     * @return a decimal representing NaN
     */
    public abstract Decimal nan();

    /**
     * Obtain the decimal value as a byte array.
     *
     * @return a byte array containing an IEEE-754-2008 32-bit decimal
     */
    public abstract byte[] toByteArray();


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public abstract int hashCode();

    @Override
    public abstract boolean equals(Object obj);

    @Override
    public String toString()
        {
        if (isFinite())
            {
            return isZero() && isSigned()
                ? "-0"
                : toBigDecimal().stripTrailingZeros().toEngineeringString();
            }

        if (isInfinite())
            {
            return (isSigned() ? '-' : "") + "Infinity";
            }

        return isSignalingNaN() ? "sNaN" : "NaN";
        }


    // ----- helpers -------------------------------------------------------------------------------

    /**
     * Convert the three least significant decimal digits of the passed integer value to a declet.
     * <p/>
     * Details are in IEEE 754-2008 section 3.5.2, table 3.4.
     *
     * @param nDigits  the int value containing the digits
     *
     * @return a declet
     */
    public static int intToDeclet(int nDigits)
        {
        return digitsToDeclet((nDigits / 100) % 10, (nDigits / 10) % 10, nDigits % 10);
        }

    /**
     * Convert three decimal digits to a declet.
     * <p/>
     * Details are in IEEE 754-2008 section 3.5.2, table 3.4.
     *
     * @param d1  4-bit value "d1" from table 3.4 (most significant digit)
     * @param d2  4-bit value "d2" from table 3.4
     * @param d3  4-bit value "d3" from table 3.4 (least significant digit)
     *
     * @return a declet
     */
    public static int digitsToDeclet(int d1, int d2, int d3)
        {
        switch ((d1 & 0b1000) >>> 1 | (d2 & 0b1000) >>> 2 | (d3 & 0b1000) >>> 3)
            {
            // table 3.4        (const 1's)
            // d1.0 d2.0 d3.0   b0123456789   b0 b1 b2                         b3 b4 b5                         b7 b8 b9
            // --------------  ------------   ------------------------------   ------------------------------   ----------
            case 0b000: return 0b0000000000 | (d1 & 0b111)              << 7 | (d2 & 0b111)              << 4 | d3 & 0b111;
            case 0b001: return 0b0000001000 | (d1 & 0b111)              << 7 | (d2 & 0b111)              << 4 | d3 & 0b001;
            case 0b010: return 0b0000001010 | (d1 & 0b111)              << 7 | (d3 & 0b110 | d2 & 0b001) << 4 | d3 & 0b001;
            case 0b011: return 0b0001001110 | (d1 & 0b111)              << 7 | (d2 & 0b001)              << 4 | d3 & 0b001;
            case 0b100: return 0b0000001100 | (d3 & 0b110 | d1 & 0b001) << 7 | (d2 & 0b111)              << 4 | d3 & 0b001;
            case 0b101: return 0b0000101110 | (d2 & 0b110 | d1 & 0b001) << 7 | (d2 & 0b001)              << 4 | d3 & 0b001;
            case 0b110: return 0b0000001110 | (d3 & 0b110 | d1 & 0b001) << 7 | (d2 & 0b001)              << 4 | d3 & 0b001;
            case 0b111: return 0b0001101110 | (d1 & 0b001)              << 7 | (d2 & 0b001)              << 4 | d3 & 0b001;

            default:
                throw new IllegalArgumentException("d1=" + d1 + ", d2=" + d2 + ", d3=" + d3);
            }
        }

    /**
     * Convert the passed declet to three decimal digits, and format them as a Java <tt>int</tt> in
     * the range 0-999.
     * <p/>
     * Details are in IEEE 754-2008 section 3.5.2, table 3.3.
     *
     * @param nBits  a declet
     *
     * @return three decimal digits in a Java <tt>int</tt> (000-999)
     */
    public static int decletToInt(int nBits)
        {
        //               b6 b7 b8                b3 b4
        switch ((nBits & 0b1110) << 1 | (nBits & 0b1100000) >>> 5)
            {
            //     0xxxx                     b0123456789
            default:
                return 100 * (    ((nBits & 0b1110000000) >>> 7)) +   // d1 = b0 b1 b2
                        10 * (    ((nBits & 0b0001110000) >>> 4)) +   // d2 = b3 b4 b5
                             (    ((nBits & 0b0000000111)      ));    // d3 = b7 b8 b9
            //     100xx
            case 0b10000:
            case 0b10001:
            case 0b10010:
            case 0b10011:
                return 100 * (    ((nBits & 0b1110000000) >>> 7)) +   // d1 = b0 b1 b2
                        10 * (    ((nBits & 0b0001110000) >>> 4)) +   // d2 = b3 b4 b5
                             (8 + ((nBits & 0b0000000001)      ));    // d3 = 8 + b9
            //     101xx
            case 0b10100:
            case 0b10101:
            case 0b10110:
            case 0b10111:
                return 100 * (    ((nBits & 0b1110000000) >>> 7)) +   // d1 = b0 b1 b2
                        10 * (8 + ((nBits & 0b0000010000) >>> 4)) +   // d2 = 8 + b5
                             (    ((nBits & 0b0001100000) >>> 4)      // d3 = b3 b4
                                + ((nBits & 0b0000000001)      ));    //    + b9
            //     110xx
            case 0b11000:
            case 0b11001:
            case 0b11010:
            case 0b11011:
                return 100 * (8 + ((nBits & 0b0010000000) >>> 7)) +   // d1 = 8 + b2
                        10 * (    ((nBits & 0b0001110000) >>> 4)) +   // d2 = b3 b4 b5
                             (    ((nBits & 0b1100000000) >>> 7)      // d3 = b0 b1
                                + ((nBits & 0b0000000001)      ));    //    + b9
            //     11100
            case 0b11100:
                return 100 * (8 + ((nBits & 0b0010000000) >>> 7)) +   // d1 = 8 + b2
                        10 * (8 + ((nBits & 0b0000010000) >>> 4)) +   // d2 = 8 + b5
                             (    ((nBits & 0b1100000000) >>> 7)      // d3 = b0 b1
                                + ((nBits & 0b0000000001)      ));    //    + b9
            //     11101
            case 0b11101:
                return 100 * (8 + ((nBits & 0b0010000000) >>> 7)) +   // d1 = 8 + b2
                        10 * (    ((nBits & 0b1100000000) >>> 7)      // d2 = b0 b1
                                + ((nBits & 0b0000010000) >>> 4)) +   //    + b5
                             (8 + ((nBits & 0b0000000001)      ));    // d3 = 8 + b9
            //     11110
            case 0b11110:
                return 100 * (    ((nBits & 0b1110000000) >>> 7)) +   // d1 = b0 b1 b2
                        10 * (8 + ((nBits & 0b0000010000) >>> 4)) +   // d2 = 8 + b5
                             (8 + ((nBits & 0b0000000001)      ));    // d3 = 8 + b9
            //     11111
            case 0b11111:
                return 100 * (8 + ((nBits & 0b0010000000) >>> 7)) +   // d1 = 8 + b2
                        10 * (8 + ((nBits & 0b0000010000) >>> 4)) +   // d2 = 8 + b5
                             (8 + ((nBits & 0b0000000001)      ));    // d3 = 8 + b9
            }
        }

    /**
     * Convert the passed declet to three decimal digits, and return each of them in the three least
     * significant bytes of a Java <tt>int</tt>.
     * <p/>
     * Details are in IEEE 754-2008 section 3.5.2, table 3.3.
     *
     * @param nBits  a declet
     *
     * @return three decimal digits in a Java <tt>int</tt>, such that bits 0-7 contain the least
     *         significant digit, bits 8-15 the second, and bits 16-23 the most significant digit
     */
    public static int decletToDigits(int nBits)
        {
        //               b6 b7 b8                b3 b4
        switch ((nBits & 0b1110) << 1 | (nBits & 0b1100000) >>> 5)
            {
            //     0xxxx                     b0123456789
            default:
                return 256 * (    ((nBits & 0b1110000000) >>> 7)) +   // d1 = b0 b1 b2
                        16 * (    ((nBits & 0b0001110000) >>> 4)) +   // d2 = b3 b4 b5
                             (    ((nBits & 0b0000000111)      ));    // d3 = b7 b8 b9
            //     100xx
            case 0b10000:
            case 0b10001:
            case 0b10010:
            case 0b10011:
                return 256 * (    ((nBits & 0b1110000000) >>> 7)) +   // d1 = b0 b1 b2
                        16 * (    ((nBits & 0b0001110000) >>> 4)) +   // d2 = b3 b4 b5
                             (8 + ((nBits & 0b0000000001)      ));    // d3 = 8 + b9
            //     101xx
            case 0b10100:
            case 0b10101:
            case 0b10110:
            case 0b10111:
                return 256 * (    ((nBits & 0b1110000000) >>> 7)) +   // d1 = b0 b1 b2
                        16 * (8 + ((nBits & 0b0000010000) >>> 4)) +   // d2 = 8 + b5
                             (    ((nBits & 0b0001100000) >>> 4)      // d3 = b3 b4
                                + ((nBits & 0b0000000001)      ));    //    + b9
            //     110xx
            case 0b11000:
            case 0b11001:
            case 0b11010:
            case 0b11011:
                return 256 * (8 + ((nBits & 0b0010000000) >>> 7)) +   // d1 = 8 + b2
                        16 * (    ((nBits & 0b0001110000) >>> 4)) +   // d2 = b3 b4 b5
                             (    ((nBits & 0b1100000000) >>> 7)      // d3 = b0 b1
                                + ((nBits & 0b0000000001)      ));    //    + b9
            //     11100
            case 0b11100:
                return 256 * (8 + ((nBits & 0b0010000000) >>> 7)) +   // d1 = 8 + b2
                        16 * (8 + ((nBits & 0b0000010000) >>> 4)) +   // d2 = 8 + b5
                             (    ((nBits & 0b1100000000) >>> 7)      // d3 = b0 b1
                                + ((nBits & 0b0000000001)      ));    //    + b9
            //     11101
            case 0b11101:
                return 256 * (8 + ((nBits & 0b0010000000) >>> 7)) +   // d1 = 8 + b2
                        16 * (    ((nBits & 0b1100000000) >>> 7)      // d2 = b0 b1
                                + ((nBits & 0b0000010000) >>> 4)) +   //    + b5
                             (8 + ((nBits & 0b0000000001)      ));    // d3 = 8 + b9
            //     11110
            case 0b11110:
                return 256 * (    ((nBits & 0b1110000000) >>> 7)) +   // d1 = b0 b1 b2
                        16 * (8 + ((nBits & 0b0000010000) >>> 4)) +   // d2 = 8 + b5
                             (8 + ((nBits & 0b0000000001)      ));    // d3 = 8 + b9
            //     11111
            case 0b11111:
                return 256 * (8 + ((nBits & 0b0010000000) >>> 7)) +   // d1 = 8 + b2
                        16 * (8 + ((nBits & 0b0000010000) >>> 4)) +   // d2 = 8 + b5
                             (8 + ((nBits & 0b0000000001)      ));    // d3 = 8 + b9
            }
        }

    /**
     * An extension of the ArithmeticException that carries a suggested infinite or NaN Decimal.
     */
    public static class RangeException
            extends ArithmeticException
        {
        public RangeException(String s, Decimal decNaN)
            {
            super(s);

            f_decNaN = decNaN;
            }

        public Decimal getDecimal()
            {
            return f_decNaN;
            }

        private final Decimal f_decNaN;
        }

    /**
     * The log2(10) value.
     */
    public static final double LOG2_10 = 1.0/Math.log10(2);
    }