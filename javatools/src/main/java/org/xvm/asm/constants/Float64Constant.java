package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;


/**
 * Represent a 64-bit binary floating point constant.
 */
public class Float64Constant
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
    public Float64Constant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool);
        m_dflVal = in.readDouble();
        }

    /**
     * Construct a constant whose value is a 64-bit binary floating point.
     *
     * @param pool    the ConstantPool that will contain this Constant
     * @param dflVal  the floating point value
     */
    public Float64Constant(ConstantPool pool, double dflVal)
        {
        super(pool);
        m_dflVal = dflVal;
        }


    // ----- type-specific methods -----------------------------------------------------------------

    /**
     * Add another FloatConstant to the value of this FloatConstant.
     *
     * @param that  a Float64Constant
     *
     * @return the sum, as a Float64Constant
     */
    public Float64Constant add(Float64Constant that)
        {
        return getConstantPool().ensureFloat64Constant(this.m_dflVal + that.m_dflVal);
        }


    // ----- ValueConstant methods -----------------------------------------------------------------

    /**
     * {@inheritDoc}
     * @return  the constant's value as a Java Double
     */
    @Override
    public Double getValue()
        {
        return Double.valueOf(m_dflVal);
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.Float64;
        }

    @Override
    protected Object getLocator()
        {
        return getValue();
        }

    @Override
    protected int compareDetails(Constant that)
        {
        if (!(that instanceof Float64Constant))
            {
            return -1;
            }
        return Double.compare(this.m_dflVal, ((Float64Constant) that).m_dflVal);
        }

    @Override
    public String getValueString()
        {
        return Double.toString(m_dflVal);
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        out.writeByte(getFormat().ordinal());
        out.writeDouble(m_dflVal);
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
        long l = Double.doubleToLongBits(m_dflVal);
        return ((int) l) ^ (int) (l >>> 32);
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The constant value.
     */
    private final double m_dflVal;
    }
