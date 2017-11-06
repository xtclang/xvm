package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;

import static org.xvm.util.Handy.appendIntAsHex;
import static org.xvm.util.Handy.quotedChar;
import static org.xvm.util.Handy.readUtf8Char;
import static org.xvm.util.Handy.writeUtf8Char;


/**
 * Represent a unicode character constant.
 */
public class CharConstant
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
    public CharConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool);
        m_chVal = readUtf8Char(in);
        }

    /**
     * Construct a constant whose value is a unicode character.
     *
     * @param pool   the ConstantPool that will contain this Constant
     * @param chVal  the unicode character value
     */
    public CharConstant(ConstantPool pool, int chVal)
        {
        super(pool);
        m_chVal = chVal;
        }


    // ----- type-specific functionality -----------------------------------------------------------

    /**
     * {@inheritDoc}
     * @return  the constant's unicode character value as a Java Integer
     */
    @Override
    public Integer getValue()
        {
        return Integer.valueOf(m_chVal);
        }

    /**
     * Add the value of a CharConstant to the value of this CharConstant to produce a new
     * StringConstant, following the same rules that the runtime Char and String classes would use.
     *
     * @param that  the CharConstant
     *
     * @return a resulting concatenated StringConstant
     */
    public StringConstant add(CharConstant that)
        {
        assert Character.isValidCodePoint(this.m_chVal);
        assert Character.isValidCodePoint(that.m_chVal);
        StringBuilder sb = new StringBuilder()
                .append((char) this.m_chVal)
                .append((char) that.m_chVal);
        return getConstantPool().ensureStringConstant(sb.toString());
        }

    /**
     * Add the value of a StringConstant to the value of this CharConstant to produce a new
     * StringConstant, following the same rules that the runtime String and char classes would use.
     *
     * @param that  the StringConstant
     *
     * @return a resulting concatenated StringConstant
     */
    public StringConstant add(StringConstant that)
        {
        assert Character.isValidCodePoint(this.m_chVal);
        StringBuilder sb = new StringBuilder()
                .append((char) this.m_chVal)
                .append(that.getValue());
        return getConstantPool().ensureStringConstant(sb.toString());
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.Char;
        }

    @Override
    public TypeConstant getType()
        {
        return getConstantPool().typeChar();
        }

    @Override
    public Object getLocator()
        {
        // Integer only guarantees that up to 0x7F is cached
        return m_chVal <= 0x7F ? Character.valueOf((char) m_chVal) : null;
        }

    @Override
    protected int compareDetails(Constant that)
        {
        int nThis = this.m_chVal;
        int nThat = ((CharConstant) that).m_chVal;
        return nThis - nThat;
        }

    @Override
    public String getValueString()
        {
        return m_chVal > 0xFFFF
                ? appendIntAsHex(new StringBuilder("\'\\U"), m_chVal).append('\'').toString()
                : quotedChar((char) m_chVal);
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        out.writeByte(getFormat().ordinal());
        writeUtf8Char(out, m_chVal);
        }

    @Override
    public String getDescription()
        {
        return "char=" + getValueString() + ", index=" + m_chVal;
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        return m_chVal;
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The constant character code-point value stored as an integer.
     */
    private final int m_chVal;
    }
