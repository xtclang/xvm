package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.util.PackedInteger;


/**
 * Represent a signed 8-bit integer constant.
 */
public class Int8Constant
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
    public Int8Constant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool);
        m_nVal = in.readByte();
        }

    /**
     * Construct a constant whose value is a signed 8-bit integer.
     *
     * @param pool  the ConstantPool that will contain this Constant
     * @param nVal  the value in the range -128 to 127
     */
    public Int8Constant(ConstantPool pool, int nVal)
        {
        super(pool);
        if (nVal < -128 || nVal > 127)
            {
            throw new IllegalArgumentException("Int8 must be in range -128..127 (n=" + nVal + ")");
            }
        m_nVal = nVal;
        }


    // ----- type-specific methods -----------------------------------------------------------------

    /**
     * Add the value of another Int8Constant to the value of this Int8Constant, resulting in a new
     * Int8Constant.
     *
     * @param that  another Int8Constant to add to this
     *
     * @return the sum
     *
     * @throws ArithmeticException  on overflow
     */
    public Int8Constant add(Int8Constant that)
        {
        int nSum = this.getValue() + that.getValue();
        if (nSum < -128 || nSum > 127)
            {
            throw new ArithmeticException("overflow");
            }

        return getConstantPool().ensureInt8Constant(nSum);
        }


    // ----- ValueConstant methods -----------------------------------------------------------------

    /**
     * {@inheritDoc}
     * @return  the constant's unsigned byte value as a Java Integer in the range -128 to 127
     */
    @Override
    public Integer getValue()
        {
        return Integer.valueOf(m_nVal);
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.Int8;
        }

    @Override
    protected Object getLocator()
        {
        // Integer caches all possible values
        return getValue();
        }

    @Override
    protected int compareDetails(Constant that)
        {
        return this.m_nVal - ((Int8Constant) that).m_nVal;
        }

    @Override
    public String getValueString()
        {
        return Integer.toString(m_nVal);
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        out.writeByte(getFormat().ordinal());
        out.writeByte(m_nVal);
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
        return m_nVal;
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The constant value in the range -128 to 127.
     */
    private final int m_nVal;
    }
