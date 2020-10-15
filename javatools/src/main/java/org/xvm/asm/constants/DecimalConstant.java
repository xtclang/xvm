package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.math.BigDecimal;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;

import org.xvm.type.Decimal;
import org.xvm.type.Decimal128;
import org.xvm.type.Decimal32;
import org.xvm.type.Decimal64;


/**
 * Represent a 32-bit, 64-bit, or 128-bit IEEE-754-2008 decimal constant.
 */
public class DecimalConstant
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
    public DecimalConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool);
        switch (format)
            {
            case Dec32:
                m_dec = new Decimal32(in);
                break;

            case Dec64:
                m_dec = new Decimal64(in);
                break;

            case Dec128:
                m_dec = new Decimal128(in);
                break;

            default:
                throw new IOException("unsupported format: " + format);
            }
        }

    /**
     * Construct a constant whose value is a fixed-length 32-bit, 64-bit, or 128-bit decimal.
     *
     * @param pool  the ConstantPool that will contain this Constant
     * @param dec   the decimal value
     */
    public DecimalConstant(ConstantPool pool, Decimal dec)
        {
        super(pool);
        switch (dec.getBitLength())
            {
            case 32:
            case 64:
            case 128:
                m_dec = dec;
                break;

            default:
                throw new IllegalArgumentException("unsupported decimal length: " + dec.getBitLength());
            }
        }


    // ----- type-specific methods -----------------------------------------------------------------

    /**
     * Add another DecimalConstant to the value of this DecimalConstant.
     *
     * @param that  a DecimalConstant of the same format
     *
     * @return the sum, as a DecimalConstant of the same format
     */
    public DecimalConstant add(DecimalConstant that)
        {
        if (this.getFormat() != that.getFormat())
            {
            throw new IllegalStateException();
            }

        BigDecimal bigdecSum = this.m_dec.toBigDecimal().add(that.m_dec.toBigDecimal());

        Decimal decSum;
        switch (m_dec.getBitLength())
            {
            case 32:
                decSum = new Decimal32(bigdecSum);
                break;
            case 64:
                decSum = new Decimal64(bigdecSum);
                break;
            case 128:
                decSum = new Decimal128(bigdecSum);
                break;

            default:
                throw new IllegalStateException();
            }

        return getConstantPool().ensureDecConstant(decSum);
        }


    // ----- ValueConstant methods -----------------------------------------------------------------

    /**
     * {@inheritDoc}
     * @return  the constant's value as a Decimal
     */
    @Override
    public Decimal getValue()
        {
        return m_dec;
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        switch (m_dec.getBitLength())
            {
            case 32:
                return Format.Dec32;
            case 64:
                return Format.Dec64;
            case 128:
                return Format.Dec128;

            default:
                throw new IllegalStateException("unsupported decimal length: " + m_dec.getBitLength());
            }
        }

    @Override
    protected int compareDetails(Constant that)
        {
        if (!(that instanceof DecimalConstant))
            {
            return -1;
            }
        return this.m_dec.compareForObjectOrder(((DecimalConstant) that).m_dec);
        }

    @Override
    public String getValueString()
        {
        return m_dec.toString();
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        out.writeByte(getFormat().ordinal());
        m_dec.writeBytes(out);
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
        return m_dec.hashCode();
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The constant value.
     */
    private final Decimal m_dec;
    }
