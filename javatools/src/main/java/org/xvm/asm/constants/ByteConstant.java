package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;

import org.xvm.compiler.Token;

import org.xvm.util.PackedInteger;

import static org.xvm.util.Handy.byteToHexString;
import static org.xvm.util.Handy.nibbleToChar;


/**
 * Represent any of: a bit (1-bit), nibble (4-bit), and octet (checked or unchecked, signed or
 * unsigned 8-bit byte) constant.
 */
public class ByteConstant
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
    public ByteConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool);

        m_format = format;
        m_nVal   = isSigned(format)
                ? in.readByte()
                : in.readUnsignedByte();
        }

    /**
     * Construct a constant whose value is a bit, nibble, or octet.
     *
     * @param pool   the ConstantPool that will contain this Constant
     * @param format one of: Bit, Nibble, UInt8
     * @param bVal   the bit, nibble, or octet value
     */
    public ByteConstant(ConstantPool pool, Format format, int bVal)
        {
        super(pool);

        switch (format)
            {
            case Bit:
                if ((bVal & ~0x01) != 0)
                    {
                    throw new IllegalArgumentException("Bit must be in range 0..1");
                    }
                bVal = bVal == 0 ? 0 : 1;
                break;

            case Nibble:
                if ((bVal & ~0x0F) != 0)
                    {
                    throw new IllegalArgumentException("Nibble must be in range 0..15");
                    }
                bVal &= 0x0F;
                break;

            case CInt8:
            case Int8:
                if (bVal < -128 || bVal > 127)
                    {
                    throw new IllegalArgumentException("Int8 must be in range -128..127 (n=" + bVal + ")");
                    }
                break;

            case CUInt8:
            case UInt8:
                if ((bVal & ~0xFF) != 0)
                    {
                    throw new IllegalArgumentException("Byte must be in range 0..255");
                    }
                bVal &= 0xFF;
                break;

            default:
                throw new IllegalArgumentException("format=" + format);
            }

        m_format = format;
        m_nVal   = bVal;
        }


    // ----- type-specific methods -----------------------------------------------------------------

    /**
     * Create a new ByteConstant with the specified value, but only if it is in the legal range.
     *
     * @param n  an integer value
     *
     * @return the corresponding ByteConstant
     *
     * @throws ArithmeticException  if the value is out of range
     */
    public ByteConstant validate(int n)
        {
        Format  format  = getFormat();
        boolean fSigned = isSigned(format);
        int     nMin    = fSigned ? -128 : 0;
        int     nMax    = fSigned ? 127 : 255;
        if (n < nMin || n > nMax)
            {
            if (isChecked(format))
                {
                throw new ArithmeticException("overflow");
                }
            else
                {
                boolean fNeg = n < 0;
                n &= 0xFF;
                if (fNeg)
                    {
                    n |= 0xFFFFFF00;
                    }
                }
            }

        return getConstantPool().ensureByteConstant(m_format, n);
        }

    /**
     * Create a new ByteConstant with the specified nibble value, but only if it is in the legal
     * range.
     *
     * @param n  an integer value
     *
     * @return the corresponding ByteConstant
     *
     * @throws ArithmeticException  if the value is out of range
     */
    public ByteConstant validateNibble(int n)
        {
        if (n < 0 || n > 15)
            {
            throw new ArithmeticException("overflow");
            }

        return getConstantPool().ensureNibbleConstant(n);
        }

    static int nonzero(int n)
        {
        if (n == 0)
            {
            throw new ArithmeticException("zero");
            }

        return n;
        }

    /**
     * Determine if the specified format is a signed format.
     *
     * @param format  a format supported by this constant class
     *
     * @return true if the format is signed
     */
    static private boolean isSigned(Format format)
        {
        return format == Format.CInt8 || format == Format.Int8;
        }

    /**
     * Determine if the specified format is a range-checked format.
     *
     * @param format  a format supported by this constant class
     *
     * @return true if the format is checked
     */
    static private boolean isChecked(Format format)
        {
        return format != Format.Int8 && format != Format.UInt8;
        }


    // ----- ValueConstant methods -----------------------------------------------------------------

    /**
     * {@inheritDoc}
     * @return  the constant's unsigned byte value as a Java Integer in the range 0-255 (or smaller
     *          for Nibble and Bit values)
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
        return m_format;
        }

    @Override
    public PackedInteger getIntValue()
        {
        return PackedInteger.valueOf(m_nVal);
        }

    private Format normalize(Format format)
        {
        return switch (format)
            {
            case CInt8    -> Format.Int8;
            case CInt16   -> Format.Int16;
            case CInt32   -> Format.Int32;
            case CInt64   -> Format.Int64;
            case CInt128  -> Format.Int128;
            case CIntN    -> Format.IntN;
            case CUInt8   -> Format.UInt8;
            case CUInt16  -> Format.UInt16;
            case CUInt32  -> Format.UInt32;
            case CUInt64  -> Format.UInt64;
            case CUInt128 -> Format.UInt128;
            case CUIntN   -> Format.UIntN;
            default       -> format;
            };
        }

    @Override
    public Constant apply(Token.Id op, Constant that)
        {
        switch (that == null
                ? op.TEXT + normalize(this.getFormat()).name()
                : normalize(this.getFormat()).name() + op.TEXT + normalize(that.getFormat()).name())
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
                return apply(op, ((LiteralConstant) that).toCInt8Constant());

            case "Int8<<IntLiteral":
            case "Int8>>IntLiteral":
            case "Int8>>>IntLiteral":
                return apply(op, ((LiteralConstant) that).toIntConstant(Format.CInt64));

            case "Int8+Int8":
                return validate(this.m_nVal + ((ByteConstant) that).m_nVal);
            case "Int8-Int8":
                return validate(this.m_nVal - ((ByteConstant) that).m_nVal);
            case "Int8*Int8":
                return validate(this.m_nVal * ((ByteConstant) that).m_nVal);
            case "Int8/Int8":
                return validate(this.m_nVal / nonzero(((ByteConstant) that).m_nVal));
            case "Int8%Int8":
                int nDivisor = nonzero(((ByteConstant) that).m_nVal);
                int nModulo  = this.m_nVal % nDivisor;
                return validate(nModulo < 0 ? nModulo + nDivisor : nModulo);
            case "Int8&Int8":
                return validate(this.m_nVal & ((ByteConstant) that).m_nVal);
            case "Int8|Int8":
                return validate(this.m_nVal | ((ByteConstant) that).m_nVal);
            case "Int8^Int8":
                return validate(this.m_nVal ^ ((ByteConstant) that).m_nVal);
            case "Int8..Int8":
                return ConstantPool.getCurrentPool().ensureRangeConstant(this, that);
            case "Int8..<Int8":
                return ConstantPool.getCurrentPool().ensureRangeConstant(this, false, that, true);

            case "Int8<<Int64":
                return validate(this.m_nVal << ((IntConstant) that).getValue().and(new PackedInteger(7)).getInt());
            case "Int8>>Int64":
                return validate(this.m_nVal >> ((IntConstant) that).getValue().and(new PackedInteger(7)).getInt());
            case "Int8>>>Int64":
                return validate(this.m_nVal >>> ((IntConstant) that).getValue().and(new PackedInteger(7)).getInt());

            case "Int8==Int8":
                return getConstantPool().valOf(this.m_nVal == ((ByteConstant) that).m_nVal);
            case "Int8!=Int8":
                return getConstantPool().valOf(this.m_nVal != ((ByteConstant) that).m_nVal);
            case "Int8<Int8":
                return getConstantPool().valOf(this.m_nVal < ((ByteConstant) that).m_nVal);
            case "Int8<=Int8":
                return getConstantPool().valOf(this.m_nVal <= ((ByteConstant) that).m_nVal);
            case "Int8>Int8":
                return getConstantPool().valOf(this.m_nVal > ((ByteConstant) that).m_nVal);
            case "Int8>=Int8":
                return getConstantPool().valOf(this.m_nVal >= ((ByteConstant) that).m_nVal);

            case "Int8<=>Int8":
                return getConstantPool().valOrd(this.m_nVal - ((ByteConstant) that).m_nVal);

            case "-UInt8":
                return validate(-this.m_nVal);
            case "~UInt8":
                return validate(~this.m_nVal);

            case "UInt8+IntLiteral":
            case "UInt8-IntLiteral":
            case "UInt8*IntLiteral":
            case "UInt8/IntLiteral":
            case "UInt8%IntLiteral":
            case "UInt8&IntLiteral":
            case "UInt8|IntLiteral":
            case "UInt8^IntLiteral":
            case "UInt8..IntLiteral":
            case "UInt8..<IntLiteral":
            case "UInt8==IntLiteral":
            case "UInt8!=IntLiteral":
            case "UInt8<IntLiteral":
            case "UInt8<=IntLiteral":
            case "UInt8>IntLiteral":
            case "UInt8>=IntLiteral":
            case "UInt8<=>IntLiteral":
                return apply(op, ((LiteralConstant) that).toCUInt8Constant());

            case "UInt8<<IntLiteral":
            case "UInt8>>IntLiteral":
            case "UInt8>>>IntLiteral":
                return apply(op, ((LiteralConstant) that).toIntConstant(Format.CInt64));

            case "UInt8+UInt8":
                return validate(this.m_nVal + ((ByteConstant) that).m_nVal);
            case "UInt8-UInt8":
                return validate(this.m_nVal - ((ByteConstant) that).m_nVal);
            case "UInt8*UInt8":
                return validate(this.m_nVal * ((ByteConstant) that).m_nVal);
            case "UInt8/UInt8":
                return validate(this.m_nVal / nonzero(((ByteConstant) that).m_nVal));
            case "UInt8%UInt8":
                // no special handling needed for Java's negative "modulos" (remainders) here since
                // we're dealing with unsigned values
                return validate(this.m_nVal % nonzero(((ByteConstant) that).m_nVal));
            case "UInt8&UInt8":
                return validate(this.m_nVal & ((ByteConstant) that).m_nVal);
            case "UInt8|UInt8":
                return validate(this.m_nVal | ((ByteConstant) that).m_nVal);
            case "UInt8^UInt8":
                return validate(this.m_nVal ^ ((ByteConstant) that).m_nVal);
            case "UInt8..UInt8":
            case "Nibble..Nibble":
                return ConstantPool.getCurrentPool().ensureRangeConstant(this, that);
            case "UInt8..<UInt8":
                return ConstantPool.getCurrentPool().ensureRangeConstant(this, false, that, true);

            case "UInt8<<Int64":
                return validate(this.m_nVal << ((IntConstant) that).getValue().and(new PackedInteger(7)).getInt());
            case "UInt8>>Int64":
            case "UInt8>>>Int64":
                return validate(this.m_nVal >>> ((IntConstant) that).getValue().and(new PackedInteger(7)).getInt());

            case "UInt8==UInt8":
            case "Nibble==Nibble":
                return getConstantPool().valOf(this.m_nVal == ((ByteConstant) that).m_nVal);
            case "UInt8!=UInt8":
            case "Nibble!=Nibble":
                return getConstantPool().valOf(this.m_nVal != ((ByteConstant) that).m_nVal);
            case "UInt8<UInt8":
            case "Nibble<Nibble":
                return getConstantPool().valOf(this.m_nVal < ((ByteConstant) that).m_nVal);
            case "UInt8<=UInt8":
            case "Nibble<=Nibble":
                return getConstantPool().valOf(this.m_nVal <= ((ByteConstant) that).m_nVal);
            case "UInt8>UInt8":
            case "Nibble>Nibble":
                return getConstantPool().valOf(this.m_nVal > ((ByteConstant) that).m_nVal);
            case "UInt8>=UInt8":
            case "Nibble>=Nibble":
                return getConstantPool().valOf(this.m_nVal >= ((ByteConstant) that).m_nVal);
            case "UInt8<=>UInt8":
            case "Nibble<==>Nibble":
                return getConstantPool().valOrd(this.m_nVal - ((ByteConstant) that).m_nVal);

            case "Nibble+Nibble":
                return validateNibble(this.m_nVal + ((ByteConstant) that).m_nVal);
            case "Nibble-Nibble":
                return validateNibble(this.m_nVal - ((ByteConstant) that).m_nVal);
            case "Nibble*Nibble":
                return validateNibble(this.m_nVal * ((ByteConstant) that).m_nVal);
            case "Nibble/Nibble":
                return validateNibble(this.m_nVal / nonzero(((ByteConstant) that).m_nVal));

            // these are "fake" i.e. compile-time only in order to support calculations resulting
            // from the use of Range in ForEachStatement
            case "Bit+IntLiteral":
            case "Bit-IntLiteral":
            case "Nibble+IntLiteral":
            case "Nibble-IntLiteral":
                {
                int delta = ((LiteralConstant) that).toIntConstant(Format.CInt32).getIntValue().getInt();
                if (op == Token.Id.SUB)
                    {
                    delta = -delta;
                    }

                return m_format == Format.Bit
                        ? getConstantPool().ensureBitConstant(m_nVal + delta)
                        : getConstantPool().ensureNibbleConstant(m_nVal + delta);
                }
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
        if (!(that instanceof ByteConstant))
            {
            return -1;
            }
        return this.m_nVal - ((ByteConstant) that).m_nVal;
        }

    @Override
    public String getValueString()
        {
        switch (m_format)
            {
            case Bit:
                return String.valueOf(m_nVal);

            case Nibble:
                return "0x" + nibbleToChar(m_nVal);

            case CInt8:
            case Int8:
                return Integer.toString(m_nVal);

            default:
            case CUInt8:
            case UInt8:
                return byteToHexString(m_nVal);
            }
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        out.writeByte(m_format.ordinal());
        out.writeByte(m_nVal);
        }

    @Override
    public String getDescription()
        {
        return m_format.name() + '=' + getValueString();
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        return m_nVal;
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The format of the constant; one of: Bit, Nibble, CInt8, Int8, CUInt8, UInt8.
     */
    private final Format m_format;

    /**
     * The constant octet value stored here as an integer in the range 0-255.
     */
    private final int m_nVal;
    }
