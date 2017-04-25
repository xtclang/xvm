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
public class ByteConstant
        extends Constant
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
    public ByteConstant(ConstantPool pool, Format format, DataInput in)
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
    public ByteConstant(ConstantPool pool, int bVal)
        {
        super(pool);
        m_nVal = bVal & 0xFF;
        }


    // ----- type-specific functionality -----------------------------------------------------------

    /**
     * Get the value of the constant.
     *
     * @return  the constant's octet value as an <tt>int</tt> in the
     *          range 0 to 255
     */
    public int getValue()
        {
        return m_nVal;
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.Byte;
        }

    @Override
    protected Object getLocator()
        {
        // Byte caches all possible values
        return Byte.valueOf((byte) m_nVal);
        }

    @Override
    protected int compareDetails(Constant that)
        {
        return this.m_nVal - ((ByteConstant) that).m_nVal;
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
     * The constant octet value stored as an integer.
     */
    private final int m_nVal;
    }
