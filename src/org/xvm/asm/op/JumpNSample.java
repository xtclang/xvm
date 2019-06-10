package org.xvm.asm.op;


import java.io.DataInput;
import java.io.IOException;

import java.util.concurrent.ThreadLocalRandom;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.Op;
import org.xvm.asm.OpCondJump;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;


/**
 * OP_JMP_NSAMPLE inverse-sample-rate, addr ; jump if this is NOT a selected sample based on the
 *                                          ; rvalue sample rate (a compile-time or run-time
 *                                          ; constant)
 *
 * <p/>TODO verify that inverse-sample-rate is a constant or a runtime constant
 */
public class JumpNSample
        extends OpCondJump
    {
    /**
     * Construct a OP_JMP_NSAMPLE op.
     *
     * @param arg  the sample rate (must be a compile-time or run-time constant)
     * @param op   the op to conditionally jump to
     */
    public JumpNSample(Argument arg, Op op)
        {
        super(arg, op);
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public JumpNSample(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);
        }

    @Override
    public int getOpCode()
        {
        return OP_JMP_NSAMPLE;
        }

    @Override
    protected int completeUnaryOp(Frame frame, int iPC, ObjectHandle hValue)
        {
        int nEvery = m_nEvery;
        if (nEvery == 0)
            {
            long lEvery = ((JavaLong) hValue).getValue();
            if (lEvery <= 0)
                {
                System.err.println("illegal sample rate indicator: " + lEvery + " (must be >= 0)");
                }
            m_nEvery = nEvery = Math.max(1, Math.min(Integer.MAX_VALUE, (int) lEvery));
            }

        return f_rnd.nextInt(nEvery) == 0 ? iPC + 1 : jump(frame, iPC + m_ofJmp, m_cExits);
        }

    private static final ThreadLocalRandom f_rnd = ThreadLocalRandom.current();

    private transient int m_nEvery;
    }
