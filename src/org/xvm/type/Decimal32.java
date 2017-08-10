package org.xvm.type;


import java.io.DataInput;
import java.io.IOException;

import java.math.BigDecimal;


/**
 * A representation of an IEEE-754-2008 32-bit decimal.
 */
public class Decimal32
    {
    /**
     * Constructor used for deserialization.
     *
     * @param in  the DataInput stream to read the value from
     *
     * @throws IOException  if an issue occurs reading from the DataInput
     */
    public Decimal32(DataInput in)
            throws IOException
        {
        m_nBits = in.readInt();
        }

    /**
     * Construct a decimal value from a Java <tt>int</tt> whose format is that of an IEEE-754-2008
     * 32-bit decimal.
     *
     * @param nBits  a 32-bit Java <tt>int</tt> containing the bits of an IEEE-754-2008 decimal
     */
    public Decimal32(int nBits)
        {
        m_nBits = nBits;
        }

    /**
     * Construct a decimal value from a byte array.
     *
     * @param abValue  a byte array containing a 32-bit Decimal
     */
    public Decimal32(byte[] abValue)
        {
        m_nBits = (abValue[0] & 0xFF) << 24
                | (abValue[1] & 0xFF) << 16
                | (abValue[2] & 0xFF) <<  8
                | (abValue[3] & 0xFF);
        }

    /**
     * Construct a decimal value from a 64-bit signed integer.
     *
     * @param dec  a BigDecimal value
     */
    public Decimal32(BigDecimal dec)
        {
        m_nBits = toIntBits(dec);
        m_dec   = dec;
        }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return -1, 0, or 1, depending on if the value is less than zero, zero (regardless of sign),
     *         or greater than zero
     */
    public int getSignum()
        {
        // TODO
        return 0;
        }

    /**
     * @return true iff the sign bit is clear
     */
    public boolean isPositive()
        {
        // TODO
        return true;
        }

    /**
     * @return true iff the sign bit is set
     */
    public boolean isNegative()
        {
        // TODO
        return true;
        }

    /**
     * @return true iff the decimal is zero, including both positive and negative zero
     */
    public boolean isZero()
        {
        // TODO
        return true;
        }

    /**
     * @return true iff the value is neither an infinity nor a NaN
     */
    public boolean isFinite()
        {
        // TODO
        return true;
        }

    /**
     * @return true iff the value is an infinity value, regardless of sign
     */
    public boolean isInfinite()
        {
        // TODO
        return true;
        }

    /**
     * @return true iff the value is a NaN value, regardless of sign
     */
    public boolean isNan()
        {
        // TODO
        return true;
        }

    /**
     * @return 1 iff the sign bit is set; otherwise 0
     */
    public int getSign()
        {
        // TODO
        return 0;
        }

    public int getDigits()
        {
        // TODO
        return 0;
        }

    public int getExponent()
        {
        // TODO
        return 0;
        }


    // ----- conversions ---------------------------------------------------------------------------

    /**
     * Obtain the decimal value as a Java BigDecimal, iff the decimal is finite.
     *
     * @return a BigDecimal, or null if the decimal is not finite
     */
    public BigDecimal toBigDecimal()
        {
        BigDecimal dec = m_dec;
        if (dec == null && isFinite())
            {
            m_dec = dec = toBigDecimal(m_nBits);
            }
        return dec;
        }

    /**
     * Obtain the decimal value as a Java <tt>int</tt> whose format is that of an IEEE-754-2008
     * 32-bit decimal.
     *
     * @return a 32-bit Java <tt>int</tt> containing the bits of an IEEE-754-2008 decimal
     */
    public int toIntBits()
        {
        return m_nBits;
        }

    /**
     * Obtain the decimal value as a byte array.
     *
     * @return a byte array containing an IEEE-754-2008 32-bit decimal
     */
    public byte[] toByteArray()
        {
        int    n  = m_nBits;
        byte[] ab = new byte[4];
        ab[0] = (byte) (n >>> 24);
        ab[1] = (byte) (n >>> 16);
        ab[2] = (byte) (n >>>  8);
        ab[3] = (byte) (n       );
        return ab;
        }


    // ----- helpers -------------------------------------------------------------------------------

    /**
     * Convert the bits of an IEEE 754 decimal to a Java BigDecimal.
     *
     * @param nBits  a 32-bit value containing an IEEE 754 decimal
     *
     * @return a Java BigDecimal
     */
    public static BigDecimal toBigDecimal(int nBits)
        {
        // TODO
        return null;
        }

    // TODO probably want to move this to a common base, or Handy

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


    // ----- fields --------------------------------------------------------------------------------

    // TODO well known bit patterns

    public static final Decimal32 POS_ZERO     = new Decimal32();
    public static final Decimal32 NEG_ZERO     = new Decimal32();
    public static final Decimal32 POS_ONE      = new Decimal32();
    public static final Decimal32 NEG_ONE      = new Decimal32();
    public static final Decimal32 POS_NaN      = new Decimal32();
    public static final Decimal32 NEG_NaN      = new Decimal32();
    public static final Decimal32 POS_INFINITY = new Decimal32();
    public static final Decimal32 NEG_INFINITY = new Decimal32();

    /**
     * The bits of the decimal value.
     */
    private int m_nBits;

    /**
     * A cached BigDecimal value.
     */
    private BigDecimal m_dec;
    }



