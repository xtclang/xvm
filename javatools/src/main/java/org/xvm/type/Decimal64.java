package org.xvm.type;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;


/**
 * A representation of an IEEE-754-2008 64-bit decimal.
 */
public class Decimal64
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
    public Decimal64(DataInput in)
            throws IOException
        {
        m_nBits = in.readLong();
        }

    /**
     * Construct a decimal value from a Java <tt>long</tt> whose format is that of an IEEE-754-2008
     * 64-bit decimal.
     *
     * @param nBits  a 64-bit Java <tt>long</tt> containing the bits of an IEEE-754-2008 decimal
     */
    public Decimal64(long nBits)
        {
        m_nBits = nBits;
        }

    /**
     * Construct a decimal value from a byte array.
     *
     * @param abValue  a byte array containing a 64-bit Decimal
     */
    public Decimal64(byte[] abValue)
        {
        if (abValue == null)
            {
            throw new IllegalArgumentException("value required");
            }

        if (abValue.length != 8)
            {
            throw new IllegalArgumentException("byte count != 8 (actual=" + abValue.length + ")");
            }

        int MSB = (abValue[0] & 0xFF) << 24
                | (abValue[1] & 0xFF) << 16
                | (abValue[2] & 0xFF) <<  8
                | (abValue[3] & 0xFF);
        int LSB = (abValue[4] & 0xFF) << 24
                | (abValue[5] & 0xFF) << 16
                | (abValue[6] & 0xFF) <<  8
                | (abValue[7] & 0xFF);
        m_nBits = ((long) MSB) << 32 | LSB & 0xFFFFFFFFL;
        }

    /**
     * Construct a 64-bit IEEE-754-2008 decimal value from a BigDecimal.
     *
     * @param dec  a BigDecimal value
     *
     * @throws RangeException if the BigDecimal is out of range
     */
    public Decimal64(BigDecimal dec)
        {
        if (dec == null)
            {
            throw new IllegalArgumentException("value required");
            }

        m_nBits = toLongBits(dec);
        m_dec   = dec;

        assert dec.equals(toBigDecimal(m_nBits)); // TODO remove this eventually
        }


    // ----- accessors -----------------------------------------------------------------------------

    @Override
    public int getByteLength()
        {
        return 8;
        }

    @Override
    public MathContext getMathContext()
        {
        return MathContext.DECIMAL64;
        }

    @Override
    public int getByte(int i)
        {
        if ((i & ~0x7) != 0)
            {
            throw new IllegalArgumentException("index out of range: " + i);
            }

        return ((int) (m_nBits >>> (i*8))) & 0xFF;
        }

    @Override
    protected int leftmost7Bits()
        {
        return (int) (m_nBits >>> 57);
        }

    @Override
    public boolean isZero()
        {
        // G0 and G1 must not both be 1, and G2-G4 must be 0, and T (rightmost 50 bits) must be 0
        return (leftmost7Bits() & 0b0110000) != 0b0110000 && (m_nBits & 0x1C03FFFFFFFFFFFFL) == 0;
        }

    @Override
    public void writeBytes(DataOutput out)
            throws IOException
        {
        out.writeLong(m_nBits);
        }

    /**
     * @return the significand of the decimal as a Java <tt>long</tt>
     */
    public long getSignificand()
        {
        long nBits = ensureFiniteBits(m_nBits);
        int  nToG4 = (int) (nBits >>> G4_SHIFT);
        long nSig  = (nToG4 & 0b011000) == 0b011000
                ? (nToG4 & 0b000001) + 8
                : (nToG4 & 0b000111);

        // unpack the digits from most significant declet to least significant declet
        for (int cShift = 40; cShift >= 0; cShift -= 10)
            {
            nSig = nSig * 1000 + decletToInt((int) (nBits >>> cShift));
            }
        return nSig;
        }

    /**
     * @return the exponent of the decimal as a Java <tt>int</tt>
     */
    public int getExponent()
        {
        // combination field is 13 bits (from bit 50 to bit 62), including 8 "pure" exponent bits
        int nCombo = (int) (ensureFiniteBits(m_nBits) >>> 50);
        int nExp   = (nCombo & 0b01100000000000) == 0b01100000000000
                ? (nCombo & 0b00011000000000) >>> 1
                : (nCombo & 0b01100000000000) >>> 3;

        // pull the rest of the exponent bits out of "pure" exponent section of the combo bits
        // section, and unbias the exponent
        return (nExp | nCombo & 0xFF) - 398;
        }


    // ----- conversions ---------------------------------------------------------------------------

    /**
     * Obtain the decimal value as a Java <tt>long</tt> whose format is that of an IEEE-754-2008
     * 64-bit decimal.
     *
     * @return a 64-bit Java <tt>long</tt> containing the bits of an IEEE-754-2008 decimal
     */
    public long toLongBits()
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
            return new Decimal64(big);
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
        long   nBits = m_nBits;
        int    MSB   = (int) (nBits >>> 32);
        int    LSB   = (int) nBits;
        byte[] ab    = new byte[8];

        ab[0] = (byte) (MSB >>> 24);
        ab[1] = (byte) (MSB >>> 16);
        ab[2] = (byte) (MSB >>>  8);
        ab[3] = (byte) (MSB       );
        ab[4] = (byte) (LSB >>> 24);
        ab[5] = (byte) (LSB >>> 16);
        ab[6] = (byte) (LSB >>>  8);
        ab[7] = (byte) (LSB       );

        return ab;
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        return ((int) (m_nBits >>> 32)) ^ (int) m_nBits;
        }

    @Override
    public boolean equals(Object obj)
        {
        return obj instanceof Decimal64 that && this.m_nBits == that.m_nBits;
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
    public static long ensureFiniteBits(long nBits)
        {
        if ((nBits & G0_G3_MASK) == G0_G3_MASK)
            {
            throw new NumberFormatException("Not a finite value");
            }
        return nBits;
        }

    /**
     * Convert a Java BigDecimal to an IEEE 754 64-bit decimal.
     *
     * @param dec  a Java BigDecimal value
     *
     * @return a Java <tt>long</tt> that contains a 64-bit IEEE 754 decimal value
     *
     * @throws RangeException if the value is out of range
     */
    public static long toLongBits(BigDecimal dec)
        {
        dec = dec.round(MathContext.DECIMAL64);

        // obtain the significand
        long nSig = dec.unscaledValue().longValueExact();
        if (nSig < -9999999999999999L || nSig > 9999999999999999L)
            {
            throw new RangeException("significand is >16 digits: " + nSig,
                    nSig > 0 ? POS_INFINITY : NEG_INFINITY);
            }

        // bias the exponent (the scale is basically a negative exponent)
        int nExp = 398 - dec.scale();
        if (nExp < 0 || nExp >= 768)
            {
            throw new RangeException("biased exponent is out of range [0,768): " + nExp,
                    nSig > 0
                        ? nExp > 0
                            ? POS_INFINITY
                            : POS_ZERO
                        : nExp > 0
                            ? NEG_INFINITY
                            : NEG_ZERO);
            }

        long nBits = 0;
        if (nSig < 0)
            {
            nBits = SIGN_BIT;
            nSig  = -nSig;
            }

        // store the least significant 8 bits of the exponent into the combo field starting at G5
        // store the least significant 15 decimal digits of the significand in 5 10-bit declets in T
        int nLeft  = (int) (nSig / 1_000_000_000L);
        int nRight = (int) (nSig % 1_000_000_000L);
        nBits |=   (((long) (nExp & 0xFF)                         ) << 50)
                 | (((long) intToDeclet(nLeft  /     1_000 % 1000)) << 40)
                 | (((long) intToDeclet(nLeft              % 1000)) << 30)
                 | (((long) intToDeclet(nRight / 1_000_000 % 1000)) << 20)
                 | (((long) intToDeclet(nRight /     1_000 % 1000)) << 10)
                 | (((long) intToDeclet(nRight             % 1000))      );

        // remaining significand of 8 or 9 is stored in G4 as 0 or 1, with remaining exponent stored
        // in G2-G3, and G0-G1 both set to 1; otherwise, remaining significand (3 bits) is stored in
        // G2-G4 with remaining exponent stored in G0-G1
        int nSigRem = nLeft / 1_000_000;
        int nGBits  = nSigRem >= 8                              // G01234
                ? (0b11000 | (nSigRem & 0b00001) | ((nExp & 0b11000_00000) >>> 7))
                : (          (nSigRem & 0b00111) | ((nExp & 0b11000_00000) >>> 5));

        return nBits | ((long) nGBits) << G4_SHIFT;
        }

    /**
     * Convert the bits of an IEEE 754 decimal to a Java BigDecimal.
     *
     * @param nBits  a 64-bit value containing an IEEE 754 decimal
     *
     * @return a Java BigDecimal
     */
    public static BigDecimal toBigDecimal(long nBits)
        {
        ensureFiniteBits(nBits);

        // combination field is 13 bits (from bit 50 to bit 62), including 8 "pure" exponent bits
        int  nCombo = (int) (nBits >>> 50);
        int  nExp   = nCombo & 0xFF;
        long nSig;

        // test G0 and G1
        if ((nCombo & 0b0_11000_00000000) == 0b0_11000_00000000)
            {
            // when the most significant five bits of G are 110xx or 1110x, the leading significand
            // digit d0 is 8+G4, a value 8 or 9, and the leading biased exponent bits are 2*G2 + G3,
            // a value of 0, 1, or 2
            nExp |= ((nCombo & 0b0_00110_00000000) >>> 1);   // shift right 9, but then shift left 8
            nSig  = ((nCombo & 0b0_00001_00000000) >>> 8) + 8;
            }
        else
            {
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
        nSig = (((((nSig * 1000 + decletToInt((int) (nBits >>> 40)))
                         * 1000 + decletToInt((int) (nBits >>> 30)))
                         * 1000 + decletToInt((int) (nBits >>> 20)))
                         * 1000 + decletToInt((int) (nBits >>> 10)))
                         * 1000 + decletToInt((int) (nBits       )))
                         * (((nBits & SIGN_BIT) >> 63) | 1);            // apply sign

        return new BigDecimal(BigInteger.valueOf(nSig), -nExp, MathContext.DECIMAL64);
        }


    // ----- constants -----------------------------------------------------------------------------

    /**
     * The sign bit for a 64-bit IEEE 754 decimal.
     */
    private static final long       SIGN_BIT        = 1L << 63;

    /**
     * The amount to shift the G3 bit of a 64-bit IEEE 754 decimal.
     */
    private static final int        G3_SHIFT        = 59;

    /**
     * The bit mask for the G0-G3 bits of a 64-bit IEEE 754 decimal.
     */
    private static final long       G0_G3_MASK      = 0b1111L << G3_SHIFT;

    /**
     * The amount to shift the G4 bit of a 64-bit IEEE 754 decimal.
     */
    private static final int        G4_SHIFT        = 58;

    /**
     * The value for the G0-G4 bits of a 64-bit IEEE 754 decimal that indicate that the decimal
     * value is "Not a Number" (NaN).
     */
    private static final long       G0_G4_NAN       = 0b11111L << G4_SHIFT;

    /**
     * The value for the G0-G4 bits of a 64-bit IEEE 754 decimal that indicate that the decimal
     * value is infinite.
     */
    private static final long       G0_G4_INF       = 0b11110L << G4_SHIFT;

    /**
     * The amount to shift the G5 bit of a 64-bit IEEE 754 decimal.
     */
    private static final int        G5_SHIFT        = 57;

    /**
     * The value of the G5 bit that indicates that a 64-bit IEEE 754 decimal is a signaling NaN, if
     * the decimal is a NaN.
     */
    private static final long       G5_SIGNAL       = 1L << G5_SHIFT;

    /**
     * The decimal value for zero.
     */
    public static final Decimal64   POS_ZERO        = new Decimal64(0x2238000000000000L);

    /**
     * The decimal value for negative zero.
     */
    public static final Decimal64   NEG_ZERO        = new Decimal64(0xA238000000000000L);

    /**
     * The decimal value for positive one (1).
     */
    public static final Decimal64   POS_ONE         = new Decimal64(0x2238000000000001L);

    /**
     * The decimal value for negative one (-1).
     */
    public static final Decimal64   NEG_ONE         = new Decimal64(0xA238000000000001L);

    /**
     * The decimal value for a "quiet" Not-A-Number (NaN).
     */
    public static final Decimal64   NaN             = new Decimal64(G0_G4_NAN);

    /**
     * The decimal value for a signaling Not-A-Number (NaN).
     */
    public static final Decimal64   SNaN            = new Decimal64(G0_G4_NAN | G5_SIGNAL);

    /**
     * The decimal value for positive infinity.
     */
    public static final Decimal64   POS_INFINITY    = new Decimal64(G0_G4_INF);

    /**
     * The decimal value for negative infinity.
     */
    public static final Decimal64   NEG_INFINITY    = new Decimal64(SIGN_BIT | G0_G4_INF);


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The bits of the decimal value.
     */
    private final long m_nBits;

    /**
     * A cached BigDecimal value.
     */
    private transient BigDecimal m_dec;
    }