package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

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
