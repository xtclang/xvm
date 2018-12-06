package org.xvm.asm.op;


import java.io.DataInput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.Op;
import org.xvm.asm.OpJump;
import org.xvm.asm.Scope;

import org.xvm.runtime.Frame;
import org.xvm.runtime.Frame.AllGuard;


/**
 * GUARDALL addr ; (implicit ENTER)
 */
public class GuardAll
        extends OpJump
    {
    /**
     * Construct a GUARDALL op.
     *
     * @param nRelAddress  the relative address of the finally block
     *
     * @deprecated
     */
    public GuardAll(int nRelAddress)
        {
        super(null);

        m_ofJmp = nRelAddress;
        }

    /**
     * Construct a GUARDALL op based on the destination Op.
     *
     * @param op  the Op to jump to when the guarded section completes
     */
    public GuardAll(Op op)
        {
        super(op);
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public GuardAll(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);
        }

    @Override
    public int getOpCode()
        {
        return OP_GUARD_ALL;
        }

    @Override
    public boolean isEnter()
        {
        return true;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        int iScope = frame.enterScope();

        AllGuard guard = m_guard;
        if (guard == null)
            {
            m_guard = guard = new AllGuard(iPC, iScope, m_ofJmp);
            }
        frame.pushGuard(guard);

        return iPC + 1;
        }

    @Override
    public void simulate(Scope scope)
        {
        scope.enter();
        }

    private transient AllGuard m_guard; // cached struct
    }
