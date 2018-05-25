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
 * Represent an interval of two constant values.
 */
public class IntervalConstant
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
    public IntervalConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool);

        m_iVal1 = readMagnitude(in);
        m_iVal2 = readMagnitude(in);
        }

    /**
     * Construct a constant whose value is an interval or range.
     *
     * @param pool    the ConstantPool that will contain this Constant
     * @param const1  the value of the first constant
     * @param const2  the value of the second constant
     */
    public IntervalConstant(ConstantPool pool, Constant const1, Constant const2)
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


    // ----- type-specific methods -----------------------------------------------------------------

    /**
     * @return  the first constant in the interval
     */
    public Constant getFirst()
        {
        return m_const1;
        }

    /**
     * @return  the last constant in the interval
     */
    public Constant getLast()
        {
        return m_const2;
        }

    /**
     * @return  true iff the last constant in the interval is ordered before the first constant in
     *          the interval
     */
    public boolean isReverse()
        {
        // only indicate "Reverse" if the first constant is greater than the second constant when
        // they are compared; apply() must return either valTrue() or valFalse() for this op
        return m_const1.apply(Id.COMP_GT, m_const2) == getConstantPool().valTrue();
        }

    /**
     * Helper.
     *
     * @param constVal  a value of an element in the interval
     *
     * @return the TypeConstant for the interval (or range)
     */
    public static TypeConstant getIntervalTypeFor(Constant constVal)
        {
        ConstantPool pool = constVal.getConstantPool();
        TypeConstant typeInterval;
        switch (constVal.getFormat())
            {
            case IntLiteral:
            case Bit:
            case Nibble:
            case Int8:
            case Int16:
            case Int32:
            case Int64:
            case Int128:
            case VarInt:
            case UInt8:
            case UInt16:
            case UInt32:
            case UInt64:
            case UInt128:
            case VarUInt:
            case Date:
            case Char:
            case SingletonConst:
                typeInterval = pool.typeRange();
                break;

            default:
                typeInterval = pool.typeInterval();
                break;
            }

        return pool.ensureParameterizedTypeConstant(typeInterval, constVal.getType());
        }

    /**
     * Helper.
     *
     * @param type  the type of an element in the interval
     *
     * @return the TypeConstant for the interval (or range)
     */
    public static TypeConstant getIntervalTypeFor(TypeConstant type)
        {
        ConstantPool pool = type.getConstantPool();
        TypeConstant typeInterval;
        switch (type.getEcstasyClassName())
            {
            case "IntLiteral":
            case "Bit":
            case "Nibble":
            case "Int8":
            case "Int16":
            case "Int32":
            case "Int64":
            case "Int128":
            case "VarInt":
            case "UInt8":
            case "UInt16":
            case "UInt32":
            case "UInt64":
            case "UInt128":
            case "VarUInt":
            case "Date":
            case "Char":
            case "Boolean":
            case "Ordered":
                typeInterval = pool.typeRange();
                break;

            case "String":
            case "FPLiteral":
            case "Dec32":
            case "Dec64":
            case "Dec128":
            case "VarDec":
            case "Float16":
            case "Float32":
            case "Float64":
            case "Float128":
            case "VarFloat":
                typeInterval = pool.typeInterval();
                break;

            default:
                typeInterval = type.isA(pool.typeSequential())
                        ? pool.typeRange()
                        : pool.typeInterval();
                break;
            }

        return pool.ensureParameterizedTypeConstant(typeInterval, type);
        }


    // ----- ValueConstant methods -----------------------------------------------------------------

    @Override
    public TypeConstant getType()
        {
        return getIntervalTypeFor(m_const1);
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
        return Format.Interval;
        }

    @Override
    public void forEachUnderlying(Consumer<Constant> visitor)
        {
        visitor.accept(m_const1);
        visitor.accept(m_const2);
        }

    @Override
    public IntervalConstant resolveTypedefs()
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
        int nResult = this.m_const1.compareTo(((IntervalConstant) that).m_const1);
        if (nResult == 0)
            {
            nResult = this.m_const2.compareTo(((IntervalConstant) that).m_const2);
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
    protected void disassemble(DataInput in)
            throws IOException
        {
        final ConstantPool pool = getConstantPool();
        m_const1 = pool.getConstant(m_iVal1);
        m_const2 = pool.getConstant(m_iVal2);
        }

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
     * The first value of the interval.
     */
    private Constant m_const1;

    /**
     * The second value of the interval.
     */
    private Constant m_const2;
    }

