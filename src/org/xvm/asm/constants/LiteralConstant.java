package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.math.BigDecimal;
import java.math.BigInteger;

import java.util.function.Consumer;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;

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
     * Add the value of a LiteralConstant to the value of this LiteralConstant, resulting in the
     * creation of a result LiteralConstant.
     *
     * @param that  another LiteralConstant of a type that can be added to the type of this
     *
     * @return a new LiteralConstant representing the result of adding <i>that</i> to <i>this</i>
     */
    public LiteralConstant add(LiteralConstant that)
        {
        ConstantPool pool = getConstantPool();
        String       sLit = null;
        switch (m_fmt)
            {
            case IntLiteral:
                // can add an FPLiteral (commutative)
                if (that.m_fmt == Format.FPLiteral)
                    {
                    return that.add(this);
                    }

                if (that.m_fmt == Format.IntLiteral)
                    {
                    sLit = getIntegerValue().add(that.getIntegerValue()).toString(getIntegerRadix());
                    }
                break;

            case FPLiteral:
                {
                // can add either an FPLiteral or an IntLiteral to an FPLiteral, resulting in a new
                // FPLiteral
                if (getFPRadix() == 2)
                    {
                    double dflThis = getFPDouble();
                    if (that.getFormat() == Format.FPLiteral)
                        {
                        sLit = Double.toString(dflThis + that.getFPDouble());
                        }
                    else if (that.getFormat() == Format.IntLiteral)
                        {
                        PackedInteger piThat = that.getIntegerValue();
                        double dflSum = piThat.isBig()
                                ? dflThis + piThat.getBigInteger().doubleValue()
                                : dflThis + piThat.getLong();
                        sLit = Double.toString(dflSum);
                        }
                    }
                else
                    {
                    BigDecimal decThis = getFPDecimal();
                    if (that.getFormat() == Format.FPLiteral)
                        {
                        sLit = decThis.add(new BigDecimal(that.getIntegerValue().getBigInteger()))
                                .toString().replace('E', 'P');
                        }
                    else if (that.getFormat() == Format.IntLiteral)
                        {
                        sLit = decThis.add(new BigDecimal(that.getIntegerValue().getBigInteger()))
                                .toString().replace('E', 'P');
                        }
                    }
                }
                break;

            case Date:
                // TODO can add a duration
                throw new UnsupportedOperationException();

            case Time:
                // TODO can add a duration
                throw new UnsupportedOperationException();

            case DateTime:
                // TODO can add a duration
                throw new UnsupportedOperationException();

            case Duration:
                // TODO can add another duration
                throw new UnsupportedOperationException();

            case TimeInterval:
                // TODO not sure why we even have this type - can you add another time interval? a duration?
                throw new UnsupportedOperationException();
            }

        if (sLit != null)
            {
            return pool.ensureLiteralConstant(this.getFormat(), sLit);
            }

        throw new IllegalStateException("this=" + this.getFormat().name() + ", that=" + that.getFormat().name());
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
