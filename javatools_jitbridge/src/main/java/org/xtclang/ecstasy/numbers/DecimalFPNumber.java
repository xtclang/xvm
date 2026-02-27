package org.xtclang.ecstasy.numbers;

import java.math.BigDecimal;
import java.math.BigInteger;

import org.xvm.javajit.Ctx;

import org.xtclang.ecstasy.text.String;

/**
 * Native DecimalFPNumber wrapper.
 */
public abstract class DecimalFPNumber
        extends FPNumber
        implements FPConvertible {

    /**
     * The least significant 46 bits.
     */
    protected static final long $LS46BITS = 0x3FFFFFFFFFFFL;

    /**
     * The cached value of this decimal as a {@link BigDecimal}.
     */
    protected BigDecimal $dec;

    // ----- JIT methods ---------------------------------------------------------------------------

    @Override
    public String toString(Ctx ctx) {
        return String.of(ctx, $toBigDecimal().toEngineeringString());
    }

    /**
     * @return the size of the IEEE-754-2008 formatted decimal, in bits, generally expected to be 32
     *         or a multiple thereof
     */
    public int $getBitLength() {
        return $getByteLength() * 8;
    }

    /**
     * @return the size of the IEEE-754-2008 formatted decimal, in bytes.
     */
    public abstract int $getByteLength();

    /**
     * Obtain a byte of the IEEE-754-2008 formatted decimal.
     *
     * @param i  the index of the byte, where 0 is the most significant byte, and
     *           <tt>({@link #$getBitLength()}-1)</tt> is the least significant byte
     *
     * @return the byte at the specified index
     */
    public abstract int $getByte(int i);

    /**
     * Obtain a byte of the IEEE-754-2008 formatted decimal stored in a Java {@code int}.
     *
     * @param i     the index of the byte, where 0 is the most significant byte, and
     *              <tt>({@link #$getBitLength()}-1)</tt> is the least significant byte
     * @param bits  a Java {@code int} containing the IEEE-754-2008 formatted decimal
     *
     * @return the byte at the specified index
     */
    public static int $getByte(int i, int bits) {
        if ((i & ~0x3) != 0) {
            throw new IllegalArgumentException("index out of range: " + i);
        }
        return (bits >>> (i*8)) & 0xFF;
    }

    /**
     * Obtain a byte of the IEEE-754-2008 formatted decimal stored in a Java {@code long}.
     *
     * @param i     the index of the byte, where 0 is the most significant byte, and
     *              <tt>({@link #$getBitLength()}-1)</tt> is the least significant byte
     * @param bits  a Java {@code long} containing the IEEE-754-2008 formatted decimal
     *
     * @return the byte at the specified index
     */
    public static int $getByte(int i, long bits) {
        if ((i & ~0x7) != 0) {
            throw new IllegalArgumentException("index out of range: " + i);
        }
        return ((int) (bits >>> (i*8))) & 0xFF;
    }

    /**
     * Obtain a byte of the IEEE-754-2008 formatted decimal stored in a Java {@code long}.
     *
     * @param i         the index of the byte, where 0 is the most significant byte, and
     *                  <tt>({@link #$getBitLength()}-1)</tt> is the least significant byte
     * @param lowBits   a Java {@code long} containing the low 64-bits of a IEEE-754-2008 formatted
     *                  decimal
     * @param highBits  a Java {@code long} containing the high 64-bits of a IEEE-754-2008 formatted
     *                  decimal
     *
     * @return the byte at the specified index
     */
    public static int $getByte(int i, long lowBits, long highBits) {
        if ((i & ~0xF) != 0) {
            throw new IllegalArgumentException("index out of range: " + i);
        }
        long lBits = i < 8 ? highBits : lowBits;
        return ((int) (lBits >>> ((i&7)*8))) & 0xFF;
    }

    /**
     * @return an int whose 7 LSBs are (left to right): S, G0, G1, G2, G3, G4, G5
     */
    protected int $leftmost7Bits() {
        return $getByte(0) >>> 1;
    }

    /**
     * Obtain an int whose 7 LSBs are (left to right): S, G0, G1, G2, G3, G4, G5
     *
     * @param bits  a Java {@code int} containing the IEEE-754-2008 formatted decimal
     *
     * @return an int whose 7 LSBs are (left to right): S, G0, G1, G2, G3, G4, G5
     */
    protected static int $leftmost7Bits(int bits) {
        return $getByte(0, bits) >>> 1;
    }

    /**
     * Obtain an int whose 7 LSBs are (left to right): S, G0, G1, G2, G3, G4, G5
     *
     * @param bits  a Java {@code long} containing the IEEE-754-2008 formatted decimal
     *
     * @return an int whose 7 LSBs are (left to right): S, G0, G1, G2, G3, G4, G5
     */
    protected static int $leftmost7Bits(long bits) {
        return $getByte(0, bits) >>> 1;
    }

    /**
     * Obtain an int whose 7 LSBs are (left to right): S, G0, G1, G2, G3, G4, G5
     *
     * @param lowBits   a Java {@code long} containing the low 64-bits of a IEEE-754-2008 formatted
     *                  decimal
     * @param highBits  a Java {@code long} containing the high 64-bits of a IEEE-754-2008 formatted
     *                  decimal
     *
     * @return an int whose 7 LSBs are (left to right): S, G0, G1, G2, G3, G4, G5
     */
    protected static int $leftmost7Bits(long lowBits, long highBits) {
        return (int) (highBits >>> 57);
    }

    /**
     * @return true iff the decimal is zero, including both positive and negative zero
     */
    public abstract boolean $isZero();

    /**
     * Determine whether the 32-bit IEEE-754-2008 formatted decimal is zero, including both
     *         positive and negative zero
     *
     * @param bits  a Java {@code int} containing the 32-bits of a IEEE-754-2008 formatted decimal
     *
     * @return true iff the 32-bit IEEE-754-2008 formatted decimal is zero
     */
    public static boolean $isZero(int bits) {
        // G0 and G1 must not both be 1, and G2-G4 must be 0, and T (rightmost 20 bits) must be 0
        return ($leftmost7Bits(bits) & 0b0110000) != 0b0110000
                    && (bits & 0b00011100000011111111111111111111) == 0;
    }

    /**
     * Determine whether the 64-bit IEEE-754-2008 formatted decimal is zero, including both
     * positive and negative zero
     *
     * @param bits  a Java {@code long} containing the 64-bits of a IEEE-754-2008 formatted decimal
     *
     * @return true iff the 64-bit IEEE-754-2008 formatted decimal is zero
     */
    public static boolean $isZero(long bits) {
        // G0 and G1 must not both be 1, and G2-G4 must be 0, and T (rightmost 50 bits) must be 0
        return ($leftmost7Bits(bits) & 0b0110000) != 0b0110000 && (bits & 0x1C03FFFFFFFFFFFFL) == 0;
    }

    /**
     * Determine whether the 128-bit IEEE-754-2008 formatted decimal is zero, including both
     * positive and negative zero
     *
     * @param lowBits   a Java {@code long} containing the low 64-bits of a IEEE-754-2008 formatted
     *                  decimal
     * @param highBits  a Java {@code long} containing the high 64-bits of a IEEE-754-2008 formatted
     *                  decimal
     *
     * @return true iff the 128-bit IEEE-754-2008 formatted decimal is zero
     */
    public static boolean $isZero(long lowBits, long highBits) {
        // G0 and G1 must not both be 1, and G2-G4 must be 0, and T (rightmost 110 bits) must be 0
        return ($leftmost7Bits(lowBits, highBits) & 0b0110000) != 0b0110000
                    && (highBits & $LS46BITS) == 0 && lowBits == 0;
    }

    /**
     * @return true iff the sign bit is set
     */
    public boolean $isSigned() {
        // sign bit S must be 1
        // ignore G0..G5
        return $isSigned($leftmost7Bits());
    }

    /**
     * @return true iff the sign bit is set
     */
    public static boolean $isSigned(int leftSevenBits) {
        // sign bit S must be 1
        // ignore G0..G5
        return (leftSevenBits & 0b1000000) == 0b1000000;
    }

    /**
     * @return true iff the value is neither an infinity nor a NaN
     */
    public boolean $isFinite() {
        // ignore sign bit S
        // G0..G3 must all be 1 for Infinity or NaN
        // ignore G4..G5
        return $isFinite($leftmost7Bits());
    }

    /**
     * Determine whether a decimal value is neither an infinity nor a NaN
     *
     * @param leftSevenBits  the leftmost 7 bits of the decimal value
     *
     * @return true iff the value is neither an infinity nor a NaN
     */
    public static boolean $isFinite(int leftSevenBits) {
        // ignore sign bit S
        // G0..G3 must all be 1 for Infinity or NaN
        // ignore G4..G5
        return (leftSevenBits & 0b0111100) != 0b0111100;
    }

    /**
     * @return true iff the value is an infinity value, regardless of sign
     */
    public boolean $isInfinite() {
        // ignore sign bit S
        // G0..G3 must all be 1 for Infinity
        // G4 must be 0 for Infinity
        // ignore G5
        return $isInfinite($leftmost7Bits());
    }

    /**
     * Determine whether a decimal value is an infinity value, regardless of sign
     *
     * @param leftSevenBits  the leftmost 7 bits of the decimal value
     *
     * @return true iff the value is an infinity value, regardless of sign
     */
    public static boolean $isInfinite(int leftSevenBits) {
        // ignore sign bit S
        // G0..G3 must all be 1 for Infinity
        // G4 must be 0 for Infinity
        // ignore G5
        return (leftSevenBits & 0b0111110) == 0b0111100;
    }

    /**
     * @return true iff the value is a NaN value, regardless of sign
     */
    public boolean $isNaN() {
        return $isNaN($leftmost7Bits());
    }

    /**
     * Determine whether a decimal value is a NaN value, regardless of sign.
     *
     * @param leftSevenBits  the leftmost 7 bits of the decimal value
     *
     * @return true iff the value is a NaN value, regardless of sign
     */
    public static boolean $isNaN(int leftSevenBits) {
        // ignore sign bit S
        // G0..G4 must all be 1 for NaN
        // ignore G5
        return (leftSevenBits & 0b0111110) == 0b0111110;
    }


    /**
     * @return true iff the value is a signaling NaN value
     */
    public boolean $isSignalingNaN() {
        // ignore sign bit S
        // G0..G4 must all be 1 for NaN
        // G5 must be 1 for signaling
        return ($leftmost7Bits() & 0b0111111) == 0b0111111;
    }

    /**
     * @return true iff the value is a quiet NaN value
     */
    public boolean $isQuietNaN() {
        // ignore sign bit S
        // G0..G4 must all be 1 for NaN
        // G5 must be 0 for quiet
        return ($leftmost7Bits() & 0b0111111) == 0b0111110;
    }

    /**
     * Compare any two decimal values for equality.
     *
     * @param that  any of a Dec32, Dec64, or Dec128
     *
     * @return true iff the two values are equal in terms of their sign, significand, and exponent
     */
    public boolean $isSameValue(DecimalFPNumber that) {
        if (this == that) {
            return true;
        }

        if (that == null) {
            return false;
        }

        if (this.getClass() == that.getClass()) {
            return this.equals(that);
        }

        if (!(this.$isFinite() && that.$isFinite())) {
            return this.$isSigned()       == that.$isSigned()
                    && this.$isInfinite()     == that.$isInfinite()
                    && this.$isQuietNaN()     == that.$isQuietNaN()
                    && this.$isSignalingNaN() == that.$isSignalingNaN();
        }

        DecimalFPNumber dec1 = this;
        DecimalFPNumber dec2 = that;
        switch ((dec1.$getBitLength() << 8) | dec2.$getBitLength()) {
            case ( 64 << 8) | 32:
                dec1 = that;
                dec2 = this;
                // fall through
            case ( 32 << 8) | 64: {
                Dec32 dec32 = (Dec32) dec1;
                Dec64 dec64 = (Dec64) dec2;
                return dec32.$isSigned()       == dec64.$isSigned()
                        && dec32.$getExponent()    == dec64.$getExponent()
                        && dec32.$getSignificand() == dec64.$getSignificand();
            }

            case (128 << 8) | 32:
                dec1 = that;
                dec2 = this;
                // fall through
            case ( 32 << 8) | 128: {
                Dec32  dec32  = (Dec32)  dec1;
                Dec128 dec128 = (Dec128) dec2;
                return dec32.$isSigned()       == dec128.$isSigned()
                        && dec32.$getExponent()    == dec128.$getExponent()
                        && BigInteger.valueOf(dec32.$getSignificand()).equals(dec128.$getSignificand());
            }

            case (128 << 8) | 64:
                dec1 = that;
                dec2 = this;
                // fall through
            case ( 64 << 8) | 128: {
                Dec64  dec64  = (Dec64)  dec1;
                Dec128 dec128 = (Dec128) dec2;
                return dec64.$isSigned()       == dec128.$isSigned()
                        && dec64.$getExponent()    == dec128.$getExponent()
                        && BigInteger.valueOf(dec64.$getSignificand()).equals(dec128.$getSignificand());
            }

            default:
                throw new UnsupportedOperationException("dec1=" + dec1.getClass().getName()
                        + ", dec2=" + dec1.getClass().getName());
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
    public int $compareForObjectOrder(DecimalFPNumber that) {
        if (this == that) {
            return 0;
        }

        BigDecimal bdecThis = this.$toBigDecimal();
        BigDecimal bdecThat = that.$toBigDecimal();
        if (bdecThis == null || bdecThat == null) {
            // sort NaN, -Infinity, finite, +Infinity
            int nThis = this.$isNaN() ? -2 : this.$isFinite() ? 0 : this.$isSigned() ? -1 : 1;
            int nThat = that.$isNaN() ? -2 : that.$isFinite() ? 0 : that.$isSigned() ? -1 : 1;
            return nThis - nThat;
        }

        return bdecThis.compareTo(bdecThat);
    }

    /**
     * Obtain the decimal value as a Java BigDecimal, iff the decimal is finite.
     *
     * @return a BigDecimal, or null if the decimal is not finite
     */
    public abstract BigDecimal $toBigDecimal();

    /**
     * Convert this decimal to a Java {@link BigInteger}.
     * <p>
     * If the {@code direction} parameter is {@code null}, a default direction value of
     * {@link Rounding.TowardZero} will be used. This is
     *
     * @param direction  an optional {@link Rounding} direction to use
     *
     * @return this decimal converted to a Java {@link BigInteger}
     */
    public BigInteger $toBigInteger(Rounding direction) {
        if (direction == null) {
            direction = Rounding.TowardZero.$INSTANCE;
        }
        return $toBigDecimal().setScale(0, direction.$roundingMode()).toBigInteger();
    }

    /**
     * Convert the three least significant decimal digits of the passed integer value to a declet.
     * <p/>
     * Details are in IEEE 754-2008 section 3.5.2, table 3.4.
     *
     * @param nDigits  the int value containing the digits
     *
     * @return a declet
     */
    public static int $intToDeclet(int nDigits) {
        return $digitsToDeclet((nDigits / 100) % 10, (nDigits / 10) % 10, nDigits % 10);
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
    public static int $digitsToDeclet(int d1, int d2, int d3) {
        return switch ((d1 & 0b1000) >>> 1 | (d2 & 0b1000) >>> 2 | (d3 & 0b1000) >>> 3) {
            // table 3.4   (const 1's)
            // d1.0-d3.0   b0123456789   b0 b1 b2                         b3 b4 b5                         b7 b8 b9
            // ---------  ------------   ------------------------------   ------------------------------   ----------
            case 0b000 -> 0b0000000000 | (d1 & 0b111)              << 7 | (d2 & 0b111)              << 4 | d3 & 0b111;
            case 0b001 -> 0b0000001000 | (d1 & 0b111)              << 7 | (d2 & 0b111)              << 4 | d3 & 0b001;
            case 0b010 -> 0b0000001010 | (d1 & 0b111)              << 7 | (d3 & 0b110 | d2 & 0b001) << 4 | d3 & 0b001;
            case 0b011 -> 0b0001001110 | (d1 & 0b111)              << 7 | (d2 & 0b001)              << 4 | d3 & 0b001;
            case 0b100 -> 0b0000001100 | (d3 & 0b110 | d1 & 0b001) << 7 | (d2 & 0b111)              << 4 | d3 & 0b001;
            case 0b101 -> 0b0000101110 | (d2 & 0b110 | d1 & 0b001) << 7 | (d2 & 0b001)              << 4 | d3 & 0b001;
            case 0b110 -> 0b0000001110 | (d3 & 0b110 | d1 & 0b001) << 7 | (d2 & 0b001)              << 4 | d3 & 0b001;
            case 0b111 -> 0b0001101110 | (d1 & 0b001)              << 7 | (d2 & 0b001)              << 4 | d3 & 0b001;

            default -> throw new IllegalArgumentException("d1=" + d1 + ", d2=" + d2 + ", d3=" + d3);
        };
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
    public static int $decletToInt(int nBits) {
        //               b6 b7 b8                b3 b4
        switch ((nBits & 0b1110) << 1 | (nBits & 0b1100000) >>> 5) {
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
    public static int $decletToDigits(int nBits) {
        //               b6 b7 b8                b3 b4
        switch ((nBits & 0b1110) << 1 | (nBits & 0b1100000) >>> 5) {
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

    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public java.lang.String toString() {
        if ($isFinite()) {
            return $isZero() && $isSigned()
                    ? "-0"
                    : $toBigDecimal().stripTrailingZeros().toEngineeringString();
        }

        if ($isInfinite()) {
            return ($isSigned() ? '-' : "") + "Infinity";
        }

        return $isSignalingNaN() ? "sNaN" : "NaN";
    }
}
