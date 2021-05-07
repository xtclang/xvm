
package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;


/**
 * Represent a 16-bit binary floating point constant.
 */
public class Float16Constant
        extends ValueConstant
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Constructor used for deserialization.
     *
     * @param pool    the ConstantPool that will contain this Constant
     * @param format  the format of the Constant in the stream
     * @param in      the DataInput stream to read the Constant value from
     *
     * @throws IOException  if an issue occurs reading the Constant value
     */
    public Float16Constant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool);
        m_flVal = toFloat(in.readUnsignedShort());
        }

    /**
     * Construct a constant whose value is a 16-bit binary floating point.
     *
     * @param pool   the ConstantPool that will contain this Constant
     * @param flVal  the floating point value
     */
    public Float16Constant(ConstantPool pool, float flVal)
        {
        super(pool);

        float flVal16 = toFloat(toHalf(flVal));

        if (Float.isFinite(flVal) && !Float.isFinite(flVal16))
            {
            throw new IllegalArgumentException("value out of range: " + flVal);
            }
        m_flVal = flVal16;
        }


    // ----- type-specific methods -----------------------------------------------------------------

    /**
     * Add another FloatConstant to the value of this FloatConstant.
     *
     * @param that  a Float16Constant
     *
     * @return the sum, as a Float16Constant
     */
    public Float16Constant add(Float16Constant that)
        {
        return getConstantPool().ensureFloat16Constant(this.m_flVal + that.m_flVal);
        }


    // ----- ValueConstant methods -----------------------------------------------------------------

    /**
     * {@inheritDoc}
     * @return  the constant's value as a Java Float
     */
    @Override
    public Float getValue()
        {
        return Float.valueOf(m_flVal);
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.Float16;
        }

    @Override
    protected Object getLocator()
        {
        return getValue();
        }

    @Override
    protected int compareDetails(Constant that)
        {
        if (!(that instanceof Float16Constant))
            {
            return -1;
            }
        return Float.compare(this.m_flVal, ((Float16Constant) that).m_flVal);
        }

    @Override
    public String getValueString()
        {
        return Float.toString(m_flVal);
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        out.writeByte(getFormat().ordinal());
        out.writeShort(toHalf(m_flVal));
        }

    @Override
    public String getDescription()
        {
        return "value=" + getValueString();
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        return Float.floatToIntBits(m_flVal);
        }


    // ----- helpers -------------------------------------------------------------------------------

    /**
     * Convert a 16-bit "half precision" floating point to a "full precision" 32-bit float.
     *
     * @param nHalf  the 16-bit floating point value stored in a 16-bit Java int, whose bits are
     *               encoded using the IEEE-754 binary-radix floating point format
     *
     * @return a 32-bit float
     */
    public static float toFloat(int nHalf)
        {
        // notes from the IEEE-754 specification:

        // left to right bits of a binary floating point number:
        // size        bit ids       name  description
        // ----------  ------------  ----  ---------------------------
        // 1 bit                       S   sign
        // w bits      E[0]..E[w-1]    E   biased exponent
        // t=p-1 bits  d[1]..d[p-1]    T   trailing significant field

        // The range of the encoding’s biased exponent E shall include:
        // ― every integer between 1 and 2^w − 2, inclusive, to encode normal numbers
        // ― the reserved value 0 to encode ±0 and subnormal numbers
        // ― the reserved value 2w − 1 to encode +/-infinity and NaN

        // The representation r of the floating-point datum, and value v of the floating-point datum
        // represented, are inferred from the constituent fields as follows:
        // a) If E == 2^w−1 and T != 0, then r is qNaN or sNaN and v is NaN regardless of S
        // b) If E == 2^w−1 and T == 0, then r=v=(−1)^S * (+infinity)
        // c) If 1 <= E <= 2^w−2, then r is (S, (E−bias), (1 + 2^(1−p) * T))
        //    the value of the corresponding floating-point number is
        //        v = (−1)^S * 2^(E−bias) * (1 + 2^(1−p) * T)
        //    thus normal numbers have an implicit leading significand bit of 1
        // d) If E == 0 and T != 0, then r is (S, emin, (0 + 2^(1−p) * T))
        //    the value of the corresponding floating-point number is
        //        v = (−1)^S * 2^emin * (0 + 2^(1−p) * T)
        //    thus subnormal numbers have an implicit leading significand bit of 0
        // e) If E == 0 and T ==0, then r is (S, emin, 0) and v = (−1)^S * (+0)

        // parameter                                      bin16  bin32
        // --------------------------------------------   -----  -----
        // k, storage width in bits                         16     32
        // p, precision in bits                             11     24
        // emax, maximum exponent e                         15    127
        // bias, E-e                                        15    127
        // sign bit                                          1      1
        // w, exponent field width in bits                   5      8
        // t, trailing significant field width in bits      10     23

        // a quick & dirty implementation:
        // int nS = (nHalf >>> 15) & 0x1;
        // int nE = (nHalf >>> 10) & 0x1F;
        // int nT = (nHalf       ) & 0x3FF;
        //
        // nE = nE == 0x1F
        //         ? 0xFF  // it's 2^w-1; it's all 1's, so keep it all 1's for the 32-bit float
        //         : nE - 15 + 127;     // adjust the exponent from the 16-bit bias to the 32-bit bias
        //
        // // sign S is now bit 31
        // // exp E is from bit 30 to bit 23
        // // scale T by 13 binary digits (it grew from 10 to 23 bits)
        // return Float.intBitsToFloat(nS << 31 | nE << 23 | nT << 13);

        // from: https://stackoverflow.com/questions/6162651/half-precision-floating-point-in-java
        int mant = nHalf & 0x03ff;                      // 10 bits mantissa
        int exp  = nHalf & 0x7c00;                      // 5 bits exponent
        if (exp == 0x7c00)                              // NaN/Inf
            {
            exp = 0x3fc00;                              // -> NaN/Inf
            }
        else if (exp != 0)                              // normalized value
            {
            exp += 0x1c000;                             // exp - 15 + 127
            if (mant == 0 && exp > 0x1c400)             // smooth transition
                {
                return Float.intBitsToFloat((nHalf & 0x8000) << 16 | exp << 13 | 0x3ff);
                }
            }
        else if (mant != 0)                             // && exp==0 -> subnormal
            {
            exp = 0x1c400;                              // make it normal
            do
                {
                mant <<= 1;                             // mantissa * 2
                exp   -= 0x400;                         // decrease exp by 1
                }
            while ((mant & 0x400) == 0);                // while not normal
            mant &= 0x3ff;                              // discard subnormal bit
            }                                           // else +/-0 -> +/-0
        return Float.intBitsToFloat(                    // combine all parts
                (nHalf & 0x8000) << 16                  // sign  << ( 31 - 15 )
                | (exp | mant) << 13);                  // value << ( 23 - 10 )
        }

    /**
     * Convert a "full precision" 32-bit float to a 16-bit "half precision" floating point value.
     *
     * @param flVal  a 32-bit float
     *
     * @return a 16-bit floating point value stored in a 16-bit Java int, whose bits are encoded
     *         using the IEEE-754 binary-radix floating point format
     */
    public static int toHalf(float flVal)
        {
        // from: https://stackoverflow.com/questions/6162651/half-precision-floating-point-in-java
        int fbits = Float.floatToIntBits(flVal);
        int sign  = fbits >>> 16 & 0x8000;              // sign only
        int val   = (fbits & 0x7fffffff) + 0x1000;      // rounded value

        if (val >= 0x47800000)                          // might be or become NaN/Inf
            {                                           // avoid Inf due to rounding
            if ((fbits & 0x7fffffff) >= 0x47800000)     // is or must become NaN/Inf
                {
                return val < 0x7f800000                 // was value but too large
                        ? sign | 0x7c00                 // make it +/-Inf
                        : sign | 0x7c00 |               // remains +/-Inf or NaN
                          (fbits & 0x007fffff) >>> 13;  // keep NaN (and Inf) bits
                }
            return sign | 0x7bff;                       // unrounded not quite Inf
            }

        if (val >= 0x38800000)                          // remains normalized value
            {
            return sign | val - 0x38000000 >>> 13;      // exp - 127 + 15
            }

        if (val < 0x33000000)                           // too small for subnormal
            {
            return sign;                                // becomes +/-0
            }

        val = (fbits & 0x7fffffff) >>> 23;              // tmp exp for subnormal calc
        return sign | ((fbits & 0x7fffff | 0x800000)    // add subnormal bit
                + (0x800000 >>> val - 102)              // round depending on cut off
                >>> 126 - val );                        // div by 2^(1-(exp-127+15)) and >> 13 | exp=0
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The constant value, stored as a 32-bit float.
     */
    private final float m_flVal;
    }
