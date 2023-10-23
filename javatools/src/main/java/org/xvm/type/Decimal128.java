package org.xvm.type;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;


/**
 * A representation of an IEEE-754-2008 128-bit decimal.
 */
public class Decimal128
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
    public Decimal128(DataInput in)
            throws IOException
        {
        m_nHBits = in.readLong();
        m_nLBits = in.readLong();
        }

    /**
     * Construct a decimal value from two Java <tt>long</tt> value whose format is that of an
     * IEEE-754-2008 128-bit decimal.
     *
     * @param nHBits  a 64-bit Java <tt>long</tt> containing the high 64 bits of an IEEE-754-2008
     *                128-bit decimal
     * @param nLBits  a 64-bit Java <tt>long</tt> containing the low 64 bits of an IEEE-754-2008
     *                128-bit decimal
     */
    public Decimal128(long nHBits, long nLBits)
        {
        m_nHBits = nHBits;
        m_nLBits = nLBits;
        }

    /**
     * Construct a decimal value from a byte array.
     *
     * @param abValue  a byte array containing a 128-bit Decimal
     */
    public Decimal128(byte[] abValue)
        {
        if (abValue == null)
            {
            throw new IllegalArgumentException("value required");
            }

        if (abValue.length != 16)
            {
            throw new IllegalArgumentException("byte count != 16 (actual=" + abValue.length + ")");
            }

        int MSB = (abValue[ 0] & 0xFF) << 24
                | (abValue[ 1] & 0xFF) << 16
                | (abValue[ 2] & 0xFF) <<  8
                | (abValue[ 3] & 0xFF);
        int LSB = (abValue[ 4] & 0xFF) << 24
                | (abValue[ 5] & 0xFF) << 16
                | (abValue[ 6] & 0xFF) <<  8
                | (abValue[ 7] & 0xFF);
        m_nHBits = ((long) MSB) << 32 | LSB & 0xFFFFFFFFL;

        MSB =     (abValue[ 8] & 0xFF) << 24
                | (abValue[ 9] & 0xFF) << 16
                | (abValue[10] & 0xFF) <<  8
                | (abValue[11] & 0xFF);
        LSB =     (abValue[12] & 0xFF) << 24
                | (abValue[13] & 0xFF) << 16
                | (abValue[14] & 0xFF) <<  8
                | (abValue[15] & 0xFF);
        m_nLBits = ((long) MSB) << 32 | LSB & 0xFFFFFFFFL;
        }

    /**
     * Construct a 128-bit IEEE-754-2008 decimal value from a BigDecimal.
     *
     * @param dec  a BigDecimal value
     */
    public Decimal128(BigDecimal dec)
        {
        if (dec == null)
            {
            throw new IllegalArgumentException("value required");
            }

        convertBigDecToLongs(dec.round(MathContext.DECIMAL128));
        }


    // ----- accessors -----------------------------------------------------------------------------

    @Override
    public int getByteLength()
        {
        return 16;
        }

    @Override
    public MathContext getMathContext()
        {
        return MathContext.DECIMAL128;
        }

    @Override
    public int getByte(int i)
        {
        if ((i & ~0xF) != 0)
            {
            throw new IllegalArgumentException("index out of range: " + i);
            }

        long lBits = i < 8 ? m_nHBits : m_nLBits;
        return ((int) (lBits >>> ((i&7)*8))) & 0xFF;
        }

    @Override
    protected int leftmost7Bits()
        {
        return (int) (m_nHBits >>> 57);
        }

    @Override
    public boolean isZero()
        {
        // G0 and G1 must not both be 1, and G2-G4 must be 0, and T (rightmost 110 bits) must be 0
        return (leftmost7Bits() & 0b0110000) != 0b0110000 && (m_nHBits & LS46BITS) == 0 && m_nLBits == 0;
        }

    @Override
    public void writeBytes(DataOutput out) throws IOException
        {
        out.writeLong(m_nHBits);
        out.writeLong(m_nLBits);
        }

    /**
     * @return the significand of the decimal as a Java <tt>BigInteger</tt>
     */
    public BigInteger getSignificand()
        {
        long nHBits = ensureFiniteHighBits(m_nHBits);
        long nLBits = m_nLBits;

        // get the first digit (most significant digit)
        int nToG4 = (int) (nHBits >>> G4_SHIFT);
        int nD0   = (nToG4 & 0b011000) == 0b011000
                ? (nToG4 & 0b000001) + 8
                : (nToG4 & 0b000111);

        // keep only the T portion of the high bits (the low bits are all part of the T portion)
        nHBits &= LS46BITS;

        // process the remainder of the T portion in the high bits (except for the last 6 bits that
        // overflowed from the low bits)
        long nHSig = nD0;
        if (nHSig != 0 || nHBits != 0)
            {
            for (int of = 36; of >= 0; of -= 10)
                {
                nHSig = nHSig * 1000 + decletToInt((int) (nHBits >>> of));
                }
            }

        // process the T portion in the low bits (including the 6 LSBs of the high bits)
        long nLSig = 0;
        if (nHSig != 0 || nLBits != 0)
            {
            // grab the 6 bits from the 7th declet that overflowed to the "high bits" long, and
            // combine those with the highest 4 bits from the "low bits" long
            nHSig = nHSig * 1000 + decletToInt((int) ((nHBits << 4) | (nLBits >>> 60)));

            for (int of = 50; of >= 0; of -= 10)
                {
                nLSig = nLSig * 1000 + decletToInt((int) (nLBits >>> of));
                }
            }

        // put the digits from the low and high bits together to form the full significand
        BigInteger bintL = nLSig == 0 ? BIGINT_ZERO : BigInteger.valueOf(nLSig);
        return nHSig == 0 ? bintL : BigInteger.valueOf(nHSig).multiply(BIGINT_10_TO_18TH).add(bintL);
        }

    /**
     * @return the exponent of the decimal as a Java <tt>int</tt>
     */
    public int getExponent()
        {
        // combination field is 17 bits (from bit 46 to bit 62), including 12 "pure" exponent bits
        int nCombo = (int) (ensureFiniteHighBits(m_nHBits) >>> 46);
        int nExp   = (nCombo & 0b0_11000_000000000000) == 0b0_11000_000000000000
                ? (nCombo & 0b0_00110_000000000000) >>> 1
                : (nCombo & 0b0_11000_000000000000) >>> 3;

        // pull the rest of the exponent bits out of "pure" exponent section of the combo bits
        // section, and unbias the exponent
        return (nExp | nCombo & 0xFFF) - 6176;
        }


    // ----- conversions ---------------------------------------------------------------------------

    /**
     * Obtain the high 64 bits of the IEEE-754-2008 128-bit decimal.
     *
     * @return a 64-bit Java <tt>long</tt> containing the high bits of an IEEE-754-2008 decimal
     */
    public long getHighBits()
        {
        return m_nHBits;
        }

    /**
     * Obtain the low 64 bits of the IEEE-754-2008 128-bit decimal.
     *
     * @return a 64-bit Java <tt>long</tt> containing the low bits of an IEEE-754-2008 decimal
     */
    public long getLowBits()
        {
        return m_nLBits;
        }

    @Override
    public BigDecimal toBigDecimal()
        {
        BigDecimal dec = m_dec;
        if (dec == null && isFinite())
            {
            dec = new BigDecimal(getSignificand(), -getExponent(), MathContext.DECIMAL128);
            m_dec = dec = isSigned() ? dec.negate() : dec;
            }
        return dec;
        }

    @Override
    public Decimal fromBigDecimal(BigDecimal big)
        {
        try
            {
            return new Decimal128(big);
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
        byte[] ab    = new byte[16];

        long   nBits = m_nHBits;
        int    MSB   = (int) (nBits >>> 32);
        int    LSB   = (int) nBits;

        ab[0x0] = (byte) (MSB >>> 24);
        ab[0x1] = (byte) (MSB >>> 16);
        ab[0x2] = (byte) (MSB >>>  8);
        ab[0x3] = (byte) (MSB       );
        ab[0x4] = (byte) (LSB >>> 24);
        ab[0x5] = (byte) (LSB >>> 16);
        ab[0x6] = (byte) (LSB >>>  8);
        ab[0x7] = (byte) (LSB       );

        nBits = m_nLBits;
        MSB   = (int) (nBits >>> 32);
        LSB   = (int) nBits;

        ab[0x8] = (byte) (MSB >>> 24);
        ab[0x9] = (byte) (MSB >>> 16);
        ab[0xA] = (byte) (MSB >>>  8);
        ab[0xB] = (byte) (MSB       );
        ab[0xC] = (byte) (LSB >>> 24);
        ab[0xD] = (byte) (LSB >>> 16);
        ab[0xE] = (byte) (LSB >>>  8);
        ab[0xF] = (byte) (LSB       );

        return ab;
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        return ((int) (m_nHBits >>> 32)) ^ (int) m_nHBits ^ ((int) (m_nLBits >>> 32)) ^ (int) m_nLBits;
        }

    @Override
    public boolean equals(Object obj)
        {
        return obj instanceof Decimal128 that &&
            this.m_nHBits == that.m_nHBits & that.m_nLBits == that.m_nLBits;
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
    private static long ensureFiniteHighBits(long nHBits)
        {
        if ((nHBits & G0_G3_MASK) == G0_G3_MASK)
            {
            throw new NumberFormatException("Not a finite value");
            }
        return nHBits;
        }

    /**
     * Convert a Java BigDecimal to an IEEE 754 128-bit decimal.
     *
     * @throws ArithmeticException if the value is out of range
     */
    private void convertBigDecToLongs(BigDecimal dec)
        {
        // get the sign
        boolean    fNeg;
        switch (dec.signum())
            {
            case -1:
                fNeg = true;
                break;

            case 0:
                // this is cheating a little bit, but the value is zero, so just steal the bits from
                // the well-known zero value
                m_nHBits = POS_ZERO.m_nHBits;
                m_nLBits = POS_ZERO.m_nLBits;
                return;

            case 1:
                fNeg = false;
                break;

            default:
                throw new IllegalStateException();
            }

        // get the raw significand
        BigInteger bint = dec.unscaledValue().abs();
        if (bint.bitLength() > 114)
            {
            // we have 113 bits for the significand, and the bitLength of a big integer includes an
            // extra bit for a sign, so we know if the big integer needs more than 114 bits, that
            // we can't translate that into a declet-based form that uses no more than 113 bits;
            // note that we could still overflow, but we'll detect that only when we're done making
            // all of the declets (because there should only be a single decimal digit value 0..9
            // left at that point)
            throw new ArithmeticException("significand is >34 digits: " + bint);
            }

        // get the biased exponent (the scale is basically a negative exponent)
        int nExp = 6176 - dec.scale();
        if (nExp < 0 || nExp >= 12288)
            {
            throw new ArithmeticException("biased exponent is out of range [0,12288): " + nExp);
            }

        // now we're ready to produce the bits, starting with the 11 declets
        long nHBits = 0;
        long nLBits = 0;
        for (int i = 0; i < 11 && bint.signum() > 0; ++i)
            {
            BigInteger[] abintDivRem = bint.divideAndRemainder(BIGINT_THOUSAND);
            BigInteger   bintTriad   = abintDivRem[1];

            int nDeclet = intToDeclet(bintTriad.intValue());
            if (i < 6)
                {
                nLBits |= ((long) nDeclet) << (i * 10);
                }
            else if (i == 6)
                {
                // split the declet across the high and low bits
                nLBits |= ((long) nDeclet) << 60;
                nHBits  = nDeclet >>> 4;            // rightmost 4 bits are in the "low bits" long
                }
            else
                {
                // declet 7 starts at bit 6 of the "high bits" long
                nHBits |= ((long) nDeclet) << ((i-7) * 10 + 6);
                }

            bint = abintDivRem[0];
            }

        // store the least significant 12 bits of the exponent into the combo field starting at G5
        nHBits |=  (nExp & 0xFFFL) << 46;

        // get remaining significand
        int nSigRem = bint.intValueExact();
        if (nSigRem > 9)
            {
            throw new ArithmeticException("significand is >34 digits: " + dec.unscaledValue().abs());
            }

        // remaining significand of 8 or 9 is stored in G4 as 0 or 1, with remaining exponent stored
        // in G2-G3, and G0-G1 both set to 1; otherwise, remaining significand (3 bits) is stored in
        // G2-G4 with remaining exponent stored in G0-G1
        int nGBits = nSigRem >= 8                                   // G01234
                ? (0b11000 | (nSigRem & 0b00001) | ((nExp & 0b110000000_00000) >>> 11))
                : (          (nSigRem & 0b00111) | ((nExp & 0b110000000_00000) >>>  9));
        nHBits |= ((long) nGBits) << G4_SHIFT;

        // store the sign bit
        if (fNeg)
            {
            nHBits |= SIGN_BIT;
            }

        m_nHBits = nHBits;
        m_nLBits = nLBits;
        }


    // ----- constants -----------------------------------------------------------------------------

    /**
     * Zero, in a BigInteger format.
     */
    private static final BigInteger BIGINT_ZERO         = BigInteger.ZERO;

    /**
     * One thousand, in a BigInteger format.
     */
    private static final BigInteger BIGINT_THOUSAND     = BigInteger.valueOf(1000);

    /**
     * One million million (10^18), in a BigInteger format.
     */
    private static final BigInteger BIGINT_10_TO_18TH   = new BigInteger("1000000000000000000");

    /**
     * The least significant 46 bits.
     */
    private static final long       LS46BITS            = 0x3FFFFFFFFFFFL;

    /**
     * The sign bit for the high 64 bits of a 128-bit IEEE 754 decimal.
     */
    private static final long       SIGN_BIT            = 1L << 63;

    /**
     * The amount to shift the G3 bit in the high 64 bits of a 128-bit IEEE 754 decimal.
     */
    private static final int        G3_SHIFT            = 59;

    /**
     * The bit mask for the G0-G3 bits of the high 64 bits of a 128-bit IEEE 754 decimal.
     */
    private static final long       G0_G3_MASK          = 0b1111L << G3_SHIFT;

    /**
     * The amount to shift the G4 bit in the high 64 bits of a 128-bit IEEE 754 decimal
     */
    private static final int        G4_SHIFT            = 58;

    /**
     * The value for the G0-G4 bits of the high 64 bits of a 128-bit IEEE 754 decimal that indicate
     * that the decimal value is "Not a Number" (NaN).
     */
    private static final long       G0_G4_NAN           = 0b11111L << G4_SHIFT;

    /**
     * The value for the G0-G4 bits in the high 64 bits of a 128-bit IEEE 754 decimal that indicate
     * that the decimal value is infinite.
     */
    private static final long       G0_G4_INF           = 0b11110L << G4_SHIFT;

    /**
     * The amount to shift the G5 bit in the high 64 bits of a 128-bit IEEE 754 decimal.
     */
    private static final int        G5_SHIFT            = 57;

    /**
     * The value of the G5 bit in the high 64 bits of a 128-bit IEEE 754 decimal that indicates that
     * the decimal is a signaling NaN, if the decimal is a NaN.
     */
    private static final long       G5_SIGNAL           = 1L << G5_SHIFT;

    /**
     * The decimal value for zero.
     */
    public static final Decimal128  POS_ZERO            = new Decimal128(0x2208000000000000L, 0);

    /**
     * The decimal value for negative zero.
     */
    public static final Decimal128  NEG_ZERO            = new Decimal128(0xA208000000000000L, 0);

    /**
     * The decimal value for positive one (1).
     */
    public static final Decimal128  POS_ONE             = new Decimal128(0x2208000000000000L, 1);

    /**
     * The decimal value for negative one (-1).
     */
    public static final Decimal128  NEG_ONE             = new Decimal128(0xA208000000000000L, 1);

    /**
     * The decimal value for a "quiet" Not-A-Number (NaN).
     */
    public static final Decimal128  NaN                 = new Decimal128(G0_G4_NAN, 0L);

    /**
     * The decimal value for a signaling Not-A-Number (NaN).
     */
    public static final Decimal128  SNaN                = new Decimal128(G0_G4_NAN | G5_SIGNAL, 0L);

    /**
     * The decimal value for positive infinity.
     */
    public static final Decimal128  POS_INFINITY        = new Decimal128(G0_G4_INF, 0L);

    /**
     * The decimal value for negative infinity.
     */
    public static final Decimal128  NEG_INFINITY        = new Decimal128(SIGN_BIT | G0_G4_INF, 0L);


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The high bits of the decimal value.
     */
    private long m_nHBits;

    /**
     * The low bits of the decimal value.
     */
    private long m_nLBits;

    /**
     * A cached BigDecimal value.
     */
    private transient BigDecimal m_dec;
    }