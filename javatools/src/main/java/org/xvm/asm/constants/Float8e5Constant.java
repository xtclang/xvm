
package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;

import org.xvm.util.Hash;


/**
 * Represent an 8-bit "FP8 E5M2" binary floating point constant.
 */
public class Float8e5Constant
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
    public Float8e5Constant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool);
        m_nBits = in.readUnsignedByte();
        }

    /**
     * Construct a constant whose value is a 16-bit binary floating point.
     *
     * @param pool   the ConstantPool that will contain this Constant
     * @param flVal  the floating point value
     */
    public Float8e5Constant(ConstantPool pool, float flVal)
        {
        super(pool);
        m_nBits = toBits(flVal);
        }


    // ----- ValueConstant methods -----------------------------------------------------------------

    /**
     * {@inheritDoc}
     * @return  the constant's value as a Java Float
     */
    @Override
    public Float getValue()
        {
        return Float.valueOf(toFloat(m_nBits));
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.Float8e5;
        }

    @Override
    protected Object getLocator()
        {
        return getValue();
        }

    @Override
    protected int compareDetails(Constant that)
        {
        if (that instanceof Float8e5Constant thatFP8)
            {
            return Float.compare(toFloat(this.m_nBits), toFloat(thatFP8.m_nBits));
            }
        else
            {
            return -1;
            }
        }

    @Override
    public String getValueString()
        {
        return Float.toString(toFloat(m_nBits));
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        out.writeByte(getFormat().ordinal());
        out.writeByte(m_nBits);
        }

    @Override
    public String getDescription()
        {
        return "value=" + getValueString();
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int computeHashCode()
        {
        return Hash.of((byte) m_nBits);
        }


    // ----- helpers -------------------------------------------------------------------------------

    /**
     * Convert an 8-bit floating point to a "full precision" 32-bit float.
     *
     * @param nBits  the 8-bit floating point value stored in a 16-bit Java int, whose bits are
     *               encoded using the FP8 E5M2 binary-radix floating point format
     *
     * @return a 32-bit float
     */
    public static float toFloat(int nBits)
        {
        switch (nBits)
            {
            case 0x00:                          // 0
                return 0.0f;
            case 0x80:                          // -0
                return -0.0f;
            case 0x7F:                          // NaN
                return Float.NaN;
            case 0xFF:                          // -NaN
                return Float.intBitsToFloat(0xFFC00000);
            case 0x7C:                          // infinity
                return Float.POSITIVE_INFINITY;
            case 0xFC:                          // -infinity
                return Float.NEGATIVE_INFINITY;
            default:
                throw new UnsupportedOperationException("TODO implement non-zero E5M2 float values");
            }
        }

    /**
     * Convert a "full precision" 32-bit float to an 8-bit FP8 E5M2 floating point value.
     *
     * @param flVal  a 32-bit float
     *
     * @return an 8-bit FP8 E5M2 floating point value stored in a Java int, whose bits are encoded
     *         using the FP8 E5M2 binary-radix floating point format
     */
    public static int toBits(float flVal)
        {
        boolean fNeg = (Float.floatToIntBits(flVal) & 0x80000000) == 0x80000000;
        if (Float.isFinite(flVal))
            {
            if (flVal == 0)
                {
                return fNeg ? 0x80 : 0x00;
                }
            else
                {
                // TODO CP implement non-zero E4M3 float values
                return 0x00;
                }
            }
        else
            {
            if (Float.isNaN(flVal))
                {
                return fNeg ? 0xFF : 0x7F;
                }
            else
                {
                assert Float.isInfinite(flVal);
                return fNeg ? 0xFC : 0x7C;
                }
            }
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The constant value, stored as a byte.
     */
    private final int m_nBits;
    }
