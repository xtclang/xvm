package org.xvm.asm.op;


import java.io.DataInput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.LinkerContext;
import org.xvm.asm.Op;
import org.xvm.asm.OpCondJump;

import org.xvm.asm.constants.ConditionalConstant;

import org.xvm.runtime.Frame;


/**
 * JMP_COND COND, addr
 */
public class JumpCond
        extends OpCondJump
    {
    /**
     * Construct a JMP_COND op.
     *
     * @param arg  the conditional constant to test
     * @param op   the op to conditionally jump to
     */
    public JumpCond(ConditionalConstant arg, Op op)
        {
        super(arg, op);
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
        super(in, aconst);
        }

    @Override
    public int getOpCode()
        {
        return OP_JMP_COND;
        }

    @Override
    protected int processUnaryOp(Frame frame, int iPC)
        {
        if (m_cond == null)
            {
            m_cond = (ConditionalConstant) frame.getConstant(m_nArg);
            }

        LinkerContext ctx = frame.f_context.getLinkerContext();
        return m_cond.evaluate(ctx) ? jump(frame, iPC + m_ofJmp, m_cExits) : iPC + 1;
        }

    private ConditionalConstant m_cond;
    }
