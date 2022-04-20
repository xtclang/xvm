package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;

import static org.xvm.util.Handy.byteArrayToHexString;


/**
 * Represent a 128-bit binary floating point constant.
 */
public class Float128Constant
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
    public Float128Constant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool);

        byte[] ab = new byte[16];
        in.readFully(ab);
        m_abVal = ab;
        }

    /**
     * Construct a constant whose value is a 126-bit binary floating point.
     *
     * @param pool   the ConstantPool that will contain this Constant
     * @param abVal  the floating point value, provided as an array of 16 bytes
     */
    public Float128Constant(ConstantPool pool, byte[] abVal)
        {
        super(pool);
        if (abVal == null || abVal.length != 16)
            {
            throw new IllegalArgumentException("Float128Constant requires an array of 16 bytes");
            }
        m_abVal = abVal;
        }


    // ----- type-specific methods -----------------------------------------------------------------

    /**
     * Add another FloatConstant to the value of this FloatConstant.
     *
     * @param that  a Float128Constant
     *
     * @return the sum, as a Float128Constant
     */
    public Float128Constant add(Float128Constant that)
        {
        throw new UnsupportedOperationException("(unsupported)");
        }


    // ----- ValueConstant methods -----------------------------------------------------------------

    /**
     * {@inheritDoc}
     * @return  the constant's value as a byte array, which must be treated as an immutable
     */
    @Override
    public byte[] getValue()
        {
        return m_abVal;
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.Float128;
        }

    @Override
    protected Object getLocator()
        {
        return getValue();
        }

    @Override
    protected int compareDetails(Constant that)
        {
        if (!(that instanceof Float128Constant))
            {
            return -1;
            }
        byte[] abThis = this.m_abVal;
        byte[] abThat = ((Float128Constant) that).m_abVal;

        for (int of = 0; of < 16; ++of)
            {
            if (abThis[of] != abThat[of])
                {
                return (abThis[of] & 0xFF) - (abThat[of] & 0xFF);
                }
            }
        return 0;
        }

    @Override
    public String getValueString()
        {
        // TODO format a 128-bit binary floating point value into a string
        return "(unsupported)";
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        out.writeByte(getFormat().ordinal());
        out.write(m_abVal);
        }

    @Override
    public String getDescription()
        {
        return "bytes=" + byteArrayToHexString(m_abVal);
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        int    nHash = 0;
        byte[] ab    = m_abVal;
        for (int of = 0; of < 16; ++of)
            {
            nHash *= 19 + ab[of];
            }
        return nHash;
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The constant value.
     */
    private final byte[] m_abVal;
    }