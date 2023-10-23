package org.xvm.asm.constants;


import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;

import org.xvm.compiler.Token;

import org.xvm.type.Decimal;

import org.xvm.util.Hash;

import static org.xvm.util.Handy.writePackedLong;


/**
 * Represent a decimal value constant that simply delegates to an actual DecimalConstant of 32-bit,
 * 64-bit, or 128-bit. This supports the `Dec` type in Ecstasy.
 */
public class DecimalAutoConstant
        extends ValueConstant
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a constant whose value is a fixed-length 32-bit, 64-bit, or 128-bit decimal.
     *
     * @param pool  the ConstantPool that will contain this Constant
     * @param dec   the decimal value
     */
    public DecimalAutoConstant(ConstantPool pool, Decimal dec)
        {
        this(pool, pool.ensureDecConstant(dec));
        }

    /**
     * Construct a constant whose value is a fixed-length 32-bit, 64-bit, or 128-bit decimal.
     *
     * @param pool  the ConstantPool that will contain this Constant
     * @param dec   the decimal value
     */
    public DecimalAutoConstant(ConstantPool pool, DecimalConstant dec)
        {
        super(pool);
        m_dec = dec;
        }


    // ----- type-specific methods -----------------------------------------------------------------

    /**
     * Add another DecimalConstant to the value of this DecimalConstant.
     *
     * @param that  a DecimalConstant of the same format
     *
     * @return the sum, as a DecimalConstant of the same format
     */
    public DecimalAutoConstant add(DecimalAutoConstant that)
        {
        return getConstantPool().ensureDecAConstant(this.getValue().add(that.getValue()));
        }


    // ----- ValueConstant methods -----------------------------------------------------------------

    /**                                       .
     * {@inheritDoc}
     * @return  the constant's value as a Decimal
     */
    @Override
    public Decimal getValue()
        {
        return m_dec.getValue();
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Constant apply(Token.Id op, Constant that)
        {
        switch (that == null
                    ? op.TEXT + this.getFormat().name()
                    : this.getFormat().name() + op.TEXT + that.getFormat().name())
            {
            case "+Dec":
                return this;

            case "-Dec":
                return getConstantPool().ensureDecAConstant(this.getValue().neg());

            case "Dec+Dec":
                return getConstantPool().ensureDecAConstant(
                    this.getValue().add(((DecimalAutoConstant) that).getValue()));

            case "Dec-Dec":
                return getConstantPool().ensureDecAConstant(
                    this.getValue().subtract(((DecimalAutoConstant) that).getValue()));

            case "Dec*Dec":
                return getConstantPool().ensureDecAConstant(
                    this.getValue().multiply(((DecimalAutoConstant) that).getValue()));

            case "Dec/Dec":
                return getConstantPool().ensureDecAConstant(
                    this.getValue().divide(((DecimalAutoConstant) that).getValue()));

            case "Dec%Dec":
                return getConstantPool().ensureDecAConstant(
                    this.getValue().mod(((DecimalAutoConstant) that).getValue()));

            case "Dec^Dec":
                return getConstantPool().ensureDecAConstant(
                    this.getValue().pow(((DecimalAutoConstant) that).getValue()));

            case "Dec..Dec":
                return getConstantPool().ensureRangeConstant(this, that);

            case "Dec==Dec":
            case "Dec!=Dec":
            case "Dec<Dec":
            case "Dec<=Dec":
            case "Dec>Dec":
            case "Dec>=Dec":
            case "Dec<==>Dec":
                return translateOrder(
                    this.getValue().compareForObjectOrder(((DecimalAutoConstant) that).getValue()), op);
            }

        return super.apply(op, that);
        }

    @Override
    protected Object getLocator()
        {
        return m_dec;
        }

    @Override
    public Format getFormat()
        {
        return Format.Dec64;
        }

    @Override
    protected int compareDetails(Constant that)
        {
        if (!(that instanceof DecimalAutoConstant))
            {
            return -1;
            }
        return this.getValue().compareForObjectOrder(((DecimalAutoConstant) that).getValue());
        }

    @Override
    public String getValueString()
        {
        return m_dec.getValueString();
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        m_dec = (DecimalConstant) pool.register(m_dec);
        }

    @Override
    protected void assemble(DataOutput out)
        throws IOException
        {
        out.writeByte(Format.Dec64.ordinal());
        writePackedLong(out, m_dec.getPosition());
        }

    @Override
    public String getDescription()
        {
        return "value=" + getValueString();
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int computeHashCode()
        {
        return Hash.of(m_dec);
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The DecimalConstant to delegate to.
     */
    private DecimalConstant m_dec;
    }