package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.LinkerContext;

import org.xvm.util.Handy;

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


    // ----- type-specific functionality -----------------------------------------------------------

    /**
     * @return the condition that this condition negates
     */
    public ConditionalConstant getCondition()
        {
        return m_constCond;
        }


    // ----- ConditionalConstant methods -----------------------------------------------------------

    @Override
    public boolean evaluate(LinkerContext ctx)
        {
        return !m_constCond.evaluate(ctx);
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.ConditionNot;
        }

    @Override
    protected int compareDetails(Constant that)
        {
        return m_constCond.compareTo(((org.xvm.asm.constants.NotCondition) that).m_constCond);
        }

    @Override
    public String getValueString()
        {
        return "!" + m_constCond.getValueString();
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void disassemble(DataInput in)
            throws IOException
        {
        m_constCond = (ConditionalConstant) getConstantPool().getConstant(m_iCond);
        }

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
    public int hashCode()
        {
        return Handy.hashCode(m_constCond);
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
