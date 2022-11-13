
package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.util.Hash;


/**
 * Represent a 16-bit "brain" binary floating point constant.
 */
public class BFloat16Constant
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
    public BFloat16Constant(ConstantPool pool, Format format, DataInput in)
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
    public BFloat16Constant(ConstantPool pool, float flVal)
        {
        super(pool);
        if (Float.isFinite(flVal) && !Float.isFinite(toFloat(toHalf(flVal))))
            {
            throw new IllegalArgumentException("value out of range: " + flVal);
            }
        m_flVal = flVal;
        }


    // ----- type-specific methods -----------------------------------------------------------------

    /**
     * Add another BFloatConstant to the value of this BFloatConstant.
     *
     * @param that  a BFloat16Constant
     *
     * @return the sum, as a BFloat16Constant
     */
    public BFloat16Constant add(BFloat16Constant that)
        {
        return getConstantPool().ensureBFloat16Constant(this.m_flVal + that.m_flVal);
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
        return Format.BFloat16;
        }

    @Override
    protected Object getLocator()
        {
        return getValue();
        }

    @Override
    protected int compareDetails(Constant that)
        {
        if (!(that instanceof BFloat16Constant))
            {
            return -1;
            }
        return Float.compare(this.m_flVal, ((BFloat16Constant) that).m_flVal);
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
    public int computeHashCode()
        {
        return Hash.of(m_flVal);
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
        return Float.intBitsToFloat(nHalf << 16);
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
        // this "magic number" rounds the result instead of truncating it
        flVal *= 1.001957f;

        int fbits = Float.floatToIntBits(flVal);
        return fbits >>> 16;
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The constant value, stored as a 32-bit float.
     */
    private final float m_flVal;
    }
