package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;


/**
 * Represent a 32-bit binary floating point constant.
 */
public class Float32Constant
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
    public Float32Constant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool);
        m_flVal = in.readFloat();
        }

    /**
     * Construct a constant whose value is a 32-bit binary floating point.
     *
     * @param pool   the ConstantPool that will contain this Constant
     * @param flVal  the floating point value
     */
    public Float32Constant(ConstantPool pool, float flVal)
        {
        super(pool);
        m_flVal = flVal;
        }


    // ----- type-specific methods -----------------------------------------------------------------

    /**
     * Add another FloatConstant to the value of this FloatConstant.
     *
     * @param that  a Float32Constant
     *
     * @return the sum, as a Float32Constant
     */
    public Float32Constant add(Float32Constant that)
        {
        return getConstantPool().ensureFloat32Constant(this.m_flVal + that.m_flVal);
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
        return Format.Float32;
        }

    @Override
    protected Object getLocator()
        {
        return getValue();
        }

    @Override
    protected int compareDetails(Constant that)
        {
        if (!(that instanceof Float32Constant))
            {
            return -1;
            }
        return Float.compare(this.m_flVal, ((Float32Constant) that).m_flVal);
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
        out.writeFloat(m_flVal);
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


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The constant value.
     */
    private final float m_flVal;
    }
