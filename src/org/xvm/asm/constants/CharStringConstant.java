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
public class CharStringConstant
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
    public CharStringConstant(ConstantPool pool, Format format, DataInput in)
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
    public CharStringConstant(ConstantPool pool, String sVal)
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

    /**
     * For a sub-class of CharStringConstant that has not yet been resolved, this allows the value
     * to be resolved.
     *
     * @param value  the value to resolve the constant to
     */
    protected void resolve(String value)
        {
        if (m_sVal == UNRESOLVED && this.getClass() != CharStringConstant.class)
            {
            m_sVal = value;
            }
        else
            {
            throw new IllegalStateException();
            }
        }

    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.String;
        }

    @Override
    public Object getLocator()
        {
        return m_sVal;
        }

    @Override
    protected int compareDetails(Constant that)
        {
        return this.m_sVal.compareTo(((CharStringConstant) that).m_sVal);
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
     * A special value that means that the constant has not been resolved.
     */
    protected static final String UNRESOLVED = "<unresolved-name>";

    /**
     * The constant character string value.
     */
    private String m_sVal;
    }
