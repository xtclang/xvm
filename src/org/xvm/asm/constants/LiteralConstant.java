package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.math.BigDecimal;
import java.math.BigInteger;

import java.util.function.Consumer;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;

import org.xvm.compiler.Token;

import org.xvm.type.Decimal;
import org.xvm.type.Decimal128;
import org.xvm.type.Decimal32;
import org.xvm.type.Decimal64;

import org.xvm.util.PackedInteger;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Represent a constant that stores its value as a StringConstant.
 * <p/> 
 * This class implements the following constant formats:
 * <ul>
 * <li>IntLiteral</li>
 * <li>FPLiteral</li>
 * <li>Date</li>        
 * <li>Time</li>        
 * <li>DateTime</li>    
 * <li>Duration</li>    
 * <li>TimeInterval</li>
 * <li>Version (but via the {@link VersionConstant} sub-class)</li>
 * </ul>
 */
public class LiteralConstant
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
    public LiteralConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        this(pool, format);
        m_iStr = readMagnitude(in);
        }

    /**
     * Construct a constant whose value is a literal.
     *
     * @param pool    the ConstantPool that will contain this Constant
     * @param format  the Constant Format
     * @param sVal    the literal value
     */
    public LiteralConstant(ConstantPool pool, Format format, String sVal)
        {
        this(pool, format);

        if (sVal == null)
            {
            throw new IllegalStateException("literal value required");
            }

        // validate literal value
        switch (format)
            {
            case IntLiteral:
                // TODO
                break;
            
            case FPLiteral:
                // TODO
                break;

            case Date:
                // TODO
                break;

            case Time:
                // TODO
                break;

            case DateTime:
                // TODO
                break;

            case Duration:
                // TODO
                break;

            case TimeInterval:
                // TODO
                break;

            case Version:
                if (!(this instanceof VersionConstant))
                    {
                    throw new IllegalStateException("version requires VersionConstant subclass");
                    }
                break;

            default:
                throw new IllegalStateException("unsupported format: " + format);
            }

        m_constStr = pool.ensureStringConstant(sVal);
        }

    /**
     * Internal constructor.
     *
     * @param pool    constant pool
     * @param format  format of this constant
     */
    private LiteralConstant(ConstantPool pool, Format format)
        {
        super(pool);
        
        if (format == null)
            {
            throw new IllegalStateException("format required");
            }
        
        switch (format)
            {
            case IntLiteral:
            case FPLiteral:
            case Date:        
            case Time:        
            case DateTime:    
            case Duration:    
            case TimeInterval:
            case Version:
                break;

            default:
                throw new IllegalStateException("unsupported format: " + format);
            }

        m_fmt = format;
        }


    // ----- type-specific functionality -----------------------------------------------------------

    /**
     * {@inheritDoc}
     * @return  the constant's value as a String
     */
    @Override
    public String getValue()
        {
        return m_constStr.getValue();
        }

    /**
     * @return the radix of an IntLiteral
     */
    public int getIntegerRadix()
        {
        assert getFormat() == Format.IntLiteral;
        String s = getValue();

        // if the next character is '0', it is potentially part of a prefix denoting a radix
        if (s.length() > 2 && s.charAt(0) == '0')
            {
            switch (s.charAt(1))
                {
                case 'B':
                case 'b':
                    return 2;
                case 'o':
                    return 8;
                case 'X':
                case 'x':
                    return 16;
                }
            }

        return 10;
        }

    /**
     * @return the PackedInteger value of an IntLiteral
     */
    public PackedInteger getIntegerValue()
        {
        assert getFormat() == Format.IntLiteral;
        String s = getValue();
        int    r = getIntegerRadix();
        if (r == 10)
            {
            return s.length() < 20
                    ? PackedInteger.valueOf(Long.parseLong(s))
                    : new PackedInteger(new BigInteger(s));
            }
        else
            {
            return s.length() < 2 + (64 / Integer.numberOfTrailingZeros(r))
                    ? new PackedInteger(Long.parseLong(s.substring(2), r))
                    : new PackedInteger(new BigInteger(s.substring(2), r));
            }
        }

    /**
     * Obtain the radix of the floating point literal.
     * <p/>
     * This should only be called if the constant is an FPLiteral.
     *
     * @return 2 iff the floating point literal specifies a binary radix, otherwise 10
     */
    public int getFPRadix()
        {
        // Ecstasy always uses decimal by default, unless forced to use base 2 floating point
        assert getFormat() == Format.FPLiteral;
        return m_constStr.getValue().indexOf('E') >= 0 || m_constStr.getValue().indexOf('e') >= 0
                ? 2
                : 10;
        }

    /**
     * Obtain the decimal value of the floating point literal.
     * <p/>
     * This should only be called if the constant is an FPLiteral and the value's radix is 10.
     *
     * @return the decimal value of the floating point literal
     */
    public BigDecimal getFPDecimal()
        {
        assert getFormat() == Format.FPLiteral && getFPRadix() == 10;

        // BigDecimal uses "E" to indicate a decimal exponent, while ISO uses "P"
        return new BigDecimal(m_constStr.getValue().replace('p', 'e').replace('P', 'E'));
        }

    /**
     * Obtain the radix-2 value of the floating point literal.
     * <p/>
     * This should only be called if the constant is an FPLiteral.
     *
     * @return the decimal value of the floating point literal
     */
    public double getFPDouble()
        {
        assert getFormat() == Format.FPLiteral;
        return getFPRadix() == 2
                ? Double.parseDouble(m_constStr.getValue())
                : getFPDecimal().doubleValue();
        }

    /**
     * Convert the LiteralConstant to an Int8Constant, iff the LiteralConstant is an IntLiteral
     * whose value is in the range -128..127.
     *
     * @return an Int8Constant
     *
     * @throws ArithmeticException  on overflow
     */
    public Int8Constant toInt8Constant()
        {
        if (getFormat() != Format.IntLiteral)
            {
            throw new IllegalStateException("format=" + getFormat());
            }

        PackedInteger pi = getIntegerValue();
        if (pi.isBig() || pi.getLong() < -128 || pi.getLong() > 127)
            {
            throw new ArithmeticException("out of range: " + pi);
            }

        return getConstantPool().ensureInt8Constant(pi.getInt());
        }

    /**
     * Convert the LiteralConstant to an UInt8Constant, iff the LiteralConstant is an IntLiteral
     * whose value is in the range 0..255.
     *
     * @return a UInt8Constant
     *
     * @throws ArithmeticException  on overflow
     */
    public UInt8Constant toUInt8Constant()
        {
        if (getFormat() != Format.IntLiteral)
            {
            throw new IllegalStateException("format=" + getFormat());
            }

        PackedInteger pi = getIntegerValue();
        if (pi.isBig() || pi.getLong() < 0 || pi.getLong() > 255)
            {
            throw new ArithmeticException("out of range: " + pi);
            }

        return getConstantPool().ensureUInt8Constant(pi.getInt());
        }

    /**
     * Convert the LiteralConstant to an IntConstant of the specified format.
     *
     * @param format  the format of the IntConstant to use
     *
     * @return an IntConstant
     *
     * @throws ArithmeticException  on overflow
     */
    public IntConstant toIntConstant(Format format)
        {
        if (getFormat() != Format.IntLiteral)
            {
            throw new IllegalStateException("format=" + getFormat());
            }

        PackedInteger pi = getIntegerValue();
        if (       pi.compareTo(IntConstant.getMinLimit(format)) < 0
                || pi.compareTo(IntConstant.getMaxLimit(format)) > 0)
            {
            throw new ArithmeticException("out of range: " + pi);
            }

        return getConstantPool().ensureIntConstant(pi, format);
        }

    /**
     * @return the equivalent Float16Constant for this LiteralConstant
     */
    public Float16Constant toFloat16Constant()
        {
        assert m_fmt == Format.IntLiteral || m_fmt == Format.FPLiteral;
        return getConstantPool().ensureFloat16Constant(m_fmt == Format.IntLiteral
                ? getIntegerValue().getBigInteger().floatValue()
                : (float) getFPDouble());
        }

    /**
     * @return the equivalent Float32Constant for this LiteralConstant
     */
    public Float32Constant toFloat32Constant()
        {
        assert m_fmt == Format.IntLiteral || m_fmt == Format.FPLiteral;
        return getConstantPool().ensureFloat32Constant(m_fmt == Format.IntLiteral
                ? getIntegerValue().getBigInteger().floatValue()
                : (float) getFPDouble());
        }

    /**
     * @return the equivalent Float64Constant for this LiteralConstant
     */
    public Float64Constant toFloat64Constant()
        {
        assert m_fmt == Format.IntLiteral || m_fmt == Format.FPLiteral;
        return getConstantPool().ensureFloat64Constant(m_fmt == Format.IntLiteral
                ? getIntegerValue().getBigInteger().doubleValue()
                : getFPDouble());
        }

    /**
     * @return the equivalent Float128Constant for this LiteralConstant
     */
    public Float128Constant toFloat128Constant()
        {
        assert m_fmt == Format.IntLiteral || m_fmt == Format.FPLiteral;
        throw new UnsupportedOperationException();
        }

    /**
     * @return the equivalent VarFPConstant of type VarFloat for this LiteralConstant holding a
     *         FPLiteral value
     */
    public VarFPConstant toVarFloatConstant()
        {
        assert m_fmt == Format.IntLiteral || m_fmt == Format.FPLiteral;
        throw new UnsupportedOperationException();
        }

    /**
     * @return the equivalent DecimalConstant of the specified format for this LiteralConstant
     *         holding a FPLiteral value
     */
    public DecimalConstant toDecimalConstant(Format format)
        {
        assert m_fmt == Format.IntLiteral || m_fmt == Format.FPLiteral;

        BigDecimal bigdec = m_fmt == Format.IntLiteral
                ? new BigDecimal(getIntegerValue().getBigInteger())
                : getFPDecimal();

        Decimal dec;
        switch (format)
            {
            case Dec32:
                dec = new Decimal32(bigdec);
                break;
            case Dec64:
                dec = new Decimal64(bigdec);
                break;
            case Dec128:
                dec = new Decimal128(bigdec);
                break;
            default:
                throw new IllegalStateException();
            }
        return getConstantPool().ensureDecimalConstant(dec);
        }

    /**
     * @return the equivalent VarFPConstant of type VarFloat for this LiteralConstant holding a
     *         FPLiteral value
     */
    public VarFPConstant toVarDecConstant()
        {
        assert m_fmt == Format.IntLiteral || m_fmt == Format.FPLiteral;
        throw new UnsupportedOperationException();
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return m_fmt;
        }

    @Override
    public Constant simplify()
        {
        m_constStr = (StringConstant) m_constStr.simplify();
        return this;
        }

    @Override
    public Constant apply(Token.Id op, Constant that)
        {
        ConstantPool pool = getConstantPool();
        switch (this.getFormat().name() + op.TEXT + that.getFormat().name())
            {
            case "IntLiteral+IntLiteral":
                return pool.ensureLiteralConstant(Format.IntLiteral,
                        this.getIntegerValue().add(((LiteralConstant) that).getIntegerValue())
                                .toString(this.getIntegerRadix()));

            case "FPLiteral+FPLiteral":
                {
                String sLit;
                if (getFPRadix() == 2)
                    {
                    sLit = Double.toString(this.getFPDouble() + ((LiteralConstant) that).getFPDouble());
                    }
                else
                    {
                    BigDecimal decThis = this.getFPDecimal();
                    BigDecimal decThat = ((LiteralConstant) that).getFPDecimal();
                    sLit = decThis.add(decThat).toString().replace('E', 'P');
                    }
                return pool.ensureLiteralConstant(Format.FPLiteral, sLit);
                }

            case "FPLiteral+IntLiteral":
                {
                String sLit;
                if (getFPRadix() == 2)
                    {
                    double        dflThis = getFPDouble();
                    PackedInteger piThat  = ((LiteralConstant) that).getIntegerValue();
                    double        dflSum  = piThat.isBig()
                            ? dflThis + piThat.getBigInteger().doubleValue()
                            : dflThis + piThat.getLong();
                    sLit = Double.toString(dflSum);
                    }
                else
                    {
                    BigDecimal decThis = getFPDecimal();
                    BigDecimal decThat = new BigDecimal(((LiteralConstant) that).getIntegerValue().getBigInteger());
                    sLit = decThis.add(decThat).toString().replace('E', 'P');
                    }
                return pool.ensureLiteralConstant(Format.FPLiteral, sLit);
                }

            // commutative
            case "IntLiteral+FPLiteral":
                return that.apply(op, this);
            case "IntLiteral+Int8":
                return that.apply(op, this.toInt8Constant());
            case "IntLiteral+UInt8":
                return that.apply(op, this.toUInt8Constant());
            case "IntLiteral+Int16":
            case "IntLiteral+Int32":
            case "IntLiteral+Int64":
            case "IntLiteral+Int128":
            case "IntLiteral+VarInt":
            case "IntLiteral+UInt16":
            case "IntLiteral+UInt32":
            case "IntLiteral+UInt64":
            case "IntLiteral+UInt128":
            case "IntLiteral+VarUInt":
                return that.apply(op, this.toIntConstant(that.getFormat()));
            case "IntLiteral+Float16":
            case "FPLiteral+Float16":
                return that.apply(op, this.toFloat16Constant());
            case "IntLiteral+Float32":
            case "FPLiteral+Float32":
                return that.apply(op, this.toFloat32Constant());
            case "IntLiteral+Float64":
            case "FPLiteral+Float64":
                return that.apply(op, this.toFloat64Constant());
            case "IntLiteral+Float128":
            case "FPLiteral+Float128":
                return that.apply(op, this.toFloat128Constant());
            case "IntLiteral+VarFloat":
            case "FPLiteral+VarFloat":
                return that.apply(op, this.toVarFloatConstant());
            case "IntLiteral+Dec32":
            case "IntLiteral+Dec64":
            case "IntLiteral+Dec128":
            case "FPLiteral+Dec32":
            case "FPLiteral+Dec64":
            case "FPLiteral+Dec128":
                return that.apply(op, this.toDecimalConstant(that.getFormat()));
            case "IntLiteral+VarDec":
            case "FPLiteral+VarDec":
                return that.apply(op, this.toVarDecConstant());

            // case Date: // TODO can add a duration
            // case Time: // TODO can add a duration
            // case DateTime: // TODO can add a duration
            // case Duration: // TODO can add another duration
            // case TimeInterval: // TODO not sure why we even have this type - can you add another time interval? a duration?
            }

        return super.apply(op, that);
        }

    @Override
    public void forEachUnderlying(Consumer<Constant> visitor)
        {
        visitor.accept(m_constStr);
        }

    @Override
    public Object getLocator()
        {
        return getValue();
        }

    @Override
    protected int compareDetails(Constant that)
        {
        return this.getValue().compareTo(((LiteralConstant) that).getValue());
        }

    @Override
    public String getValueString()
        {
        return '\"' + getValue() + '\"';
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void disassemble(DataInput in)
            throws IOException
        {
        final ConstantPool pool = getConstantPool();
        m_constStr = (StringConstant) pool.getConstant(m_iStr);
        assert m_constStr != null;
        }

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        m_constStr = (StringConstant) pool.register(m_constStr);
        assert m_constStr != null;
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        out.writeByte(getFormat().ordinal());
        writePackedLong(out, m_constStr.getPosition());
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
        return getValue().hashCode();
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The format of the constant
     */
    private final Format m_fmt;

    /**
     * Used during deserialization: holds the index of the string constant.
     */
    private transient int m_iStr;

    /**
     * The String Constant that is the literal value.
     */
    private StringConstant m_constStr;
    }
