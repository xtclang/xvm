package org.xvm.asm.constants;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;

import org.xvm.util.Handy;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Represents a Tuple constant.
 */
public class TupleConstant
        extends Constant
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
    public TupleConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool);

        final int c = readMagnitude(in);
        if (c < 0 || c > 1000)
            {
            throw new IllegalStateException("# constants=" + c);
            }

        int[] ai = new int[c];
        for (int i = 0; i < c; ++i)
            {
            ai[i] = readMagnitude(in);
            }

        m_aiConst = ai;
        }

    /**
     * Construct a TupleConstant.
     *
     * @param pool    the ConstantPool that will contain this Constant
     * @param aconst  an array of underlying constants to evaluate
     */
    public TupleConstant(ConstantPool pool, Constant[] aconst)
        {
        super(pool);

        if (aconst == null)
            {
            throw new IllegalArgumentException("constants required");
            }

        final int c = aconst.length;
        if (c < 0 || c > 1000)
            {
            throw new IllegalArgumentException("# constants: " + c);
            }

        for (int i = 0; i < c; ++i)
            {
            if (aconst[i] == null)
                {
                throw new IllegalArgumentException("constant " + i + " required");
                }
            }

        m_aconst = aconst;
        }


    // ----- type-specific functionality -----------------------------------------------------------

    /**
     * @return a list of the constants represented by this TupleConstant
     */
    public List<Constant> constants()
        {
        return Arrays.asList(m_aconst);
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.Tuple;
        }

    @Override
    protected int compareDetails(Constant that)
        {
        return Handy.compareArrays(m_aconst, ((TupleConstant) that).m_aconst);
        }

    @Override
    public String getValueString()
        {
        final Constant[] aconst = m_aconst;

        StringBuilder sb = new StringBuilder("(");

        for (int i = 0, c = aconst.length; i < c; ++i)
            {
            if (i > 0)
                {
                sb.append(", ");
                }
            sb.append(aconst[i].getValueString());
            }

        return sb.append(')').toString();
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void disassemble(DataInput in)
            throws IOException
        {
        final int[] ai = m_aiConst;
        final int c = ai.length;
        final Constant[] aconst = new Constant[c];

        if (c > 0)
            {
            final ConstantPool pool = getConstantPool();
            for (int i = 0; i < c; ++i)
                {
                aconst[i] = pool.getConstant(ai[i]);
                }
            }

        m_aconst = aconst;
        }

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        final Constant[] aconst = m_aconst;
        for (int i = 0, c = aconst.length; i < c; ++i)
            {
            aconst[i] = pool.register(aconst[i]);
            }
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        out.writeByte(getFormat().ordinal());

        final Constant[] aconst = m_aconst;

        final int c = aconst.length;
        writePackedLong(out, c);

        for (int i = 0; i < c; ++i)
            {
            writePackedLong(out, aconst[i].getPosition());
            }
        }

    @Override
    public String getDescription()
        {
        return "Tuple=" + getValueString();
        }

    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        int nHash = m_nHash;
        if (nHash == 0)
            {
            m_nHash = nHash = Handy.hashCode(m_aconst);
            }
        return nHash;
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * During disassembly, this holds the indexes of the contained constant values.
     */
    private int[] m_aiConst;

    /**
     * The contained constant values.
     */
    protected Constant[] m_aconst;

    /**
     * Cached hash value.
     */
    private transient int m_nHash;
    }
