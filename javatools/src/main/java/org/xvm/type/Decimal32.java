package org.xvm.type;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;


/**
 * A representation of an IEEE-754-2008 32-bit decimal.
 */
public class Decimal32
        extends Decimal
    {
    // ----- constructors --------------------------------------------------------------------------

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
        if (abValue == null)
            {
            throw new IllegalArgumentException("value required");
            }

        if (abValue.length != 4)
            {
            throw new IllegalArgumentException("byte count != 4 (actual=" + abValue.length + ")");
            }

        m_nBits = (abValue[0] & 0xFF) << 24
                | (abValue[1] & 0xFF) << 16
                | (abValue[2] & 0xFF) <<  8
                | (abValue[3] & 0xFF);
        }

    /**
     * Construct a 32-bit IEEE-754-2008 decimal value from a BigDecimal.
     *
     * @param dec  a BigDecimal value
     */
    public Decimal32(BigDecimal dec)
        {
        if (dec == null)
            {
            throw new IllegalArgumentException("value required");
            }

        m_nBits = toIntBits(dec);
        }


    // ----- accessors -----------------------------------------------------------------------------

    @Override
    public int getByteLength()
        {
        return 4;
        }

    @Override
    public MathContext getMathContext()
        {
        return MathContext.DECIMAL32;
        }

    @Override
    public int getByte(int i)
        {
        if ((i & ~0x3) != 0)
            {
            throw new IllegalArgumentException("index out of range: " + i);
            }

        return (m_nBits >>> (i*8)) & 0xFF;
        }

    @Override
    protected int leftmost7Bits()
        {
        return m_nBits >>> 25;
        }

    @Override
    public boolean isZero()
        {
        // G0 and G1 must not both be 1, and G2-G4 must be 0, and T (rightmost 20 bits) must be 0
        return (leftmost7Bits() & 0b0110000) != 0b0110000 && (m_nBits & 0b00011100000011111111111111111111) == 0;
        }

    @Override
    public void writeBytes(DataOutput out) throws IOException
        {
        out.writeInt(m_nBits);
        }

    /**
     * @return the significand of the decimal as an int
     */
    public int getSignificand()
        {
        int nBits = ensureFiniteBits(m_nBits);
        int nToG4 = nBits >>> G4_SHIFT;
        int nSig  = (nToG4 & 0b011000) == 0b011000
                ? (nToG4 & 0b000001) + 8
                : (nToG4 & 0b000111);

        // unpack the digits from most significant declet to least significant declet
        nSig = nSig * 1000 + decletToInt(nBits >>> 10);
        return nSig * 1000 + decletToInt(nBits);
        }

    /**
     * @return the exponent of the decimal as an int
     */
    public int getExponent()
        {
        // combination field is 11 bits (from bit 20 to bit 30), including 6 "pure" exponent bits
        int nCombo = ensureFiniteBits(m_nBits) >>> 20;
        int nExp   = (nCombo & 0b011000000000) == 0b011000000000
                ? (nCombo & 0b000110000000) >>> 1
                : (nCombo & 0b011000000000) >>> 3;

        // pull the rest of the exponent bits out of "pure" exponent section of the combo bits
        // section, and unbias the exponent
        return (nExp | nCombo & 0b111111) - 101;
        }


    // ----- conversions ---------------------------------------------------------------------------

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

    @Override
    public BigDecimal toBigDecimal()
        {
        BigDecimal dec = m_dec;
        if (dec == null && isFinite())
            {
            m_dec = dec = toBigDecimal(m_nBits);
            }
        return dec;
        }

    @Override
    public Decimal fromBigDecimal(BigDecimal big)
        {
        try
            {
            return new Decimal32(big);
            }
        catch (RangeException e)
            {
            return e.getDecimal();
            }
        }

    @Override
    public Decimal infinity(boolean fSigned)
        {
        return fSigned ? NEG_INFINITY : POS_INFINITY;
        }

    @Override
    public Decimal zero(boolean fSigned)
        {
        return fSigned ? NEG_ZERO : POS_ZERO;
        }

    @Override
    public Decimal nan()
        {
        return NaN;
        }

    @Override
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


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        return m_nBits;
        }

    @Override
    public boolean equals(Object obj)
        {
        return obj instanceof Decimal32 that && this.m_nBits == that.m_nBits;
        }


    // ----- helpers -------------------------------------------------------------------------------

    /**
     * Test the passed bits to ensure that they are finite; if they are not, throw an exception.
     *
     * @param nBits  the 32-bit IEEE-754-2008 decimal value
     *
     * @return a finite 32-bit IEEE-754-2008 decimal value
     *
     * @throws NumberFormatException if the decimal is either a NaN or an Infinity value
     */
    public static int ensureFiniteBits(int nBits)
        {
        if ((nBits & G0_G3_MASK) == G0_G3_MASK)
            {
            throw new NumberFormatException("Not a finite value");
            }
        return nBits;
        }

    /**
     * Convert a Java BigDecimal to an IEEE 754 32-bit decimal.
     *
     * @param dec  a Java BigDecimal value
     *
     * @return a Java <tt>int</tt> that contains a 32-bit IEEE 754 decimal value
     *
     * @throws ArithmeticException if the value is out of range
     */
    public static int toIntBits(BigDecimal dec)
        {
        dec = dec.round(MathContext.DECIMAL32);

        // obtain the significand
        int nSig = dec.unscaledValue().intValueExact();
        if (nSig < -9999999 || nSig > 9999999)
            {
            throw new ArithmeticException("significand is >7 digits: " + nSig);
            }

        int nBits = 0;
        if (nSig < 0)
            {
            nBits = SIGN_BIT;
            nSig  = -nSig;
            }

        // bias the exponent (the scale is basically a negative exponent)
        int nExp = 101 - dec.scale();
        if (nExp < 0 || nExp >= 192)
            {
            throw new ArithmeticException("biased exponent is out of range [0,192): " + nExp);
            }

        // store the least significant 6 bits of the exponent into the combo field starting at G5
        // store the least significant 6 decimal digits of the significand in two 10-bit declets in T
        nBits |=  ((nExp & 0b111111              ) << 20)
                | (intToDeclet(nSig / 1000 % 1000) << 10)
                | (intToDeclet(nSig        % 1000)      );

        // remaining significand of 8 or 9 is stored in G4 as 0 or 1, with remaining exponent stored
        // in G2-G3, and G0-G1 both set to 1; otherwise, remaining significand (3 bits) is stored in
        // G2-G4 with remaining exponent stored in G0-G1
        nSig  /= 1000000;
        nBits |= nSig >= 8
                ? (0b11000 | (nSig & 0b00001) | ((nExp & 0b11000000) >>> 5)) << 26
                : (          (nSig & 0b00111) | ((nExp & 0b11000000) >>> 3)) << 26;

        return nBits;
        }

    /**
     * Convert the bits of an IEEE 754 decimal to a Java BigDecimal.
     *
     * @param nBits  a 32-bit value containing an IEEE 754 decimal
     *
     * @return a Java BigDecimal
     */
    public static BigDecimal toBigDecimal(int nBits)
        {
        ensureFiniteBits(nBits);

        // combination field is 11 bits (from bit 20 to bit 30), including 6 "pure" exponent bits
        int nCombo = nBits >>> 20;
        int nExp   = nCombo & 0b111111;
        int nSig;

        // test G0 and G1
        if ((nCombo & 0b011000000000) == 0b011000000000)
            {
            // when the most significant five bits of G are 110xx or 1110x, the leading significand
            // digit d0 is 8+G4, a value 8 or 9, and the leading biased exponent bits are 2*G2 + G3,
            // a value of 0, 1, or 2
            nExp |= ((nCombo & 0b000110000000) >>> 1);    // shift right 7, but then shift left 6
            nSig  = ((nCombo & 0b000001000000) >>> 6) + 8;
            }
        else
            {
            // when the most significant five bits of G are 0xxxx or 10xxx, the leading significand
            // digit d0 is 4*G2 + 2*G3 + G4, a value in the range 0 through 7, and the leading
            // biased exponent bits are 2*G0 + G1, a value 0, 1, or 2; consequently if T is 0 and
            // the most significant five bits of G are 00000, 01000, or 10000, then the value is 0:
            //      v = (−1) S * (+0)
            nExp |= (nCombo & 0b011000000000) >>> 3;    // shift right 9, but then shift left 6
            nSig  = (nCombo & 0b000111000000) >>> 6;
            }

        // unbias the exponent
        nExp -= 101;

        // unpack the digits from most significant declet to least significant declet
        nSig = ((nSig * 1000 + decletToInt(nBits >>> 10))
                      * 1000 + decletToInt(nBits       ))
                      * (((nBits & SIGN_BIT) >> 31) | 1);       // apply sign

        return new BigDecimal(BigInteger.valueOf(nSig), -nExp, MathContext.DECIMAL32);
        }


    // ----- constants -----------------------------------------------------------------------------

    /**
     * The sign bit for a 32-bit IEEE 754 decimal.
     */
    private static final int      SIGN_BIT     = 0x80000000;

    /**
     * The amount to shift the G3 bit of a 32-bit IEEE 754 decimal.
     */
    private static final int      G3_SHIFT     = 27;

    /**
     * The bit mask for the G0-G3 bits of a 32-bit IEEE 754 decimal.
     */
    private static final int      G0_G3_MASK   = 0b1111 << G3_SHIFT;

    /**
     * The amount to shift the G4 bit of a 32-bit IEEE 754 decimal.
     */
    private static final int      G4_SHIFT     = 26;

    /**
     * The value for the G0-G4 bits of a 32-bit IEEE 754 decimal that indicate that the decimal
     * value is "Not a Number" (NaN).
     */
    private static final int      G0_G4_NAN    = 0b11111 << G4_SHIFT;

    /**
     * The value for the G0-G4 bits of a 32-bit IEEE 754 decimal that indicate that the decimal
     * value is infinite.
     */
    private static final int      G0_G4_INF    = 0b11110 << G4_SHIFT;

    /**
     * The amount to shift the G5 bit of a 32-bit IEEE 754 decimal.
     */
    private static final int      G5_SHIFT     = 25;

    /**
     * The value of the G5 bit that indicates that a 32-bit IEEE 754 decimal is a signaling NaN, if
     * the decimal is a NaN.
     */
    private static final int      G5_SIGNAL    = 1 << G5_SHIFT;

    /**
     * The decimal value for zero.
     */
    public static final Decimal32 POS_ZERO     = new Decimal32(0x22500000);

    /**
     * The decimal value for negative zero.
     */
    public static final Decimal32 NEG_ZERO     = new Decimal32(0xA2500000);

    /**
     * The decimal value for positive one (1).
     */
    public static final Decimal32 POS_ONE      = new Decimal32(0x22500001);

    /**
     * The decimal value for negative one (-1).
     */
    public static final Decimal32 NEG_ONE      = new Decimal32(0xA2500001);

    /**
     * The decimal value for a "quiet" Not-A-Number (NaN).
     */
    public static final Decimal32 NaN          = new Decimal32(G0_G4_NAN);

    /**
     * The decimal value for a signaling Not-A-Number (NaN).
     */
    public static final Decimal32 SNaN         = new Decimal32(G0_G4_NAN | G5_SIGNAL);

    /**
     * The decimal value for positive infinity.
     */
    public static final Decimal32 POS_INFINITY = new Decimal32(G0_G4_INF);

    /**
     * The decimal value for negative infinity.
     */
    public static final Decimal32 NEG_INFINITY = new Decimal32(SIGN_BIT | G0_G4_INF);


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The bits of the decimal value.
     */
    private final int m_nBits;

    /**
     * A cached BigDecimal value.
     */
    private transient BigDecimal m_dec;
    }