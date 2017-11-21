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
import org.xvm.compiler.Token.Id;

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
     /**
     * Obtain the radix of the integer literal.
     * <p/>
     * This must not be called if the constant is not an IntLiteral.
     *
     * @return the radix of an IntLiteral
     */
    public int getIntRadix()
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
     * Obtain the value of the integer literal.
     * <p/>
     * This must not be called if the constant is not an IntLiteral.
     *
     * @return the PackedInteger value of an IntLiteral
     */
    public PackedInteger getPackedInteger()
        {
        assert getFormat() == Format.IntLiteral;
        String s = getValue();
        int    r = getIntRadix();
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
     * This must not be called if the constant is not an IntLiteral or an FPLiteral.
     *
     * @return 2 iff the floating point literal specifies a binary radix, otherwise 10
     */
    public int getFPRadix()
        {
        assert getFormat() == Format.IntLiteral || getFormat() == Format.FPLiteral;

        // Ecstasy always uses decimal by default, unless forced to use base 2 floating point
        return m_constStr.getValue().indexOf('E') >= 0 || m_constStr.getValue().indexOf('e') >= 0
                ? 2
                : 10;
        }

    /**
     * Obtain the BigDecimal value of the floating point literal.
     * <p/>
     * This must not be called if the constant is not an IntLiteral or an FPLiteral of radix 10.
     *
     * @return the BigDecimal value of the floating point literal
     */
    public BigDecimal getBigDecimal()
        {
        assert getFormat() == Format.IntLiteral || getFormat() == Format.FPLiteral && getFPRadix() == 10;

        // Java BigDecimal uses "E" to indicate a decimal exponent, while ISO uses "P"
        return getFormat() == Format.IntLiteral
                ? new BigDecimal(getPackedInteger().getBigInteger())
                : new BigDecimal(m_constStr.getValue().replace('p', 'e').replace('P', 'E'));
        }

    /**
     * Obtain the Decimal value of the floating point literal.
     * <p/>
     * This must not be called if the constant is not an IntLiteral or an FPLiteral of radix 10.
     *
     * @return the Decimal value of the floating point literal
     */
    public Decimal getDecimal(Format format)
        {
        BigDecimal bigdec = getBigDecimal();
        switch (format)
            {
            case Dec32:
                return new Decimal32(bigdec);
            case Dec64:
                return new Decimal64(bigdec);
            case Dec128:
                return new Decimal128(bigdec);
            default:
                throw new IllegalStateException();
            }
        }

    /**
     * Obtain the radix-2 value of the floating point literal.
     * <p/>
     * This must not be called if the constant is not an IntLiteral or an FPLiteral.
     *
     * @return the Java "float" value of the floating point literal
     */
    public float getFloat()
        {
        if (getFormat() == Format.IntLiteral)
            {
            return getPackedInteger().getBigInteger().floatValue();
            }

        return getFPRadix() == 2
                ? Float.parseFloat(m_constStr.getValue())
                : getBigDecimal().floatValue();
        }

    /**
     * Obtain the radix-2 value of the floating point literal.
     * <p/>
     * This must not be called if the constant is not an IntLiteral or an FPLiteral.
     *
     * @return the Java "double" value of the floating point literal
     */
    public double getDouble()
        {
        if (getFormat() == Format.IntLiteral)
            {
            return getPackedInteger().getBigInteger().doubleValue();
            }

        return getFPRadix() == 2
                ? Double.parseDouble(m_constStr.getValue())
                : getBigDecimal().doubleValue();
        }

    /**
     * Convert the LiteralConstant to an UInt8Constant of type bit, iff the LiteralConstant is an
     * IntLiteral whose value is in the range 0..1.
     *
     * @return a UInt8Constant
     *
     * @throws ArithmeticException  on overflow
     */
    public UInt8Constant toBitConstant()
        {
        if (getFormat() != Format.IntLiteral)
            {
            throw new IllegalStateException("format=" + getFormat());
            }

        PackedInteger pi = getPackedInteger();
        if (pi.isBig() || pi.getLong() < 0 || pi.getLong() > 1)
            {
            throw new ArithmeticException("out of range: " + pi);
            }

        return getConstantPool().ensureBitConstant(pi.getInt());
        }

    /**
     * Convert the LiteralConstant to an UInt8Constant of type bit, iff the LiteralConstant is an
     * IntLiteral whose value is in the range 0..1.
     *
     * @return a UInt8Constant
     *
     * @throws ArithmeticException  on overflow
     */
    public UInt8Constant toNibbleConstant()
        {
        if (getFormat() != Format.IntLiteral)
            {
            throw new IllegalStateException("format=" + getFormat());
            }

        PackedInteger pi = getPackedInteger();
        if (pi.isBig() || pi.getLong() < 0x0 || pi.getLong() > 0xF)
            {
            throw new ArithmeticException("out of range: " + pi);
            }

        return getConstantPool().ensureNibbleConstant(pi.getInt());
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

        PackedInteger pi = getPackedInteger();
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

        PackedInteger pi = getPackedInteger();
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

        PackedInteger pi = getPackedInteger();
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
        return getConstantPool().ensureFloat16Constant(getFloat());
        }

    /**
     * @return the equivalent Float32Constant for this LiteralConstant
     */
    public Float32Constant toFloat32Constant()
        {
        assert m_fmt == Format.IntLiteral || m_fmt == Format.FPLiteral;
        return getConstantPool().ensureFloat32Constant(getFloat());
        }

    /**
     * @return the equivalent Float64Constant for this LiteralConstant
     */
    public Float64Constant toFloat64Constant()
        {
        assert m_fmt == Format.IntLiteral || m_fmt == Format.FPLiteral;
        return getConstantPool().ensureFloat64Constant(getDouble());
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
        return getConstantPool().ensureDecimalConstant(getDecimal(format));
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
    public TypeConstant resultType(Id op, Constant that)
        {
        // order of automatic type promotion is from IntLiteral to FPLiteral to any "actual" types
        if (this.getFormat() == Format.IntLiteral && that.getFormat() != Format.IntLiteral)
            {
            return that.getType();
            }

        if (this.getFormat() == Format.FPLiteral && that.getFormat() != Format.IntLiteral
                                                 && that.getFormat() != Format.FPLiteral)
            {
            return that.getType();
            }

        return super.resultType(op, that);
        }

    @Override
    public Constant apply(Token.Id op, Constant that)
        {
        ConstantPool pool = getConstantPool();
        switch (this.getFormat().name() + op.TEXT + that.getFormat().name())
            {
            case "IntLiteral+IntLiteral":
            case "IntLiteral-IntLiteral":
            case "IntLiteral*IntLiteral":
            case "IntLiteral/IntLiteral":
            case "IntLiteral%IntLiteral":
            case "IntLiteral&IntLiteral":
            case "IntLiteral|IntLiteral":
            case "IntLiteral^IntLiteral":
            case "IntLiteral<<IntLiteral":
            case "IntLiteral>>IntLiteral":
            case "IntLiteral>>>IntLiteral":
                {
                PackedInteger piThis   = this.getPackedInteger();
                PackedInteger piThat   = ((LiteralConstant) that).getPackedInteger();
                PackedInteger piResult;
                switch (op)
                    {
                    case ADD:
                        piResult = piThis.add(piThat);
                        break;
                    case SUB:
                        piResult = piThis.sub(piThat);
                        break;
                    case MUL:
                        piResult = piThis.mul(piThat);
                        break;
                    case DIV:
                        piResult = piThis.div(piThat);
                        break;
                    case MOD:
                        piResult = piThis.mod(piThat);
                        break;
                    case BIT_AND:
                        piResult = piThis.and(piThat);
                        break;
                    case BIT_OR:
                        piResult = piThis.or(piThat);
                        break;
                    case BIT_XOR:
                        piResult = piThis.xor(piThat);
                        break;
                    case SHL:
                        piResult = piThis.shl(piThat);
                        break;
                    case SHR:
                        piResult = piThis.shr(piThat);
                        break;
                    case USHR:
                        piResult = piThis.ushr(piThat);
                        break;
                    default:
                        throw new IllegalStateException();
                    }
                return pool.ensureLiteralConstant(Format.IntLiteral, piResult.toString(this.getIntRadix()));
                }

            case "IntLiteral==IntLiteral":
            case "IntLiteral!=IntLiteral":
            case "IntLiteral<IntLiteral":
            case "IntLiteral<=IntLiteral":
            case "IntLiteral>IntLiteral":
            case "IntLiteral>=IntLiteral":
            case "IntLiteral<=>IntLiteral":
                {
                return translateOrder(getPackedInteger().cmp(((LiteralConstant) that).getPackedInteger()), op);
                }

            case "FPLiteral+IntLiteral":
            case "FPLiteral+FPLiteral":
            case "IntLiteral+FPLiteral":
                {
                String sLit;
                if (this.getFPRadix() == 2 || ((LiteralConstant) that).getFPRadix() == 2)
                    {
                    sLit = Double.toString(this.getDouble() + ((LiteralConstant) that).getDouble());
                    }
                else
                    {
                    BigDecimal decThis = this.getBigDecimal();
                    BigDecimal decThat = ((LiteralConstant) that).getBigDecimal();
                    sLit = decThis.add(decThat).toString().replace('E', 'P');
                    }
                return pool.ensureLiteralConstant(Format.FPLiteral, sLit);
                }

            case "FPLiteral-IntLiteral":
            case "FPLiteral-FPLiteral":
            case "IntLiteral-FPLiteral":
                {
                String sLit;
                if (this.getFPRadix() == 2 || ((LiteralConstant) that).getFPRadix() == 2)
                    {
                    sLit = Double.toString(this.getDouble() - ((LiteralConstant) that).getDouble());
                    }
                else
                    {
                    BigDecimal decThis = this.getBigDecimal();
                    BigDecimal decThat = ((LiteralConstant) that).getBigDecimal();
                    sLit = decThis.subtract(decThat).toString().replace('E', 'P');
                    }
                return pool.ensureLiteralConstant(Format.FPLiteral, sLit);
                }

            case "FPLiteral*IntLiteral":
            case "FPLiteral*FPLiteral":
            case "IntLiteral*FPLiteral":
                {
                String sLit;
                if (this.getFPRadix() == 2 || ((LiteralConstant) that).getFPRadix() == 2)
                    {
                    sLit = Double.toString(this.getDouble() * ((LiteralConstant) that).getDouble());
                    }
                else
                    {
                    BigDecimal decThis = this.getBigDecimal();
                    BigDecimal decThat = ((LiteralConstant) that).getBigDecimal();
                    sLit = decThis.multiply(decThat).toString().replace('E', 'P');
                    }
                return pool.ensureLiteralConstant(Format.FPLiteral, sLit);
                }

            case "FPLiteral/IntLiteral":
            case "FPLiteral/FPLiteral":
            case "IntLiteral/FPLiteral":
                {
                String sLit;
                if (this.getFPRadix() == 2 || ((LiteralConstant) that).getFPRadix() == 2)
                    {
                    sLit = Double.toString(this.getDouble() / ((LiteralConstant) that).getDouble());
                    }
                else
                    {
                    BigDecimal decThis = this.getBigDecimal();
                    BigDecimal decThat = ((LiteralConstant) that).getBigDecimal();
                    sLit = decThis.divide(decThat).toString().replace('E', 'P');
                    }
                return pool.ensureLiteralConstant(Format.FPLiteral, sLit);
                }

            case "FPLiteral==FPLiteral":
            case "FPLiteral!=FPLiteral":
            case "FPLiteral<FPLiteral":
            case "FPLiteral<=FPLiteral":
            case "FPLiteral>FPLiteral":
            case "FPLiteral>=FPLiteral":
            case "FPLiteral<=>FPLiteral":
                {
                int nOrd;
                if (this.getFPRadix() == 2 || ((LiteralConstant) that).getFPRadix() == 2)
                    {
                    double dflThis = this.getDouble();
                    double dflThat = ((LiteralConstant) that).getDouble();
                    // NaN has to be ordered somewhere (since we use a 3-state ordering and IEEE-754
                    // uses a 4-state ordering), so put it at "the beginning"
                    if (Double.isNaN(dflThis))
                        {
                        nOrd = Double.isNaN(dflThat) ? 0 : -1;
                        }
                    else if (Double.isNaN(dflThat))
                        {
                        nOrd = 1;
                        }
                    else
                        {
                        nOrd = Double.compare(dflThis, dflThat);
                        }
                    }
                else
                    {
                    BigDecimal decThis = this.getBigDecimal();
                    BigDecimal decThat = ((LiteralConstant) that).getBigDecimal();
                    nOrd = decThis.compareTo(decThat);
                    }
                return translateOrder(nOrd, op);
                }

            case "IntLiteral+Int8":
            case "IntLiteral-Int8":
            case "IntLiteral*Int8":
            case "IntLiteral/Int8":
            case "IntLiteral%Int8":
            case "IntLiteral&Int8":
            case "IntLiteral|Int8":
            case "IntLiteral^Int8":
            case "IntLiteral==Int8":
            case "IntLiteral!=Int8":
            case "IntLiteral<Int8":
            case "IntLiteral<=Int8":
            case "IntLiteral>Int8":
            case "IntLiteral>=Int8":
            case "IntLiteral<=>Int8":
                return this.toInt8Constant().apply(op, that);

            case "IntLiteral+UInt8":
            case "IntLiteral-UInt8":
            case "IntLiteral*UInt8":
            case "IntLiteral/UInt8":
            case "IntLiteral%UInt8":
            case "IntLiteral&UInt8":
            case "IntLiteral|UInt8":
            case "IntLiteral^UInt8":
            case "IntLiteral==UInt8":
            case "IntLiteral!=UInt8":
            case "IntLiteral<UInt8":
            case "IntLiteral<=UInt8":
            case "IntLiteral>UInt8":
            case "IntLiteral>=UInt8":
            case "IntLiteral<=>UInt8":
                return this.toUInt8Constant().apply(op, that);

            case "IntLiteral+Int16":
            case "IntLiteral-Int16":
            case "IntLiteral*Int16":
            case "IntLiteral/Int16":
            case "IntLiteral%Int16":
            case "IntLiteral&Int16":
            case "IntLiteral|Int16":
            case "IntLiteral^Int16":
            case "IntLiteral==Int16":
            case "IntLiteral!=Int16":
            case "IntLiteral<Int16":
            case "IntLiteral<=Int16":
            case "IntLiteral>Int16":
            case "IntLiteral>=Int16":
            case "IntLiteral<=>Int16":

            case "IntLiteral+Int32":
            case "IntLiteral-Int32":
            case "IntLiteral*Int32":
            case "IntLiteral/Int32":
            case "IntLiteral%Int32":
            case "IntLiteral&Int32":
            case "IntLiteral|Int32":
            case "IntLiteral^Int32":
            case "IntLiteral==Int32":
            case "IntLiteral!=Int32":
            case "IntLiteral<Int32":
            case "IntLiteral<=Int32":
            case "IntLiteral>Int32":
            case "IntLiteral>=Int32":
            case "IntLiteral<=>Int32":

            case "IntLiteral+Int64":
            case "IntLiteral-Int64":
            case "IntLiteral*Int64":
            case "IntLiteral/Int64":
            case "IntLiteral%Int64":
            case "IntLiteral&Int64":
            case "IntLiteral|Int64":
            case "IntLiteral^Int64":
            case "IntLiteral==Int64":
            case "IntLiteral!=Int64":
            case "IntLiteral<Int64":
            case "IntLiteral<=Int64":
            case "IntLiteral>Int64":
            case "IntLiteral>=Int64":
            case "IntLiteral<=>Int64":

            case "IntLiteral+Int128":
            case "IntLiteral-Int128":
            case "IntLiteral*Int128":
            case "IntLiteral/Int128":
            case "IntLiteral%Int128":
            case "IntLiteral&Int128":
            case "IntLiteral|Int128":
            case "IntLiteral^Int128":
            case "IntLiteral==Int128":
            case "IntLiteral!=Int128":
            case "IntLiteral<Int128":
            case "IntLiteral<=Int128":
            case "IntLiteral>Int128":
            case "IntLiteral>=Int128":
            case "IntLiteral<=>Int128":

            case "IntLiteral+VarInt":
            case "IntLiteral-VarInt":
            case "IntLiteral*VarInt":
            case "IntLiteral/VarInt":
            case "IntLiteral%VarInt":
            case "IntLiteral&VarInt":
            case "IntLiteral|VarInt":
            case "IntLiteral^VarInt":
            case "IntLiteral==VarInt":
            case "IntLiteral!=VarInt":
            case "IntLiteral<VarInt":
            case "IntLiteral<=VarInt":
            case "IntLiteral>VarInt":
            case "IntLiteral>=VarInt":
            case "IntLiteral<=>VarInt":

            case "IntLiteral+UInt16":
            case "IntLiteral-UInt16":
            case "IntLiteral*UInt16":
            case "IntLiteral/UInt16":
            case "IntLiteral%UInt16":
            case "IntLiteral&UInt16":
            case "IntLiteral|UInt16":
            case "IntLiteral^UInt16":
            case "IntLiteral==UInt16":
            case "IntLiteral!=UInt16":
            case "IntLiteral<UInt16":
            case "IntLiteral<=UInt16":
            case "IntLiteral>UInt16":
            case "IntLiteral>=UInt16":
            case "IntLiteral<=>UInt16":

            case "IntLiteral+UInt32":
            case "IntLiteral-UInt32":
            case "IntLiteral*UInt32":
            case "IntLiteral/UInt32":
            case "IntLiteral%UInt32":
            case "IntLiteral&UInt32":
            case "IntLiteral|UInt32":
            case "IntLiteral^UInt32":
            case "IntLiteral==UInt32":
            case "IntLiteral!=UInt32":
            case "IntLiteral<UInt32":
            case "IntLiteral<=UInt32":
            case "IntLiteral>UInt32":
            case "IntLiteral>=UInt32":
            case "IntLiteral<=>UInt32":

            case "IntLiteral+UInt64":
            case "IntLiteral-UInt64":
            case "IntLiteral*UInt64":
            case "IntLiteral/UInt64":
            case "IntLiteral%UInt64":
            case "IntLiteral&UInt64":
            case "IntLiteral|UInt64":
            case "IntLiteral^UInt64":
            case "IntLiteral==UInt64":
            case "IntLiteral!=UInt64":
            case "IntLiteral<UInt64":
            case "IntLiteral<=UInt64":
            case "IntLiteral>UInt64":
            case "IntLiteral>=UInt64":
            case "IntLiteral<=>UInt64":

            case "IntLiteral+UInt128":
            case "IntLiteral-UInt128":
            case "IntLiteral*UInt128":
            case "IntLiteral/UInt128":
            case "IntLiteral%UInt128":
            case "IntLiteral&UInt128":
            case "IntLiteral|UInt128":
            case "IntLiteral^UInt128":
            case "IntLiteral==UInt128":
            case "IntLiteral!=UInt128":
            case "IntLiteral<UInt128":
            case "IntLiteral<=UInt128":
            case "IntLiteral>UInt128":
            case "IntLiteral>=UInt128":
            case "IntLiteral<=>UInt128":

            case "IntLiteral+VarUInt":
            case "IntLiteral-VarUInt":
            case "IntLiteral*VarUInt":
            case "IntLiteral/VarUInt":
            case "IntLiteral%VarUInt":
            case "IntLiteral&VarUInt":
            case "IntLiteral|VarUInt":
            case "IntLiteral^VarUInt":
            case "IntLiteral==VarUInt":
            case "IntLiteral!=VarUInt":
            case "IntLiteral<VarUInt":
            case "IntLiteral<=VarUInt":
            case "IntLiteral>VarUInt":
            case "IntLiteral>=VarUInt":
            case "IntLiteral<=>VarUInt":
                return this.toIntConstant(that.getFormat()).apply(op, that);

            case "IntLiteral+Float16":
            case "IntLiteral-Float16":
            case "IntLiteral*Float16":
            case "IntLiteral/Float16":
            case "IntLiteral==Float16":
            case "IntLiteral!=Float16":
            case "IntLiteral<Float16":
            case "IntLiteral<=Float16":
            case "IntLiteral>Float16":
            case "IntLiteral>=Float16":
            case "IntLiteral<=>Float16":
            case "FPLiteral+Float16":
            case "FPLiteral-Float16":
            case "FPLiteral*Float16":
            case "FPLiteral/Float16":
            case "FPLiteral==Float16":
            case "FPLiteral!=Float16":
            case "FPLiteral<Float16":
            case "FPLiteral<=Float16":
            case "FPLiteral>Float16":
            case "FPLiteral>=Float16":
            case "FPLiteral<=>Float16":
                return this.toFloat16Constant().apply(op, that);

            case "IntLiteral+Float32":
            case "IntLiteral-Float32":
            case "IntLiteral*Float32":
            case "IntLiteral/Float32":
            case "IntLiteral==Float32":
            case "IntLiteral!=Float32":
            case "IntLiteral<Float32":
            case "IntLiteral<=Float32":
            case "IntLiteral>Float32":
            case "IntLiteral>=Float32":
            case "IntLiteral<=>Float32":
            case "FPLiteral+Float32":
            case "FPLiteral-Float32":
            case "FPLiteral*Float32":
            case "FPLiteral/Float32":
            case "FPLiteral==Float32":
            case "FPLiteral!=Float32":
            case "FPLiteral<Float32":
            case "FPLiteral<=Float32":
            case "FPLiteral>Float32":
            case "FPLiteral>=Float32":
            case "FPLiteral<=>Float32":
                return this.toFloat32Constant().apply(op, that);

            case "IntLiteral+Float64":
            case "IntLiteral-Float64":
            case "IntLiteral*Float64":
            case "IntLiteral/Float64":
            case "IntLiteral==Float64":
            case "IntLiteral!=Float64":
            case "IntLiteral<Float64":
            case "IntLiteral<=Float64":
            case "IntLiteral>Float64":
            case "IntLiteral>=Float64":
            case "IntLiteral<=>Float64":
            case "FPLiteral+Float64":
            case "FPLiteral-Float64":
            case "FPLiteral*Float64":
            case "FPLiteral/Float64":
            case "FPLiteral==Float64":
            case "FPLiteral!=Float64":
            case "FPLiteral<Float64":
            case "FPLiteral<=Float64":
            case "FPLiteral>Float64":
            case "FPLiteral>=Float64":
            case "FPLiteral<=>Float64":
                return this.toFloat64Constant().apply(op, that);

            case "IntLiteral+Float128":
            case "IntLiteral-Float128":
            case "IntLiteral*Float128":
            case "IntLiteral/Float128":
            case "IntLiteral==Float128":
            case "IntLiteral!=Float128":
            case "IntLiteral<Float128":
            case "IntLiteral<=Float128":
            case "IntLiteral>Float128":
            case "IntLiteral>=Float128":
            case "IntLiteral<=>Float128":
            case "FPLiteral+Float128":
            case "FPLiteral-Float128":
            case "FPLiteral*Float128":
            case "FPLiteral/Float128":
            case "FPLiteral==Float128":
            case "FPLiteral!=Float128":
            case "FPLiteral<Float128":
            case "FPLiteral<=Float128":
            case "FPLiteral>Float128":
            case "FPLiteral>=Float128":
            case "FPLiteral<=>Float128":
                return this.toFloat128Constant().apply(op, that);

            case "IntLiteral+VarFloat":
            case "IntLiteral-VarFloat":
            case "IntLiteral*VarFloat":
            case "IntLiteral/VarFloat":
            case "IntLiteral==VarFloat":
            case "IntLiteral!=VarFloat":
            case "IntLiteral<VarFloat":
            case "IntLiteral<=VarFloat":
            case "IntLiteral>VarFloat":
            case "IntLiteral>=VarFloat":
            case "IntLiteral<=>VarFloat":
            case "FPLiteral+VarFloat":
            case "FPLiteral-VarFloat":
            case "FPLiteral*VarFloat":
            case "FPLiteral/VarFloat":
            case "FPLiteral==VarFloat":
            case "FPLiteral!=VarFloat":
            case "FPLiteral<VarFloat":
            case "FPLiteral<=VarFloat":
            case "FPLiteral>VarFloat":
            case "FPLiteral>=VarFloat":
            case "FPLiteral<=>VarFloat":
                return this.toVarFloatConstant().apply(op, that);

            case "IntLiteral+Dec32":
            case "IntLiteral-Dec32":
            case "IntLiteral*Dec32":
            case "IntLiteral/Dec32":
            case "IntLiteral==Dec32":
            case "IntLiteral!=Dec32":
            case "IntLiteral<Dec32":
            case "IntLiteral<=Dec32":
            case "IntLiteral>Dec32":
            case "IntLiteral>=Dec32":
            case "IntLiteral<=>Dec32":
            case "FPLiteral+Dec32":
            case "FPLiteral-Dec32":
            case "FPLiteral*Dec32":
            case "FPLiteral/Dec32":
            case "FPLiteral==Dec32":
            case "FPLiteral!=Dec32":
            case "FPLiteral<Dec32":
            case "FPLiteral<=Dec32":
            case "FPLiteral>Dec32":
            case "FPLiteral>=Dec32":
            case "FPLiteral<=>Dec32":

            case "IntLiteral+Dec64":
            case "IntLiteral-Dec64":
            case "IntLiteral*Dec64":
            case "IntLiteral/Dec64":
            case "IntLiteral==Dec64":
            case "IntLiteral!=Dec64":
            case "IntLiteral<Dec64":
            case "IntLiteral<=Dec64":
            case "IntLiteral>Dec64":
            case "IntLiteral>=Dec64":
            case "IntLiteral<=>Dec64":
            case "FPLiteral+Dec64":
            case "FPLiteral-Dec64":
            case "FPLiteral*Dec64":
            case "FPLiteral/Dec64":
            case "FPLiteral==Dec64":
            case "FPLiteral!=Dec64":
            case "FPLiteral<Dec64":
            case "FPLiteral<=Dec64":
            case "FPLiteral>Dec64":
            case "FPLiteral>=Dec64":
            case "FPLiteral<=>Dec64":

            case "IntLiteral+Dec128":
            case "IntLiteral-Dec128":
            case "IntLiteral*Dec128":
            case "IntLiteral/Dec128":
            case "IntLiteral==Dec128":
            case "IntLiteral!=Dec128":
            case "IntLiteral<Dec128":
            case "IntLiteral<=Dec128":
            case "IntLiteral>Dec128":
            case "IntLiteral>=Dec128":
            case "IntLiteral<=>Dec128":
            case "FPLiteral+Dec128":
            case "FPLiteral-Dec128":
            case "FPLiteral*Dec128":
            case "FPLiteral/Dec128":
            case "FPLiteral==Dec128":
            case "FPLiteral!=Dec128":
            case "FPLiteral<Dec128":
            case "FPLiteral<=Dec128":
            case "FPLiteral>Dec128":
            case "FPLiteral>=Dec128":
            case "FPLiteral<=>Dec128":
                return this.toDecimalConstant(that.getFormat()).apply(op, that);

            case "IntLiteral+VarDec":
            case "IntLiteral-VarDec":
            case "IntLiteral*VarDec":
            case "IntLiteral/VarDec":
            case "IntLiteral==VarDec":
            case "IntLiteral!=VarDec":
            case "IntLiteral<VarDec":
            case "IntLiteral<=VarDec":
            case "IntLiteral>VarDec":
            case "IntLiteral>=VarDec":
            case "IntLiteral<=>VarDec":
            case "FPLiteral+VarDec":
            case "FPLiteral-VarDec":
            case "FPLiteral*VarDec":
            case "FPLiteral/VarDec":
            case "FPLiteral==VarDec":
            case "FPLiteral!=VarDec":
            case "FPLiteral<VarDec":
            case "FPLiteral<=VarDec":
            case "FPLiteral>VarDec":
            case "FPLiteral>=VarDec":
            case "FPLiteral<=>VarDec":
                return this.toVarDecConstant().apply(op, that);

            // case Date: // TODO can add a duration
            // case Time: // TODO can add a duration
            // case DateTime: // TODO can add a duration
            // case Duration: // TODO can add another duration
            // case TimeInterval: // TODO not sure why we even have this type - can you add another time interval? a duration?
            }

        return super.apply(op, that);
        }

    @Override
    public Constant convertTo(TypeConstant typeOut)
        {
        switch (this.getFormat().name() + "->" + typeOut.getEcstasyClassName())
            {
            case "IntLiteral->Bit":
                return toBitConstant();

            case "IntLiteral->Nibble":
                return toNibbleConstant();

            case "IntLiteral->Int8":
                return toInt8Constant();

            case "IntLiteral->UInt8":
                return toUInt8Constant();

            case "IntLiteral->Int16":
            case "IntLiteral->Int32":
            case "IntLiteral->Int64":
            case "IntLiteral->Int128":
            case "IntLiteral->VarInt":
            case "IntLiteral->UInt16":
            case "IntLiteral->UInt32":
            case "IntLiteral->UInt64":
            case "IntLiteral->UInt128":
            case "IntLiteral->VarUInt":
                return toIntConstant(Format.valueOf(typeOut.getEcstasyClassName()));

            case "IntLiteral->FPLiteral":
                return getConstantPool().ensureLiteralConstant(Format.FPLiteral, getValue());

            case "IntLiteral->Float16":
            case "FPLiteral->Float16":
                return toFloat16Constant();

            case "IntLiteral->Float32":
            case "FPLiteral->Float32":
                return toFloat32Constant();

            case "IntLiteral->Float64":
            case "FPLiteral->Float64":
                return toFloat64Constant();

            case "IntLiteral->Float128":
            case "FPLiteral->Float128":
                return toFloat128Constant();

            case "IntLiteral->VarFloat":
            case "FPLiteral->VarFloat":
                return toVarFloatConstant();

            case "IntLiteral->Dec32":
            case "IntLiteral->Dec64":
            case "IntLiteral->Dec128":
            case "FPLiteral->Dec32":
            case "FPLiteral->Dec64":
            case "FPLiteral->Dec128":
                return toDecimalConstant(Format.valueOf(typeOut.getEcstasyClassName()));

            case "IntLiteral->VarDec":
            case "FPLiteral->VarDec":
                return toVarDecConstant();
            }

        // handle conversions to unpredictable interface types
        ConstantPool pool = getConstantPool();
        switch (this.getFormat().name())
            {
            case "IntLiteral":
                if (pool.typeInt().isA(typeOut))
                    {
                    return toIntConstant(Format.Int64);
                    }
                else if (pool.typeByte().isA(typeOut))
                    {
                    return toUInt8Constant();
                    }
                else
                    {
                    // go through the entire list of possibilities
                    for (Format format = Format.Bit;
                            format.ordinal() <= Format.VarDec.ordinal(); format = format.next())
                        {
                        TypeConstant typeSupported = pool.ensureEcstasyTypeConstant(format.name());
                        if (typeSupported.isA(typeOut))
                            {
                            return convertTo(typeSupported);
                            }
                        }
                    }
                break;

            case "FPLiteral":
                // go through the entire list of possibilities
                for (Format format = Format.Float16;
                        format.ordinal() <= Format.VarDec.ordinal(); format = format.next())
                    {
                    TypeConstant typeSupported = pool.ensureEcstasyTypeConstant(format.name());
                    if (typeSupported.isA(typeOut))
                        {
                        return convertTo(typeSupported);
                        }
                    }
                break;

            default:
                // TODO
                throw new UnsupportedOperationException("TODO conversion");
            }

        return super.convertTo(typeOut);
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
