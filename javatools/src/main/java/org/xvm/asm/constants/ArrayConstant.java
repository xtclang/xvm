package org.xvm.asm.constants;


import java.util.function.Consumer;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.util.Hash;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Represent a constant value that contains a number of other constant values. Specifically this
 * supports the array, tuple, and set types.
 */
public class ArrayConstant
        extends ValueConstant
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a constant whose value is an array, tuple, or set.
     *
     * @param pool       the ConstantPool that will contain this Constant
     * @param fmt        the format of the constant
     * @param constType  the data type of the constant
     * @param aconstVal  the value of the constant
     */
    public ArrayConstant(ConstantPool pool, Format fmt, TypeConstant constType, Constant... aconstVal)
        {
        super(pool);
        validateFormatAndType(fmt, constType);

        if (aconstVal == null)
            {
            throw new IllegalArgumentException("value required");
            }

        f_fmt       = fmt;
        m_constType = constType;
        m_aconstVal = aconstVal;
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
    public ArrayConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool);

        int iType = readMagnitude(in);
        int cVals = readMagnitude(in);
        int[] aiVal = new int[cVals];
        for (int i = 0; i < cVals; ++i)
            {
            aiVal[i] = readMagnitude(in);
            }

        f_fmt   = format;
        m_iType = iType;
        m_aiVal = aiVal;
        }

    @Override
    protected void resolveConstants()
        {
        ConstantPool pool = getConstantPool();

        m_constType = (TypeConstant) pool.getConstant(m_iType);

        int[]      aiConst = m_aiVal;
        int        cConsts = aiConst.length;
        Constant[] aconst  = new Constant[cConsts];
        for (int i = 0; i < cConsts; ++i)
            {
            aconst[i] = pool.getConstant(aiConst[i]);
            }
        m_aconstVal = aconst;
        }

    private void validateFormatAndType(Format fmt, TypeConstant constType)
        {
        if (fmt == null)
            {
            throw new IllegalArgumentException("format required");
            }
        if (constType == null)
            {
            throw new IllegalArgumentException("type required");
            }

        // determine what the class must be based on the constant format
        String sClassName;
        switch (fmt)
            {
            case Array:
            case Tuple:
            case Set:
                sClassName = fmt.name();
                break;

            default:
                throw new IllegalArgumentException("unsupported format: " + fmt);
            }

        // require that the underlying identity be a class (not an auto-narrowing type for example)
        // and make sure that it matches
        if (!constType.isEcstasy(sClassName))
            {
            throw new IllegalArgumentException("type for " + fmt + " must be " + sClassName
                    + " (unsupported type: " + constType + ")");
            }
        }

    @Override
    public Constant convertTo(TypeConstant typeOut)
        {
        if (typeOut.isSingleDefiningConstant())
            {
            ConstantPool pool = getConstantPool();

            if (typeOut.getDefiningConstant().equals(pool.clzArray()))
                {
                TypeConstant typeEl = typeOut.getParamType(0);

                Constant[] aconstIn  = getValue();
                int        cValues   = aconstIn.length;
                Constant[] aconstOut = new Constant[cValues];

                for (int i = 0; i < cValues; i++)
                    {
                    Constant constEl = aconstIn[i].convertTo(typeEl);
                    if (constEl == null)
                        {
                        return null;
                        }
                    aconstOut[i] = constEl;
                    }
                return pool.ensureArrayConstant(typeOut, aconstOut);
                }
            }
        return null;
        }


    // ----- ValueConstant methods -----------------------------------------------------------------

    @Override
    public TypeConstant getType()
        {
        return m_constType;
        }

    /**
     * {@inheritDoc}
     * @return  the constant's contents as an array of constants
     */
    @Override
    public Constant[] getValue()
        {
        return m_aconstVal;
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return f_fmt;
        }

    @Override
    public boolean isValueCacheable()
        {
        return !m_constType.containsFormalType(true);
        }

    @Override
    public boolean containsUnresolved()
        {
        if (isHashCached())
            {
            return false;
            }

        if (m_constType.containsUnresolved())
            {
            return true;
            }

        Constant[] aconstVal = m_aconstVal;
        for (int i = 0, c = aconstVal.length; i < c; ++i)
            {
            if (aconstVal[i].containsUnresolved())
                {
                return true;
                }
            }

        return false;
        }

    @Override
    public void forEachUnderlying(Consumer<Constant> visitor)
        {
        visitor.accept(m_constType);
        Constant[] aconst = m_aconstVal;
        for (int i = 0, c = aconst.length; i < c; ++i)
            {
            visitor.accept(aconst[i]);
            }
        }

    @Override
    public ArrayConstant resolveTypedefs()
        {
        TypeConstant typeOld = m_constType;
        TypeConstant typeNew = typeOld.resolveTypedefs();

        // check values
        Constant[] aconstOld = m_aconstVal;
        Constant[] aconstNew = null;
        for (int i = 0, c = aconstOld.length; i < c; ++i)
            {
            Constant constOld = aconstOld[i];
            Constant constNew = constOld.resolveTypedefs();
            if (constNew != constOld)
                {
                if (aconstNew == null)
                    {
                    aconstNew = aconstOld.clone();
                    }
                aconstNew[i] = constNew;
                }
            }

        return typeNew == typeOld && aconstNew == null
                ? this
                : (ArrayConstant) getConstantPool().register(
                        new ArrayConstant(getConstantPool(), f_fmt, typeNew, aconstNew));
        }

    @Override
    protected int compareDetails(Constant that)
        {
        if (!(that instanceof ArrayConstant))
            {
            return -1;
            }
        int nResult = this.m_constType.compareTo(((ArrayConstant) that).m_constType);
        if (nResult != 0)
            {
            return nResult;
            }

        Constant[] aconstThis = this.m_aconstVal;
        Constant[] aconstThat = ((ArrayConstant) that).m_aconstVal;
        int cThis = aconstThis.length;
        int cThat = aconstThat.length;
        for (int i = 0, c = Math.min(cThis, cThat); i < c; ++i)
            {
            nResult = aconstThis[i].compareTo(aconstThat[i]);
            if (nResult != 0)
                {
                return nResult;
                }
            }
        return cThis - cThat;
        }

    @Override
    public String getValueString()
        {
        Constant[] aconst  = m_aconstVal;
        int        cConsts = aconst.length;

        String sStart;
        String sEnd;
        switch (f_fmt)
            {
            case Array:
                sStart = "[";
                sEnd   = "]";
                break;
            case Tuple:
                sStart = cConsts < 2 ? "Tuple:(" : "(";
                sEnd   = ")";
                break;
            case Set:
                sStart = "Set:{";
                sEnd   = "}";
                break;

            default:
                throw new IllegalArgumentException("illegal format: " + f_fmt);
            }

        StringBuilder sb = new StringBuilder();
        sb.append(sStart);

        for (int i = 0; i < cConsts; ++i)
            {
            if (i > 0)
                {
                sb.append(", ");
                }

            sb.append(aconst[i]);
            }

        sb.append(sEnd);
        return sb.toString();
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        m_constType = (TypeConstant) pool.register(m_constType);
        m_aconstVal = registerConstants(pool, m_aconstVal);
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        out.writeByte(getFormat().ordinal());
        writePackedLong(out, m_constType.getPosition());
        Constant[] aconst  = m_aconstVal;
        int        cConsts = aconst.length;
        writePackedLong(out, cConsts);
        for (int i = 0; i < cConsts; ++i)
            {
            writePackedLong(out, aconst[i].getPosition());
            }
        }

    @Override
    public String getDescription()
        {
        return "array-length=" + m_aconstVal.length;
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int computeHashCode()
        {
        return Hash.of(m_constType,
               Hash.of(m_aconstVal));
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * Holds the indexes of the type during deserialization.
     */
    private transient int m_iType;

    /**
     * Holds the indexes of the constant values during deserialization.
     */
    private transient int[] m_aiVal;

    /**
     * The constant format.
     */
    private final Format f_fmt;

    /**
     * The type represented by this constant. Note that this is not the element type, but rather is
     * the type of the array, tuple, or set.
     */
    private TypeConstant m_constType;

    /**
     * The values in the array, tuple, or set.
     */
    private Constant[] m_aconstVal;
    }

