package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;

import org.xvm.compiler.Token;

import org.xvm.type.Decimal;
import org.xvm.type.Decimal128;
import org.xvm.type.Decimal32;
import org.xvm.type.Decimal64;

import org.xvm.util.Hash;


/**
 * Represent a 32-bit, 64-bit, or 128-bit IEEE-754-2008 decimal constant.
 */
public class DecimalConstant
        extends ValueConstant {
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
            throws IOException {
        super(pool);
        switch (format) {
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
    public DecimalConstant(ConstantPool pool, Decimal dec) {
        super(pool);
        switch (dec.getBitLength()) {
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
    public DecimalConstant add(DecimalConstant that) {
        if (this.getFormat() != that.getFormat()) {
            throw new IllegalStateException();
        }

        return getConstantPool().ensureDecConstant(this.m_dec.add(that.m_dec));
    }


    // ----- ValueConstant methods -----------------------------------------------------------------

    /**
     * {@inheritDoc}
     * @return  the constant's value as a Decimal
     */
    @Override
    public Decimal getValue() {
        return m_dec;
    }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Constant apply(Token.Id op, Constant that) {
        switch (that == null
                    ? op.TEXT + this.getFormat().name()
                    : this.getFormat().name() + op.TEXT + that.getFormat().name()) {
        case "+Dec32":
        case "+Dec64":
        case "+Dec128":
            return this;

        case "-Dec32":
        case "-Dec64":
        case "-Dec128":
            return getConstantPool().ensureDecConstant(this.getValue().neg());

        case "Dec32+Dec32":
        case "Dec64+Dec64":
        case "Dec128+Dec128":
            return getConstantPool().ensureDecConstant(
                this.getValue().add(((DecimalConstant) that).getValue()));

        case "Dec32-Dec32":
        case "Dec64-Dec64":
        case "Dec128-Dec128":
            return getConstantPool().ensureDecConstant(
                this.getValue().subtract(((DecimalConstant) that).getValue()));

        case "Dec32*Dec32":
        case "Dec64*Dec64":
        case "Dec128*Dec128":
            return getConstantPool().ensureDecConstant(
                this.getValue().multiply(((DecimalConstant) that).getValue()));

        case "Dec32/Dec32":
        case "Dec64/Dec64":
        case "Dec128/Dec128":
            return getConstantPool().ensureDecConstant(
                this.getValue().divide(((DecimalConstant) that).getValue()));

        case "Dec32%Dec32":
        case "Dec64%Dec64":
        case "Dec128%Dec128":
            return getConstantPool().ensureDecConstant(
                this.getValue().mod(((DecimalConstant) that).getValue()));

        case "Dec32^Dec32":     // REVIEW GG CP WTF?!? pow()??? 😮
        case "Dec64^Dec64":
        case "Dec128^Dec128":
            return getConstantPool().ensureDecConstant(
                this.getValue().pow(((DecimalConstant) that).getValue()));

        case "Dec32..Dec32":
        case "Dec64..Dec64":
        case "Dec128..Dec128":
            return getConstantPool().ensureRangeConstant(this, that);

        case "Dec32==Dec32":
        case "Dec64==Dec64":
        case "Dec128==Dec128":
        case "Dec32!=Dec32":
        case "Dec64!=Dec64":
        case "Dec128!=Dec128":
        case "Dec32<Dec32":
        case "Dec64<Dec64":
        case "Dec128<Dec128":
        case "Dec32<=Dec32":
        case "Dec64<=Dec64":
        case "Dec128<=Dec128":
        case "Dec32>Dec32":
        case "Dec64>Dec64":
        case "Dec128>Dec128":
        case "Dec32>=Dec32":
        case "Dec64>=Dec64":
        case "Dec128>=Dec128":
        case "Dec32<==>Dec32":
        case "Dec64<==>Dec64":
        case "Dec128<==>Dec128":
            return translateOrder(
                this.getValue().compareForObjectOrder(((DecimalConstant) that).getValue()), op);
        }

        return super.apply(op, that);
    }

    @Override
    protected Object getLocator() {
        return m_dec;
    }

    @Override
    public Format getFormat() {
        return switch (m_dec.getBitLength()) {
            case 32  -> Format.Dec32;
            case 64  -> Format.Dec64;
            case 128 -> Format.Dec128;
            default  -> throw new IllegalStateException("unsupported decimal length: " + m_dec.getBitLength());
        };
    }

    @Override
    protected int compareDetails(Constant that) {
        if (!(that instanceof DecimalConstant)) {
            return -1;
        }
        return this.m_dec.compareForObjectOrder(((DecimalConstant) that).m_dec);
    }

    @Override
    public String getValueString() {
        return m_dec.toString();
    }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void assemble(DataOutput out)
            throws IOException {
        out.writeByte(getFormat().ordinal());
        m_dec.writeBytes(out);
    }

    @Override
    public String getDescription() {
        return "value=" + getValueString();
    }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int computeHashCode() {
        return Hash.of(m_dec);
    }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The constant value.
     */
    private final Decimal m_dec;
}