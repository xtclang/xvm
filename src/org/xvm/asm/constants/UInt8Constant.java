package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;

import static org.xvm.util.Handy.byteToHexString;


/**
 * Represent an octet (unsigned 8-bit byte) constant.
 */
public class UInt8Constant
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
    public UInt8Constant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool);
        m_nVal = in.readUnsignedByte();
        }

    /**
     * Construct a constant whose value is an octet.
     *
     * @param pool  the ConstantPool that will contain this Constant
     * @param bVal  the octet value
     */
    public UInt8Constant(ConstantPool pool, int bVal)
        {
        super(pool);
        m_nVal = bVal & 0xFF;
        }


    // ----- ValueConstant methods -----------------------------------------------------------------

    /**
     * {@inheritDoc}
     * @return  the constant's unsigned byte value as a Java Integer in the range 0-255
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
        return Format.UInt8;
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
        return this.m_nVal - ((UInt8Constant) that).m_nVal;
        }

    @Override
    public String getValueString()
        {
        return byteToHexString(m_nVal);
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
        return "byte=" + getValueString();
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        return m_nVal;
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The constant octet value stored here as an integer in the range 0-255.
     */
    private final int m_nVal;
    }
