package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;

import static org.xvm.util.Handy.quotedString;
import static org.xvm.util.Handy.readUtf8String;
import static org.xvm.util.Handy.writeUtf8String;


/**
 * Represent an XVM char string (string of unicode characters) constant.
 */
public class StringConstant
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
    public StringConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool);
        m_sVal = readUtf8String(in);
        }

    /**
     * Construct a constant whose value is a char string.
     *
     * @param pool  the ConstantPool that will contain this Constant
     * @param sVal  the char string value
     */
    public StringConstant(ConstantPool pool, String sVal)
        {
        super(pool);

        assert sVal != null;
        m_sVal = sVal;
        }


    // ----- type-specific functionality -----------------------------------------------------------

    /**
     * Get the value of the constant.
     *
     * @return the constant's char string value as a {@code String}
     */
    public String getValue()
        {
        return m_sVal;
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.String;
        }

    @Override
    public TypeConstant getType()
        {
        return getConstantPool().typeString();
        }

    @Override
    public Object getLocator()
        {
        return m_sVal;
        }

    @Override
    protected int compareDetails(Constant that)
        {
        return this.m_sVal.compareTo(((StringConstant) that).m_sVal);
        }

    @Override
    public String getValueString()
        {
        return quotedString(m_sVal);
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        out.writeByte(getFormat().ordinal());
        writeUtf8String(out, m_sVal);
        }

    @Override
    public String getDescription()
        {
        return "char-string=" + getValueString();
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        return m_sVal.hashCode();
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The constant character string value.
     */
    private String m_sVal;
    }
