package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;

import org.xvm.compiler.Token;

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
    public Constant apply(Token.Id op, Constant that)
        {
        ConstantPool pool = getConstantPool();
        switch (op.TEXT + that.getFormat().name())
            {
            case "+String":
                return pool.ensureStringConstant(this.m_sVal + ((StringConstant) that).m_sVal);

            case "+Char":
                assert Character.isValidCodePoint(((CharConstant) that).getValue());
                return pool.ensureStringConstant(
                        this.m_sVal + (char) (int) ((CharConstant) that).getValue());

            case "+IntLiteral":
            case "+FPLiteral":
                return pool.ensureStringConstant(
                        this.m_sVal + ((LiteralConstant) that).getValue());

            case "+EnumValueConst":
                return pool.ensureStringConstant(
                        this.m_sVal + ((EnumValueConstant) that).getClassConstant().getName());

            case "*IntLiteral":
            case "*Int64":
                {
                String s = m_sVal;
                int n = that.getFormat() == Format.IntLiteral
                        ? ((LiteralConstant) that).getPackedInteger().getInt()
                        : ((IntConstant) that).getValue().getInt();
                assert n >= 0 && n * s.length() < 1000000;

                StringBuilder sb = new StringBuilder(n * s.length());
                for (int i = 0; i < n; ++i)
                    {
                    sb.append(s);
                    }

                return getConstantPool().ensureStringConstant(sb.toString());
                }

            case "==String":
                return getConstantPool().valOf(this.m_sVal.equals(((StringConstant) that).m_sVal));
            case "!=String":
                return getConstantPool().valOf(!this.m_sVal.equals(((StringConstant) that).m_sVal));
            case "<String":
                return getConstantPool().valOf(this.m_sVal.compareTo(((StringConstant) that).m_sVal) < 0);
            case "<=String":
                return getConstantPool().valOf(this.m_sVal.compareTo(((StringConstant) that).m_sVal) <= 0);
            case ">String":
                return getConstantPool().valOf(this.m_sVal.compareTo(((StringConstant) that).m_sVal) > 0);
            case ">=String":
                return getConstantPool().valOf(this.m_sVal.compareTo(((StringConstant) that).m_sVal) >= 0);

            case "<=>String":
                return getConstantPool().valOrd(this.m_sVal.compareTo(((StringConstant) that).m_sVal));

            case "..String":
                return getConstantPool().ensureRangeConstant(this, that);

            default:
                return super.apply(op, that);
            }
        }

    @Override
    public Object getLocator()
        {
        return m_sVal;
        }

    @Override
    protected int compareDetails(Constant that)
        {
        if (!(that instanceof StringConstant))
            {
            return -1;
            }
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
