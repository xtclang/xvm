package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import java.util.function.Consumer;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.LinkerContext;

import org.xvm.util.Handy;
import org.xvm.util.Hash;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Implements the logical "not" of a condition.
 */
public class NotCondition
        extends ConditionalConstant
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a NotCondition.
     *
     * @param pool       the ConstantPool that will contain this
     *                   Constant
     * @param constCond  the underlying condition to evaluate
     */
    public NotCondition(ConstantPool pool, ConditionalConstant constCond)
        {
        super(pool);

        if (constCond == null)
            {
            throw new IllegalArgumentException("condition required");
            }

        m_constCond = constCond;
        }

    /**
     * Constructor used for deserialization.
     *
     * @param pool    the ConstantPool that will contain this Constant
     * @param format  the format of the Constant in the stream
     * @param in      the DataInput stream to read the Constant value
     *                from
     *
     * @throws IOException  if an issue occurs reading the Constant
     *                      value
     */
    public NotCondition(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool);

        m_iCond = readMagnitude(in);
        }

    @Override
    protected void resolveConstants()
        {
        m_constCond = (ConditionalConstant) getConstantPool().getConstant(m_iCond);
        }


    // ----- type-specific functionality -----------------------------------------------------------

    /**
     * @return the condition that this condition negates
     */
    public ConditionalConstant getUnderlyingCondition()
        {
        return m_constCond;
        }


    // ----- ConditionalConstant methods -----------------------------------------------------------

    @Override
    public boolean evaluate(LinkerContext ctx)
        {
        return !m_constCond.evaluate(ctx);
        }

    @Override
    public boolean testEvaluate(long n)
        {
        return !m_constCond.testEvaluate(n);
        }

    @Override
    public void collectTerminals(Set<ConditionalConstant> terminals)
        {
        m_constCond.collectTerminals(terminals);
        }

    @Override
    public boolean containsTerminal(ConditionalConstant that)
        {
        // while "this" is technically not a terminal, there is nothing that guarantees that "that"
        // is either
        if (this.equals(that))
            {
            return true;
            }

        return m_constCond.containsTerminal(that);
        }

    @Override
    public boolean isTerminalInfluenceBruteForce()
        {
        return !isTerminalInfluenceFinessable(true, new HashSet<>(), new HashSet<>());
        }

    @Override
    protected boolean isTerminalInfluenceFinessable(boolean fInNot,
            Set<ConditionalConstant> setSimple, Set<ConditionalConstant> setComplex)
        {
        return m_constCond.isTerminalInfluenceFinessable(true, setSimple, setComplex);
        }

    @Override
    public Map<ConditionalConstant, Influence> terminalInfluences()
        {
        if (isTerminalInfluenceBruteForce())
            {
            return super.terminalInfluences();
            }

        Map<ConditionalConstant, Influence> mapRaw = m_constCond.terminalInfluences();
        Map<ConditionalConstant, Influence> mapInv = new HashMap<>();
        for (Map.Entry<ConditionalConstant, Influence> entry : mapRaw.entrySet())
            {
            mapInv.put(entry.getKey(), entry.getValue().inverse());
            }
        return mapInv;
        }

    @Override
    public ConditionalConstant negate()
        {
        return m_constCond;
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.ConditionNot;
        }

    @Override
    public boolean containsUnresolved()
        {
        return !isHashCached() && m_constCond.containsUnresolved();
        }

    @Override
    public void forEachUnderlying(Consumer<Constant> visitor)
        {
        visitor.accept(m_constCond);
        }

    @Override
    protected int compareDetails(Constant that)
        {
        if (!(that instanceof NotCondition))
            {
            return -1;
            }
        return m_constCond.compareTo(((NotCondition) that).m_constCond);
        }

    @Override
    public String getValueString()
        {
        return "!" + m_constCond.getValueString();
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        m_constCond = (ConditionalConstant) pool.register(m_constCond);
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        out.writeByte(getFormat().ordinal());

        writePackedLong(out, m_constCond.getPosition());
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int computeHashCode()
        {
        return Hash.of(m_constCond);
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * During disassembly, this holds the index of the underlying condition.
     */
    private transient int m_iCond;

    /**
     * The underlying condition to evaluate.
     */
    private ConditionalConstant m_constCond;
    }
