package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;

import org.xvm.util.Handy;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Implements the logical combination of any number of conditions.
 */
public abstract class MultiCondition
        extends ConditionalConstant
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
    protected MultiCondition(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool);

        final int c = readMagnitude(in);
        if (c < 1 || c > 1000)
            {
            throw new IllegalStateException("# conditions=" + c);
            }

        int[] ai = new int[c];
        for (int i = 0; i < c; ++i)
            {
            ai[i] = readMagnitude(in);
            }

        m_aiCond = ai;
        }

    /**
     * Construct a MultiCondition.
     *
     * @param pool        the ConstantPool that will contain this Constant
     * @param aconstCond  an array of underlying conditions to evaluate
     */
    protected MultiCondition(ConstantPool pool, ConditionalConstant[] aconstCond)
        {
        super(pool);

        if (aconstCond == null)
            {
            throw new IllegalArgumentException("conditions required");
            }

        final int c = aconstCond.length;
        if (c < 1 || c > 1000)
            {
            throw new IllegalArgumentException("# conditions: " + c);
            }

        for (int i = 0; i < c; ++i)
            {
            if (aconstCond[i] == null)
                {
                throw new IllegalArgumentException("condition " + i + " required");
                }
            }

        m_aconstCond = aconstCond;
        }


    // ----- ConditionalConstant functionality -----------------------------------------------------

    @Override
    public void collectTerminals(Set<ConditionalConstant> terminals)
        {
        for (ConditionalConstant cond : m_aconstCond)
            {
            cond.collectTerminals(terminals);
            }
        }

    @Override
    public boolean containsTerminal(ConditionalConstant that)
        {
        for (ConditionalConstant cond : m_aconstCond)
            {
            if (cond.containsTerminal(that))
                {
                return true;
                }
            }

        return false;
        }

    /**
     * Flatten any nested conditions, as long as the conditions are all of the same type ("and" or
     * "or", but not a mix of both).
     *
     * @return an iterator of the multiple conditions represented by this condition
     */
    public Iterator<ConditionalConstant> flatIterator()
        {
        return new Iterator<ConditionalConstant>()
            {
            ConditionalConstant[]         acond   = m_aconstCond;
            int                           iNext   = 0;
            Iterator<ConditionalConstant> iterSub = null;

            @Override
            public boolean hasNext()
                {
                return iNext < acond.length || iterSub != null;
                }

            @Override
            public ConditionalConstant next()
                {
                if (iterSub != null)
                    {
                    ConditionalConstant cond = iterSub.next();
                    if (!iterSub.hasNext())
                        {
                        iterSub = null;
                        }
                    return cond;
                    }

                if (iNext < acond.length)
                    {
                    ConditionalConstant cond = acond[iNext++];
                    if (cond.getClass() == MultiCondition.this.getClass())
                        {
                        iterSub = ((MultiCondition) cond).flatIterator();
                        return next(); // recurse max one level to this method
                        }
                    else
                        {
                        return cond;
                        }
                    }

                throw new NoSuchElementException();
                }
            };
        }

    /**
     * @return the operator represented by this condition
     */
    protected abstract String getOperatorString();


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    protected int compareDetails(Constant that)
        {
        return Handy.compareArrays(m_aconstCond, ((org.xvm.asm.constants.MultiCondition) that).m_aconstCond);
        }


    @Override
    public String getValueString()
        {
        final ConditionalConstant[] aconstCond = m_aconstCond;

        StringBuilder sb = new StringBuilder();
        sb.append('(')
                .append(m_aconstCond[0].getValueString());

        for (int i = 1, c = aconstCond.length; i < c; ++i)
            {
            sb.append(' ')
                    .append(getOperatorString())
                    .append(' ')
                    .append(aconstCond[i].getValueString());
            }

        return sb.append(')').toString();
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void disassemble(DataInput in)
            throws IOException
        {
        final int[] ai = m_aiCond;
        final int c = ai.length;
        final ConditionalConstant[] aconstCond = new ConditionalConstant[c];

        if (c > 0)
            {
            final ConstantPool pool = getConstantPool();
            for (int i = 0; i < c; ++i)
                {
                aconstCond[i] = (ConditionalConstant) pool.getConstant(ai[i]);
                }
            }

        m_aconstCond = aconstCond;
        }

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        final ConditionalConstant[] aconstCond = m_aconstCond;
        for (int i = 0, c = aconstCond.length; i < c; ++i)
            {
            aconstCond[i] = (ConditionalConstant) pool.register(aconstCond[i]);
            }
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        out.writeByte(getFormat().ordinal());

        final ConditionalConstant[] aconstCond = m_aconstCond;

        final int c = aconstCond.length;
        writePackedLong(out, c);

        for (int i = 0; i < c; ++i)
            {
            writePackedLong(out, aconstCond[i].getPosition());
            }
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        int nHash = m_nHash;
        if (nHash == 0)
            {
            m_nHash = nHash = getOperatorString().hashCode() ^ Handy.hashCode(m_aconstCond);
            }
        return nHash;
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * During disassembly, this holds the indexes of the underlying conditions.
     */
    private int[] m_aiCond;

    /**
     * The underlying conditions to evaluate.
     */
    protected ConditionalConstant[] m_aconstCond;

    /**
     * Cached hash value.
     */
    private transient int m_nHash;
    }
