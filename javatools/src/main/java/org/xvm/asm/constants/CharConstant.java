package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;

import org.xvm.compiler.Token;
import org.xvm.compiler.Token.Id;

import org.xvm.util.PackedInteger;

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


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.Char;
        }

    @Override
    public PackedInteger getIntValue()
        {
        return PackedInteger.valueOf(m_chVal);
        }

    @Override
    public TypeConstant resultType(Id op, Constant that)
        {
        ConstantPool pool = getConstantPool();
        switch (op.TEXT + that.getFormat().name())
            {
            case "+String":
            case "+Char":
                // char+char => str
                // char+str  => str
            case "*IntLiteral":
            case "*Int64":
                return pool.typeString();

            case "..Char":
                return pool.ensureRangeType(pool.typeChar());
            }

        return super.resultType(op, that);
        }

    @Override
    public Constant apply(Token.Id op, Constant that)
        {
        switch (op.TEXT + that.getFormat().name())
            {
            case "+String":
                {
                assert Character.isValidCodePoint(this.m_chVal);
                StringBuilder sb = new StringBuilder()
                        .append((char) this.m_chVal)
                        .append(((StringConstant) that).getValue());
                return getConstantPool().ensureStringConstant(sb.toString());
                }

            case "+Char":
                {
                assert Character.isValidCodePoint(this.m_chVal);
                assert Character.isValidCodePoint(((CharConstant) that).m_chVal);
                char chThis = (char) this.m_chVal;
                char chThat = (char) ((CharConstant) that).m_chVal;
                return getConstantPool().ensureStringConstant(String.valueOf(chThis) + chThat);
                }

            case "-Char":
                {
                assert Character.isValidCodePoint(this.m_chVal);
                assert Character.isValidCodePoint(((CharConstant) that).m_chVal);
                char chThis = (char) this.m_chVal;
                char chThat = (char) ((CharConstant) that).m_chVal;
                return getConstantPool().ensureIntConstant(chThis - chThat);
                }

            case "*IntLiteral":
            case "*Int64":
                {
                assert Character.isValidCodePoint(this.m_chVal);
                char ch = (char) this.m_chVal;

                int n = that.getFormat() == Format.IntLiteral
                        ? ((LiteralConstant) that).getPackedInteger().getInt()
                        : ((IntConstant) that).getValue().getInt();
                assert n >= 0 && n < 1000000;

                char[] ach = new char[n];
                for (int i = 0; i < n; ++i)
                    {
                    ach[i] = ch;
                    }

                return getConstantPool().ensureStringConstant(new String(ach));
                }

            // these are "fake" i.e. compile-time only in order to support calculations resulting
            // from the use of Range in ForEachStatement
            case "+IntLiteral":
            case "-IntLiteral":
                {
                int delta = ((LiteralConstant) that).toIntConstant(Format.Int32).getIntValue().getInt();
                if (op == Id.SUB)
                    {
                    delta = -delta;
                    }

                return getConstantPool().ensureCharConstant(m_chVal + delta);
                }

            case "==Char":
                return getConstantPool().valOf(this.m_chVal == ((CharConstant) that).m_chVal);
            case "!=Char":
                return getConstantPool().valOf(this.m_chVal != ((CharConstant) that).m_chVal);
            case "<Char":
                return getConstantPool().valOf(this.m_chVal < ((CharConstant) that).m_chVal);
            case "<=Char":
                return getConstantPool().valOf(this.m_chVal <= ((CharConstant) that).m_chVal);
            case ">Char":
                return getConstantPool().valOf(this.m_chVal > ((CharConstant) that).m_chVal);
            case ">=Char":
                return getConstantPool().valOf(this.m_chVal >= ((CharConstant) that).m_chVal);

            case "<=>Char":
                return getConstantPool().valOrd(this.m_chVal - ((CharConstant) that).m_chVal);

            case "..Char":
                return getConstantPool().ensureRangeConstant(this, that);
            }

        return super.apply(op, that);
        }

    @Override
    public Constant convertTo(TypeConstant typeOut)
        {
        switch (typeOut.getEcstasyClassName())
            {
            case "text.String":
                {
                int ch = m_chVal;
                if (ch >= Character.MIN_VALUE && ch <= Character.MAX_VALUE)
                    {
                    return getConstantPool().
                        ensureStringConstant(Character.valueOf((char) ch).toString());
                    }
                }
            }

        return super.convertTo(typeOut);
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
        if (!(that instanceof CharConstant))
            {
            return -1;
            }
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
