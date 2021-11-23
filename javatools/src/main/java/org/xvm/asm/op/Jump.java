package org.xvm.asm.op;


import java.io.DataInput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.Op;
import org.xvm.asm.OpJump;

import org.xvm.runtime.Frame;


/**
 * JMP addr ; unconditional relative jump
 */
public class Jump
        extends OpJump
    {
    /**
     * Construct a JMP op.
     *
     * @param op  the op to jump to
     */
    public Jump(Op op)
        {
        super(op);
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public Jump(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);
        }

    @Override
    public int getOpCode()
        {
        return OP_JMP;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        if (m_fCallFinally)
            {
            return frame.processAllGuard(
                new JumpAction(iPC + m_ofJmp, m_nJumpToScope, m_ixAllGuard, m_ixBaseGuard));
            }

        for (int cPop = m_cPopGuards; cPop-- > 0;)
            {
            frame.popGuard();
            }
        return jump(frame, iPC + m_ofJmp, m_cExits);
        }

    @Override
    public void resolveAddresses(Op[] aop)
        {
        super.resolveAddresses(aop);

        Op  opJumpTo      = aop[getAddress() + m_ofJmp];
        int nAllDepthThis = getGuardAllDepth();
        int nAllDepthJump = opJumpTo.getGuardAllDepth();

        assert nAllDepthJump <= nAllDepthThis;

        if (nAllDepthJump < nAllDepthThis)
            {
            Op opFinally = findFirstUnmatchedOp(aop, OP_GUARD_ALL, OP_FINALLY);

            assert opFinally.getGuardAllDepth() == nAllDepthThis; // GuardAllDepth drops right after OP_FINALLY

            m_ixAllGuard   = opFinally.getGuardDepth() + nAllDepthThis - 1;
            m_ixBaseGuard  = nAllDepthJump - 1;
            m_nJumpToScope = opJumpTo.getDepth() - 1;
            m_fCallFinally = true;
            }

        int cPopGuards = getGuardDepth() - opJumpTo.getGuardDepth();
        assert cPopGuards >= 0;
        m_cPopGuards = cPopGuards;
        }

    @Override
    public boolean checkRedundant(Op[] aop)
        {
        if (m_ofJmp == 1)
            {
            markRedundant();
            return true;
            }

        return false;
        }

    protected static class JumpAction
            extends Frame.DeferredGuardAction
        {
        public JumpAction(int iPC, int nJumpScope, int ixAllGuard, int ixBaseGuard)
            {
            super(ixAllGuard, ixBaseGuard);

            m_iPC        = iPC;
            m_nJumpScope = nJumpScope;
            }

        @Override
        public int complete(Frame frame)
            {
            while (frame.m_iScope > m_nJumpScope)
                {
                frame.exitScope();
                }
            return m_iPC;
            }

        private final int m_iPC;
        private final int m_nJumpScope;
        }

    protected transient boolean m_fCallFinally;
    protected transient int     m_ixAllGuard;
    protected transient int     m_ixBaseGuard;
    protected transient int     m_nJumpToScope;
    protected transient int     m_cPopGuards;
    }
