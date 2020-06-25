package org.xvm.asm;


import java.io.DataOutput;
import java.io.IOException;


/**
 * Base class for the RETURN_* op-codes.
 */
public abstract class OpReturn
        extends Op
    {
    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        super.write(out, registry);
        }

    @Override
    public boolean advances()
        {
        return false;
        }

    @Override
    public void resolveAddresses(Op[] aop)
        {
        super.resolveAddresses(aop);

        int nGuardAllDepth = getGuardAllDepth();
        if (nGuardAllDepth > 0)
            {
            Op opFinally = findFirstUnmatchedOp(aop, OP_GUARD_ALL, OP_FINALLY);

            assert opFinally.getGuardAllDepth() == nGuardAllDepth; // GuardAllDepth drops right after OP_FINALLY

            m_ixAllGuard   = opFinally.getGuardDepth() + nGuardAllDepth - 1;
            m_fCallFinally = true;
            }
        }

    protected boolean m_fCallFinally;
    protected int     m_ixAllGuard;
    }
