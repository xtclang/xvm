package org.xvm.asm;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Base class for the MOV, REF and CAST op-codes.
 */
public abstract class OpMove
        extends Op
    {
    /**
     * Construct an op for the passed arguments.
     *
     * @param argFrom  the Argument to move from
     * @param argTo    the Argument to move to
     */
    protected OpMove(Argument argFrom, Argument argTo)
        {
        m_argFrom = argFrom;
        m_argTo   = argTo;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    protected OpMove(DataInput in, Constant[] aconst)
            throws IOException
        {
        m_nFromValue = readPackedInt(in);
        m_nToValue   = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        super.write(out, registry);

        if (m_argFrom != null)
            {
            m_nFromValue = encodeArgument(m_argFrom, registry);
            m_nToValue   = encodeArgument(m_argTo, registry);
            }

        writePackedLong(out, m_nFromValue);
        writePackedLong(out, m_nToValue);
        }

    @Override
    public void resetSimulation()
        {
        resetRegister(m_argTo);
        }

    @Override
    public void simulate(Scope scope)
        {
        checkNextRegister(scope, m_argTo, m_nToValue);
        }

    @Override
    public void registerConstants(ConstantRegistry registry)
        {
        m_argFrom = registerArgument(m_argFrom, registry);
        m_argTo   = registerArgument(m_argTo, registry);
        }

    @Override
    public String toString()
        {
        return super.toString()
                + ' ' + Argument.toIdString(m_argFrom, m_nFromValue)
                + ", " + Argument.toIdString(m_argTo, m_nToValue);
        }

    protected int m_nFromValue;
    protected int m_nToValue;

    private Argument m_argFrom;
    private Argument m_argTo;
    }
