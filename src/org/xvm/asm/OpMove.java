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
     * @param regTo  the Register to move to
     */
    protected OpMove(Argument argFrom, Register regTo)
        {
        m_argFrom = argFrom;
        m_regTo   = regTo;
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
        if (m_argFrom != null)
            {
            m_nFromValue = encodeArgument(m_argFrom, registry);
            m_nToValue   = encodeArgument(m_regTo, registry);
            }

        out.writeByte(getOpCode());
        writePackedLong(out, m_nFromValue);
        writePackedLong(out, m_nToValue);
        }

    @Override
    public void simulate(Scope scope)
        {
        if (m_regTo == null)
            {
            // TODO: remove when deprecated construction is removed
            if (scope.isNextRegister(m_nToValue))
                {
                scope.allocVar();
                }
            }
        else
            {
            checkNextRegister(scope, m_regTo);
            }
        }

    @Override
    public void registerConstants(ConstantRegistry registry)
        {
        m_argFrom = registerArgument(m_argFrom, registry);
        }

    protected int m_nFromValue;
    protected int m_nToValue;

    private Argument m_argFrom;
    private Register m_regTo;
    }
