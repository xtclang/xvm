package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Consumer;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;

import org.xvm.util.Handy;
import org.xvm.util.Hash;

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

        int c = readMagnitude(in);
        if (c < 2 || c > 63)
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

    @Override
    protected void resolveConstants()
        {
        int[] ai = m_aiCond;
        int   c  = ai.length;
        ConditionalConstant[] aconstCond = new ConditionalConstant[c];

        if (c > 0)
            {
            ConstantPool pool = getConstantPool();
            for (int i = 0; i < c; ++i)
                {
                aconstCond[i] = (ConditionalConstant) pool.getConstant(ai[i]);
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
            final ConditionalConstant[]   acond   = m_aconstCond;
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

    /**
     * Remove the specified condition from the multi-condition.
     *
     * @param cond  the condition to remove
     *
     * @return the modified multi-condition if the specified conditional was found; otherwise this
     */
    public ConditionalConstant remove(ConditionalConstant cond)
        {
        ConditionalConstant[] acond = m_aconstCond;
        for (int i = 0, c = acond.length; i < c; ++i)
            {
            if (acond[i].equals(cond))
                {
                return remove(i);
                }
            }
        return this;
        }

    /**
     * Remove the specified condition from the multi-condition.
     *
     * @param i  the index of the condition to remove
     *
     * @return the resulting condition
     */
    public ConditionalConstant remove(int i)
        {
        ConditionalConstant[] acondOld = m_aconstCond;
        int                   cConds   = acondOld.length;
        assert i >= 0 && i < cConds;

        if (cConds < 2)
            {
            throw new IllegalStateException("length=" + cConds);
            }

        if (cConds == 2)
            {
            return acondOld[-(i-1)];
            }

        ConditionalConstant[] acondNew = new ConditionalConstant[cConds-1];
        System.arraycopy(acondOld, 0, acondNew, 0, i);
        System.arraycopy(acondOld, i+1, acondNew, i, cConds - i - 1);
        return instantiate(acondNew);
        }

    /**
     * Factory.
     *
     * @param aconstCond  an array of conditions
     *
     * @return a multi-condition of the same type composed of the specified conditions
     */
    protected abstract MultiCondition instantiate(ConditionalConstant[] aconstCond);


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public boolean containsUnresolved()
        {
        for (Constant constant : m_aconstCond)
            {
            if (constant.containsUnresolved())
                {
                return true;
                }
            }
        return false;
        }

    @Override
    public void forEachUnderlying(Consumer<Constant> visitor)
        {
        for (Constant constant : m_aconstCond)
            {
            visitor.accept(constant);
            }
        }

    @Override
    protected int compareDetails(Constant that)
        {
        if (!(that instanceof MultiCondition))
            {
            return -1;
            }
        return this.equals(that)
                ? 0
                : Handy.compareArrays(m_aconstCond, ((MultiCondition) that).m_aconstCond);
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
    protected void registerConstants(ConstantPool pool)
        {
        m_aconstCond = (ConditionalConstant[]) registerConstants(pool, m_aconstCond);
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
    public int computeHashCode()
        {
        return Hash.of(m_aconstCond);
        }

    @Override
    public boolean equals(Object obj)
        {
        // TODO this should all be moved to the compareDetails() method, and the equals() method should be dropped (let Constant.equals() handle it by calling compareDetails())

        // must both be multi-conditions
        if (!(obj instanceof MultiCondition))
            {
            return false;
            }

        // must both have the same operator
        final MultiCondition that = (MultiCondition) obj;
        if (!this.getOperatorString().equals(that.getOperatorString()))
            {
            return false;
            }

        // must both contain the same conditions; order of conditionals is not important; start by
        // verify that they both have the same number of conditionals
        final ConditionalConstant[] aconstThis = this.m_aconstCond;
        final ConditionalConstant[] aconstThat = that.m_aconstCond;
        int cConds = aconstThis.length;
        if (cConds != aconstThat.length)
            {
            return false;
            }

        // sequentially match as many as possible
        int cSeqMatch = 0;
        for (int i = 0; i < cConds; ++i)
            {
            if (aconstThis[i].equals(aconstThat[i]))
                {
                ++cSeqMatch;
                }
            else
                {
                break;
                }
            }

        // n^2 match the remaining
        if (cSeqMatch < cConds)
            {
            boolean[] matched = new boolean[cConds];
            NextCond: for (int iThis = cSeqMatch; iThis < cConds; ++iThis)
                {
                ConditionalConstant constThis = aconstThis[iThis];
                for (int iThat = cSeqMatch; iThat < cConds; ++iThat)
                    {
                    if (!matched[iThat] && constThis.equals(aconstThat[iThat]))
                        {
                        matched[iThat] = true;
                        continue NextCond;
                        }
                    }
                return false;
                }
            }

        return true;
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
    }