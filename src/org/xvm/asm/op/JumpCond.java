package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;
import org.xvm.asm.Register;
import org.xvm.asm.Scope;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Base class for conditional jumps.
 */
public abstract class JumpCond
        extends Op
    {
    /**
     * Construct a conditional JMP op.
     *
     * @param op  the op to jump to
     */
    public JumpCond(Argument arg, Op op)
        {
        m_argVal = arg;
        m_opDest = op;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public JumpCond(DataInput in, Constant[] aconst)
            throws IOException
        {
        m_nArg  = readPackedInt(in);
        m_ofJmp = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        if (m_argVal != null)
            {
            m_nArg = encodeArgument(m_argVal, registry);
            }

        out.writeByte(getOpCode());
        writePackedLong(out, m_nArg);
        writePackedLong(out, m_ofJmp);
        }

    @Override
    public void resolveAddress(MethodStructure.Code code, int iPC)
        {
        if (m_opDest != null && m_ofJmp == 0)
            {
            int iPCThat = code.addressOf(m_opDest);
            if (iPCThat < 0)
                {
                throw new IllegalStateException("cannot find op: " + m_opDest);
                }

            // calculate relative offset
            m_ofJmp = iPCThat - iPC;
            if (m_ofJmp == 0)
                {
                throw new IllegalStateException("infinite loop: " + this);
                }
            }
        }

    protected Argument m_argVal;
    protected Op       m_opDest;
    protected int      m_nArg;
    protected int      m_ofJmp;
    }
