package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;

import org.xvm.compiler.Token;
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
     * Create a new Int8Constant with the specified value, but only if it is in the legal range.
     *
     * @param n  an integer value
     *
     * @return the corresponding Int8Constant
     *
     * @throws ArithmeticException  if the value is out of range
     */
    public Int8Constant validate(int n)
        {
        if (n < -128 || n > 127)
            {
            throw new ArithmeticException("overflow");
            }

        return getConstantPool().ensureInt8Constant(n);
        }

    static int nonzero(int n)
        {
        if (n == 0)
            {
            throw new ArithmeticException("zero");
            }

        return n;
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
    public PackedInteger getIntValue()
        {
        return PackedInteger.valueOf(m_nVal);
        }

    @Override
    public Constant apply(Token.Id op, Constant that)
        {
        switch (that == null
                    ? op.TEXT + this.getFormat().name()
                    : this.getFormat().name() + op.TEXT + that.getFormat().name())
            {
            case "-Int8":
                return validate(-this.m_nVal);

            case "~Int8":
                return validate(~this.m_nVal);

            case "Int8+IntLiteral":
            case "Int8-IntLiteral":
            case "Int8*IntLiteral":
            case "Int8/IntLiteral":
            case "Int8%IntLiteral":
            case "Int8&IntLiteral":
            case "Int8|IntLiteral":
            case "Int8^IntLiteral":
            case "Int8..IntLiteral":
            case "Int8..<IntLiteral":
            case "Int8==IntLiteral":
            case "Int8!=IntLiteral":
            case "Int8<IntLiteral":
            case "Int8<=IntLiteral":
            case "Int8>IntLiteral":
            case "Int8>=IntLiteral":
            case "Int8<=>IntLiteral":
                return apply(op, ((LiteralConstant) that).toInt8Constant());

            case "Int8<<IntLiteral":
            case "Int8>>IntLiteral":
            case "Int8>>>IntLiteral":
                return apply(op, ((LiteralConstant) that).toIntConstant(Format.Int64));

            case "Int8+Int8":
                return validate(this.m_nVal + ((Int8Constant) that).m_nVal);
            case "Int8-Int8":
                return validate(this.m_nVal - ((Int8Constant) that).m_nVal);
            case "Int8*Int8":
                return validate(this.m_nVal * ((Int8Constant) that).m_nVal);
            case "Int8/Int8":
                return validate(this.m_nVal / nonzero(((Int8Constant) that).m_nVal));
            case "Int8%Int8":
                int nDivisor = nonzero(((Int8Constant) that).m_nVal);
                int nModulo  = this.m_nVal % nDivisor;
                return validate(nModulo < 0 ? nModulo + nDivisor : nModulo);
            case "Int8&Int8":
                return validate(this.m_nVal & ((Int8Constant) that).m_nVal);
            case "Int8|Int8":
                return validate(this.m_nVal | ((Int8Constant) that).m_nVal);
            case "Int8^Int8":
                return validate(this.m_nVal ^ ((Int8Constant) that).m_nVal);
            case "Int8..Int8":
                return ConstantPool.getCurrentPool().ensureIntervalConstant(this, that);
            case "Int8..<Int8":
                return ConstantPool.getCurrentPool().ensureIntervalConstant(this, false, that, true);

            case "Int8<<Int64":
                return validate(this.m_nVal << ((IntConstant) that).getValue().and(new PackedInteger(7)).getInt());
            case "Int8>>Int64":
                return validate(this.m_nVal >> ((IntConstant) that).getValue().and(new PackedInteger(7)).getInt());
            case "Int8>>>Int64":
                return validate(this.m_nVal >>> ((IntConstant) that).getValue().and(new PackedInteger(7)).getInt());

            case "Int8==Int8":
                return getConstantPool().valOf(this.m_nVal == ((Int8Constant) that).m_nVal);
            case "Int8!=Int8":
                return getConstantPool().valOf(this.m_nVal != ((Int8Constant) that).m_nVal);
            case "Int8<Int8":
                return getConstantPool().valOf(this.m_nVal < ((Int8Constant) that).m_nVal);
            case "Int8<=Int8":
                return getConstantPool().valOf(this.m_nVal <= ((Int8Constant) that).m_nVal);
            case "Int8>Int8":
                return getConstantPool().valOf(this.m_nVal > ((Int8Constant) that).m_nVal);
            case "Int8>=Int8":
                return getConstantPool().valOf(this.m_nVal >= ((Int8Constant) that).m_nVal);

            case "Int8<=>Int8":
                return getConstantPool().valOrd(this.m_nVal - ((Int8Constant) that).m_nVal);
            }

        return super.apply(op, that);
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
        if (!(that instanceof Int8Constant))
            {
            return -1;
            }
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
