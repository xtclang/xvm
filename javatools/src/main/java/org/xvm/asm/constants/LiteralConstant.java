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
     * Construct a constant whose value is a literal.
     *
     * @param pool    the ConstantPool that will contain this Constant
     * @param format  the Constant Format
     * @param sVal    the literal value
     * @param oVal    (optional) the format-specific value
     */
    public LiteralConstant(ConstantPool pool, Format format, String sVal, Object oVal)
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

            case Version:
                if (!(this instanceof VersionConstant))
                    {
                    throw new IllegalStateException("version requires VersionConstant subclass");
                    }
                break;

            case Path:
                // TODO
                break;

            default:
                throw new IllegalStateException("unsupported format: " + format);
            }

        m_constStr = pool.ensureStringConstant(sVal);
        m_oVal     = oVal;
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
            case Version:
            case Path:
                break;

            default:
                throw new IllegalStateException("unsupported format: " + format);
            }

        m_fmt = format;
        }

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

    @Override
    protected void resolveConstants()
        {
        m_constStr = (StringConstant) getConstantPool().getConstant(m_iStr);
        }


    // ----- type-specific functionality -----------------------------------------------------------


    @Override
    public TypeConstant getType()
        {
        ConstantPool pool = getConstantPool();
        switch (m_fmt)
            {
            case IntLiteral:
                return pool.typeIntLiteral();

            case FPLiteral:
                return pool.typeFPLiteral();

            case Date:
                return pool.typeDate();

            case Time:
                return pool.typeTime();

            case DateTime:
                return pool.typeDateTime();

            case Duration:
                return pool.typeDuration();

            case Version:
                return pool.typeVersion();

            case Path:
                return pool.typePath();

            default:
                return super.getType();
            }
        }

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
     * @return the underlying string constant
     */
    public StringConstant getStringConstant()
        {
        return m_constStr;
        }

     /**
     * Obtain the radix of the numeric literal.
     * <p/>
     * This must not be called if the constant is not an IntLiteral or FPLiteral.
     *
     * @return the radix of an IntLiteral
     */
    public int getRadix()
        {
        assert getFormat() == Format.IntLiteral || getFormat() == Format.FPLiteral;

        // if the first character is '0', it is potentially part of a prefix denoting a radix
        // (note that the literal may begin with a minus sign)
        String s  = getValue();
        int    of = s.startsWith("-") ? 1 : 0;
        if (s.length() > of+2 && s.charAt(of) == '0')
            {
            switch (s.charAt(of+1))
                {
                case 'B':
                case 'b':
                    assert getFormat() == Format.IntLiteral;
                    return 2;
                case 'o':
                    assert getFormat() == Format.IntLiteral;
                    return 8;
                case 'X':
                case 'x':
                    return 16;
                }
            }

        return 10;
        }

    private int pickFPRadixForNumericOperation(LiteralConstant const1, LiteralConstant const2)
        {
        assert const1.getFormat() == Format.FPLiteral || const2.getFormat() == Format.FPLiteral;
        return const1.getRadix() == 16 && const2.getRadix() == 16
                ? 16
                : 10;
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

        PackedInteger pint = m_oVal instanceof PackedInteger ? (PackedInteger) m_oVal : null;
        if (pint == null)
            {
            String s = getValue().replace("_", ""); // TODO all of the lexer logic has to show up here
            int    r = getRadix();
            if (r == 10)
                {
                pint = s.length() < 20
                        ? PackedInteger.valueOf(Long.parseLong(s))
                        : new PackedInteger(new BigInteger(s));
                }
            else
                {
                boolean fNeg = s.startsWith("-");
                int     of   = fNeg ? 1 : 0;
                String  sNum = (fNeg ? "-" : "") + s.substring(of+2);
                pint = s.length() < of+2 + (64 / Integer.numberOfTrailingZeros(r))
                        ? new PackedInteger(Long.parseLong(sNum, r))
                        : new PackedInteger(new BigInteger(sNum, r));
                }
            m_oVal = pint;
            }

        return pint;
        }

    /**
     * Obtain the BigDecimal value of the floating point literal.
     * <p/>
     * This must not be called if the constant is not an IntLiteral or an FPLiteral.
     *
     * @return the BigDecimal value of the floating point literal
     */
    public BigDecimal getBigDecimal()
        {
        assert getFormat() == Format.IntLiteral || getFormat() == Format.FPLiteral;

        BigDecimal dec = m_oVal instanceof BigDecimal ? (BigDecimal) m_oVal : null;
        if (dec == null)
            {
            if (getFormat() == Format.IntLiteral)
                {
                dec = new BigDecimal(getPackedInteger().getBigInteger());
                }
            else
                {
                String sLit = m_constStr.getValue();
                // note: BigDecimal does not support hexadecimal representation of FP numbers
                dec = getRadix() == 16
                        ? BigDecimal.valueOf(Double.valueOf(sLit))
                        : new BigDecimal(sLit);
                }
            m_oVal = dec;
            }

        return dec;
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
        if (m_oVal instanceof Float)
            {
            return (Float) m_oVal;
            }

        float fl = getFormat() == Format.IntLiteral
                ? getPackedInteger().getBigInteger().floatValue()
                : Float.parseFloat(m_constStr.getValue());

        m_oVal = fl;
        return fl;
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
        if (m_oVal instanceof Double)
            {
            return (Double) m_oVal;
            }

        double dfl = getFormat() == Format.IntLiteral
                ? getPackedInteger().getBigInteger().doubleValue()
                : Double.parseDouble(m_constStr.getValue());

        m_oVal = dfl;
        return dfl;
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
     * @return the equivalent BFloat16Constant for this LiteralConstant
     */
    public BFloat16Constant toBFloat16Constant()
        {
        assert m_fmt == Format.IntLiteral || m_fmt == Format.FPLiteral;
        return getConstantPool().ensureBFloat16Constant(getFloat());
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

        byte[] ab = null;

        if (m_fmt == Format.IntLiteral)
            {
            PackedInteger pi = getPackedInteger();
            if (!pi.isBig())
                {
                long l = pi.getLong();
                if (l == 0)
                    {
                    ab = new byte[16];
                    if (getValue().startsWith("-"))
                        {
                        ab[0] = (byte) 0x80;
                        }
                    }
                }
            }

        // TODO

        if (ab != null)
            {
            return getConstantPool().ensureFloat128Constant(ab);
            }

        throw new UnsupportedOperationException();
        }

    /**
     * @return the equivalent FPNConstant of type FloatN for this LiteralConstant holding a
     *         FPLiteral value
     */
    public FPNConstant toFloatNConstant()
        {
        assert m_fmt == Format.IntLiteral || m_fmt == Format.FPLiteral;

        byte[] ab = null;

        if (m_fmt == Format.IntLiteral)
            {
            PackedInteger pi = getPackedInteger();
            if (!pi.isBig())
                {
                long l = pi.getLong();
                if (l == 0)
                    {
                    ab = new byte[2];
                    if (getValue().startsWith("-"))
                        {
                        ab[0] = (byte) 0x80;
                        }
                    }
                }
            }

        // TODO

        if (ab != null)
            {
            return getConstantPool().ensureFloatNConstant(ab);
            }

        throw new UnsupportedOperationException();
        }

    /**
     * @return the equivalent DecimalConstant of the specified format for this LiteralConstant
     *         holding a FPLiteral value
     */
    public DecimalConstant toDecimalConstant(Format format)
        {
        assert m_fmt == Format.IntLiteral || m_fmt == Format.FPLiteral;
        return getConstantPool().ensureDecConstant(getDecimal(format));
        }

    /**
     * @return the equivalent FPNConstant of type FloatN for this LiteralConstant holding a
     *         FPLiteral value
     */
    public FPNConstant toDecNConstant()
        {
        assert m_fmt == Format.IntLiteral || m_fmt == Format.FPLiteral;

        byte[] ab = null;

        if (m_fmt == Format.IntLiteral)
            {
            PackedInteger pi = getPackedInteger();
            if (!pi.isBig())
                {
                long l = pi.getLong();
                if (l == 0)
                    {
                    ab = new byte[4];
                    if (getValue().startsWith("-"))
                        {
                        ab[0] = (byte) 0x80;
                        }
                    }
                }
            }

        // TODO

        if (ab != null)
            {
            return getConstantPool().ensureDecNConstant(ab);
            }

        throw new UnsupportedOperationException();
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return m_fmt;
        }

    @Override
    public boolean containsUnresolved()
        {
        return m_constStr.containsUnresolved();
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
                return pool.ensureLiteralConstant(Format.IntLiteral, piResult.toString(this.getRadix()));
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
            case "IntLiteral+FPLiteral":
            case "FPLiteral+FPLiteral":
                {
                LiteralConstant litThat = (LiteralConstant) that;
                String sLit = pickFPRadixForNumericOperation(this, litThat) == 16
                        ? Double.toHexString(this.getDouble() + litThat.getDouble())
                        : this.getBigDecimal().add(litThat.getBigDecimal()).toString();
                return pool.ensureLiteralConstant(Format.FPLiteral, sLit);
                }

            case "FPLiteral-IntLiteral":
            case "FPLiteral-FPLiteral":
            case "IntLiteral-FPLiteral":
                {
                LiteralConstant litThat = (LiteralConstant) that;
                String sLit = pickFPRadixForNumericOperation(this, litThat) == 16
                        ? Double.toHexString(this.getDouble() - litThat.getDouble())
                        : this.getBigDecimal().subtract(litThat.getBigDecimal()).toString();
                return pool.ensureLiteralConstant(Format.FPLiteral, sLit);
                }

            case "FPLiteral*IntLiteral":
            case "FPLiteral*FPLiteral":
            case "IntLiteral*FPLiteral":
                {
                LiteralConstant litThat = (LiteralConstant) that;
                String sLit = pickFPRadixForNumericOperation(this, litThat) == 16
                        ? Double.toHexString(this.getDouble() * litThat.getDouble())
                        : this.getBigDecimal().multiply(litThat.getBigDecimal()).toString();
                return pool.ensureLiteralConstant(Format.FPLiteral, sLit);
                }

            case "FPLiteral/IntLiteral":
            case "FPLiteral/FPLiteral":
            case "IntLiteral/FPLiteral":
                {
                LiteralConstant litThat = (LiteralConstant) that;
                String sLit = pickFPRadixForNumericOperation(this, litThat) == 16
                        ? Double.toHexString(this.getDouble() / litThat.getDouble())
                        : this.getBigDecimal().divide(litThat.getBigDecimal()).toString();
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
                LiteralConstant litThat = (LiteralConstant) that;
                int nOrd;
                if (pickFPRadixForNumericOperation(this, litThat) == 16)
                    {
                    double dflThis = this.getDouble();
                    double dflThat = litThat.getDouble();
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
                    nOrd = this.getBigDecimal().compareTo(litThat.getBigDecimal());
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

            case "IntLiteral+IntN":
            case "IntLiteral-IntN":
            case "IntLiteral*IntN":
            case "IntLiteral/IntN":
            case "IntLiteral%IntN":
            case "IntLiteral&IntN":
            case "IntLiteral|IntN":
            case "IntLiteral^IntN":
            case "IntLiteral==IntN":
            case "IntLiteral!=IntN":
            case "IntLiteral<IntN":
            case "IntLiteral<=IntN":
            case "IntLiteral>IntN":
            case "IntLiteral>=IntN":
            case "IntLiteral<=>IntN":

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

            case "IntLiteral+UIntN":
            case "IntLiteral-UIntN":
            case "IntLiteral*UIntN":
            case "IntLiteral/UIntN":
            case "IntLiteral%UIntN":
            case "IntLiteral&UIntN":
            case "IntLiteral|UIntN":
            case "IntLiteral^UIntN":
            case "IntLiteral==UIntN":
            case "IntLiteral!=UIntN":
            case "IntLiteral<UIntN":
            case "IntLiteral<=UIntN":
            case "IntLiteral>UIntN":
            case "IntLiteral>=UIntN":
            case "IntLiteral<=>UIntN":
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

            case "IntLiteral+BFloat16":
            case "IntLiteral-BFloat16":
            case "IntLiteral*BFloat16":
            case "IntLiteral/BFloat16":
            case "IntLiteral==BFloat16":
            case "IntLiteral!=BFloat16":
            case "IntLiteral<BFloat16":
            case "IntLiteral<=BFloat16":
            case "IntLiteral>BFloat16":
            case "IntLiteral>=BFloat16":
            case "IntLiteral<=>BFloat16":
            case "FPLiteral+BFloat16":
            case "FPLiteral-BFloat16":
            case "FPLiteral*BFloat16":
            case "FPLiteral/BFloat16":
            case "FPLiteral==BFloat16":
            case "FPLiteral!=BFloat16":
            case "FPLiteral<BFloat16":
            case "FPLiteral<=BFloat16":
            case "FPLiteral>BFloat16":
            case "FPLiteral>=BFloat16":
            case "FPLiteral<=>BFloat16":
                return this.toBFloat16Constant().apply(op, that);

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

            case "IntLiteral+FloatN":
            case "IntLiteral-FloatN":
            case "IntLiteral*FloatN":
            case "IntLiteral/FloatN":
            case "IntLiteral==FloatN":
            case "IntLiteral!=FloatN":
            case "IntLiteral<FloatN":
            case "IntLiteral<=FloatN":
            case "IntLiteral>FloatN":
            case "IntLiteral>=FloatN":
            case "IntLiteral<=>FloatN":
            case "FPLiteral+FloatN":
            case "FPLiteral-FloatN":
            case "FPLiteral*FloatN":
            case "FPLiteral/FloatN":
            case "FPLiteral==FloatN":
            case "FPLiteral!=FloatN":
            case "FPLiteral<FloatN":
            case "FPLiteral<=FloatN":
            case "FPLiteral>FloatN":
            case "FPLiteral>=FloatN":
            case "FPLiteral<=>FloatN":
                return this.toFloatNConstant().apply(op, that);

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

            case "IntLiteral+DecN":
            case "IntLiteral-DecN":
            case "IntLiteral*DecN":
            case "IntLiteral/DecN":
            case "IntLiteral==DecN":
            case "IntLiteral!=DecN":
            case "IntLiteral<DecN":
            case "IntLiteral<=DecN":
            case "IntLiteral>DecN":
            case "IntLiteral>=DecN":
            case "IntLiteral<=>DecN":
            case "FPLiteral+DecN":
            case "FPLiteral-DecN":
            case "FPLiteral*DecN":
            case "FPLiteral/DecN":
            case "FPLiteral==DecN":
            case "FPLiteral!=DecN":
            case "FPLiteral<DecN":
            case "FPLiteral<=DecN":
            case "FPLiteral>DecN":
            case "FPLiteral>=DecN":
            case "FPLiteral<=>DecN":
                return this.toDecNConstant().apply(op, that);

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
        String sSimpleName = typeOut.getEcstasyClassName();
        int    ofDot       = sSimpleName.lastIndexOf('.');
        if (ofDot > 0)
            {
            sSimpleName = sSimpleName.substring(ofDot + 1);
            }

        switch (this.getFormat().name() + "->" + sSimpleName)
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
            case "IntLiteral->IntN":
            case "IntLiteral->UInt16":
            case "IntLiteral->UInt32":
            case "IntLiteral->UInt64":
            case "IntLiteral->UInt128":
            case "IntLiteral->UIntN":
                return toIntConstant(Format.valueOf(sSimpleName));

            case "IntLiteral->FPLiteral":
                return getConstantPool().ensureLiteralConstant(Format.FPLiteral, getValue());

            case "IntLiteral->BFloat16":
            case "FPLiteral->BFloat16":
                return toBFloat16Constant();

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

            case "IntLiteral->FloatN":
            case "FPLiteral->FloatN":
                return toFloatNConstant();

            case "IntLiteral->Dec32":
            case "IntLiteral->Dec64":
            case "IntLiteral->Dec128":
            case "FPLiteral->Dec32":
            case "FPLiteral->Dec64":
            case "FPLiteral->Dec128":
                return toDecimalConstant(Format.valueOf(sSimpleName));

            case "IntLiteral->DecN":
            case "FPLiteral->DecN":
                return toDecNConstant();
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
                            format.ordinal() <= Format.DecN.ordinal(); format = format.next())
                        {
                        TypeConstant typeSupported = pool.ensureEcstasyTypeConstant(
                                format.getEcstasyName());
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
                        format.ordinal() <= Format.DecN.ordinal(); format = format.next())
                    {
                    TypeConstant typeSupported = pool.ensureEcstasyTypeConstant(
                            format.getEcstasyName());
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
        if (!(that instanceof LiteralConstant))
            {
            return -1;
            }
        return this.getValue().compareTo(((LiteralConstant) that).getValue());
        }

    @Override
    public String getValueString()
        {
        return '\"' + getValue() + '\"';
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        m_constStr = (StringConstant) pool.register(m_constStr);
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

    /**
     * Cached value.
     */
    private transient Object m_oVal;
    }
