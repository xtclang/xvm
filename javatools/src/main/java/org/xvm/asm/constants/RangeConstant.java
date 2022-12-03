package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.function.Consumer;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;

import org.xvm.compiler.Token.Id;
import org.xvm.util.Hash;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Represent a range of two constant values.
 */
public class RangeConstant
        extends ValueConstant
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a constant whose value is a range or interval.
     *
     * @param pool    the ConstantPool that will contain this Constant
     * @param const1  the value of the first constant
     * @param const2  the value of the second constant
     */
    public RangeConstant(ConstantPool pool, Constant const1, boolean fExclude1, Constant const2, boolean fExclude2)
        {
        super(pool);

        if (const1 == null)
            {
            throw new IllegalArgumentException("value 1 required");
            }
        if (const2 == null)
            {
            throw new IllegalArgumentException("value 2 required");
            }
        if (const1.getFormat() != const2.getFormat() && !const1.getType().equals(const2.getType()))
            {
            throw new IllegalArgumentException("values must be of the same type");
            }

        m_const1    = const1;
        m_fExclude1 = fExclude1;
        m_const2    = const2;
        m_fExclude2 = fExclude2;
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
    public RangeConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool);

        switch (format)
            {
            case RangeExclusive:
                m_fExclude1 = false;
                m_fExclude2 = true;
                break;

            case RangeInclusive:
                m_fExclude1 = false;
                m_fExclude2 = false;
                break;

            case Range:
                int b = in.readUnsignedByte();
                m_fExclude1 = (b & 1) != 0;
                m_fExclude2 = (b & 2) != 0;
                break;

            default:
                throw new IllegalStateException("illegal format: " + format);
            }

        m_iVal1 = readMagnitude(in);
        m_iVal2 = readMagnitude(in);
        }

    @Override
    protected void resolveConstants()
        {
        ConstantPool pool = getConstantPool();

        m_const1 = pool.getConstant(m_iVal1);
        m_const2 = pool.getConstant(m_iVal2);
        }


    // ----- type-specific methods -----------------------------------------------------------------

    /**
     * @return  the first constant in the range
     */
    public Constant getFirst()
        {
        return m_const1;
        }

    /**
     * @return true iff the first value is excluded from the range
     */
    public boolean isFirstExcluded()
        {
        return m_fExclude1;
        }

    /**
     * @return  the last constant in the range
     */
    public Constant getLast()
        {
        return m_const2;
        }

    /**
     * @return true iff the last value is excluded from the range
     */
    public boolean isLastExcluded()
        {
        return m_fExclude2;
        }

    /**
     * For a value of the type of the values defining the extent of this range, determine if that
     * value would be found inside of this range.
     *
     * @param value  a value that might be found within this range
     *
     * @return true iff the value is found within this range
     */
    public boolean contains(Constant value)
        {
        if (value.equals(m_const1))
            {
            return !m_fExclude1 && !(value.equals(m_const2) && m_fExclude2);
            }

        if (value.equals(m_const2))
            {
            return !m_fExclude2;
            }

        switch (Integer.signum(m_const1.compareTo(m_const2)))
            {
            case -1:
                return value.compareTo(m_const1) >= 0 && value.compareTo(m_const2) <= 0;

            default:
            case 0:
                return false;

            case 1:
                return value.compareTo(m_const2) >= 0 && value.compareTo(m_const1) <= 0;
            }
        }

    /**
     * @return  true iff the last constant in the range is ordered before the first constant in
     *          the range
     */
    public boolean isReverse()
        {
        // only indicate "Reverse" if the first constant is greater than the second constant when
        // they are compared; apply() must return either valTrue() or valFalse() for this op
        return getConstantPool().valTrue().equals(m_const1.apply(Id.COMP_GT, m_const2));
        }

    // ----- Interval-specific support -------------------------------------------------------------

    /**
     * @return true iff the Range is an Interval (ie a range of Sequential type)
     */
    public boolean isInterval()
        {
        return m_const1 instanceof EnumValueConstant ||
               m_const1.getType().isA(getConstantPool().typeSequential());
        }

    /**
     * @return the number of elements in the interval (inclusive)
     */
    public long size()
        {
        assert isInterval();

        Constant constFirst = getEffectiveFirst();
        Constant constLast  = getEffectiveLast();
        boolean  fReverse   = isReverse();
        Constant constLo    = fReverse ? constLast : constFirst;
        Constant constHi    = fReverse ? constFirst : constLast;

        Constant constGT = constLo.apply(Id.COMP_GT, constHi);
        if (constGT.equals(constGT.getConstantPool().valTrue()))
            {
            return m_fExclude1 || m_fExclude2 ? 0 : 1;
            }

        int nOff = m_fExclude1 ^ m_fExclude2 ?  0 :
                   m_fExclude1               ? -1 : // both excluded
                                                1 ; // both included
        long lSize;
        try
            {
            lSize = constHi.apply(Id.SUB, constLo).getIntValue().getLong() + nOff;
            }
        catch (RuntimeException e)
            {
            lSize = constHi.getIntValue().sub(constLo.getIntValue()).getLong() + nOff;
            }

        return lSize;
        }

    /**
     * @return the effective first value in the interval (ie if it were inclusive)
     */
    public Constant getEffectiveFirst()
        {
        assert isInterval();

        Constant constFirst = getFirst();
        return isFirstExcluded()
                ? constFirst.apply(isReverse() ? Id.SUB : Id.ADD, getConstantPool().ensureLiteralConstant(Format.IntLiteral, "1"))
                : constFirst;
        }

    /**
     * @return the effective last value in the interval (ie if it were inclusive)
     */
    public Constant getEffectiveLast()
        {
        assert isInterval();

        Constant constLast = getLast();
        return isLastExcluded()
            ? constLast.apply(isReverse() ? Id.ADD : Id.SUB, getConstantPool().ensureLiteralConstant(Format.IntLiteral, "1"))
            : constLast;
        }


    // ----- ValueConstant methods -----------------------------------------------------------------

    @Override
    public TypeConstant getType()
        {
        TypeConstant type1 = m_const1.getType();
        TypeConstant type2 = m_const2.getType();
        if (type1.equals(type2))
            {
            return getConstantPool().ensureRangeType(type1);
            }
        if (m_const1 instanceof EnumValueConstant enumVal1 &&
            m_const2 instanceof EnumValueConstant enumVal2)
            {
            IdentityConstant idEnum1 = enumVal1.getClassConstant().getParentConstant();
            IdentityConstant idEnum2 = enumVal2.getClassConstant().getParentConstant();

            if (idEnum1.equals(idEnum2))
                {
                return getConstantPool().ensureRangeType(idEnum1.getType());
                }
            }
        throw new IllegalStateException("Non-uniform range " + this);
        }

    /**
     * {@inheritDoc}
     * @return  the constant's contents as an array of two constants
     */
    @Override
    public Constant[] getValue()
        {
        return new Constant[] {m_const1, m_const2};
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.Range;
        }

    @Override
    public boolean containsUnresolved()
        {
        return !isHashCached() && (m_const1.containsUnresolved() || m_const2.containsUnresolved());
        }

    @Override
    public void forEachUnderlying(Consumer<Constant> visitor)
        {
        visitor.accept(m_const1);
        visitor.accept(m_const2);
        }

    @Override
    public RangeConstant resolveTypedefs()
        {
        Constant constOld1 = m_const1;
        Constant constOld2 = m_const2;
        Constant constNew1 = constOld1.resolveTypedefs();
        Constant constNew2 = constOld2.resolveTypedefs();
        return constNew1 == constOld1 && constNew2 == constOld2
                ? this
                : getConstantPool().ensureRangeConstant(constNew1, constNew2);
        }

    @Override
    protected int compareDetails(Constant that)
        {
        if (!(that instanceof RangeConstant range))
            {
            return -1;
            }

        int nResult = this.m_const1.compareTo(range.m_const1);
        if (nResult == 0)
            {
            if (this.m_fExclude1 != range.m_fExclude1)
                {
                nResult = m_fExclude1 ? 1 : -1;
                }
            else
                {
                nResult = this.m_const2.compareTo(range.m_const2);
                if (nResult == 0)
                    {
                    if (this.m_fExclude2 != range.m_fExclude2)
                        {
                        nResult = m_fExclude2 ? -1 : 1;
                        }
                    }
                }
            }
        return nResult;
        }

    @Override
    public String getValueString()
        {
        return (m_fExclude1 ? '(' : '[') + m_const1.getValueString()
            + ".." + m_const2.getValueString() + (m_fExclude2 ? ')' : ']');
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        m_const1 = pool.register(m_const1);
        m_const2 = pool.register(m_const2);
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        if (!m_fExclude1)
            {
            out.writeByte((m_fExclude2 ? Format.RangeExclusive : Format.RangeInclusive).ordinal());
            }
        else
            {
            out.writeByte(Format.Range.ordinal());
            out.writeByte((m_fExclude1 ? 1 : 0) | (m_fExclude2 ? 2 : 0));
            }

        writePackedLong(out, m_const1.getPosition());
        writePackedLong(out, m_const2.getPosition());
        }

    @Override
    public String getDescription()
        {
        return getValueString();
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int computeHashCode()
        {
        return Hash.of(m_const1,
               Hash.of(m_const2,
               Hash.of(m_fExclude1,
               Hash.of(m_fExclude2))));
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * Holds the index of the first value during deserialization.
     */
    private transient int m_iVal1;

    /**
     * Holds the index of the second value during deserialization.
     */
    private transient int m_iVal2;

    /**
     * The first value of the range.
     */
    private Constant m_const1;

    /**
     * True iff the first value of the range is excluded.
     */
    private final boolean m_fExclude1;

    /**
     * The second value of the range.
     */
    private Constant m_const2;

    /**
     * True iff the second value of the range is excluded.
     */
    private final boolean m_fExclude2;
    }