package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.function.Consumer;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;

import org.xvm.compiler.Token.Id;

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
    public RangeConstant(ConstantPool pool, Constant const1, Constant const2)
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

        m_const1 = const1;
        m_const2 = const2;
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
     * @return  the last constant in the range
     */
    public Constant getLast()
        {
        return m_const2;
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
        if (value.equals(m_const1) || value.equals(m_const2))
            {
            return true;
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


    // ----- ValueConstant methods -----------------------------------------------------------------

    @Override
    public TypeConstant getType()
        {
        ConstantPool pool = getConstantPool();
        return pool.ensureParameterizedTypeConstant(pool.typeRange(), m_const1.getType());
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
        return m_const1.containsUnresolved() || m_const2.containsUnresolved();
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
                : getConstantPool().ensureIntervalConstant(constNew1, constNew2);
        }

    @Override
    protected int compareDetails(Constant that)
        {
        if (!(that instanceof RangeConstant))
            {
            return -1;
            }
        int nResult = this.m_const1.compareTo(((RangeConstant) that).m_const1);
        if (nResult == 0)
            {
            nResult = this.m_const2.compareTo(((RangeConstant) that).m_const2);
            }
        return nResult;
        }

    @Override
    public String getValueString()
        {
        return m_const1.getValueString() + ".." + m_const2.getValueString();
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
        out.writeByte(getFormat().ordinal());
        writePackedLong(out, m_const1.getPosition());
        writePackedLong(out, m_const2.getPosition());
        }

    @Override
    public String getDescription()
        {
        return "lower=" + m_const1.getValueString() + ", upper=" + m_const2.getValueString();
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        return m_const1.hashCode() ^ m_const2.hashCode();
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
     * The second value of the range.
     */
    private Constant m_const2;
    }

