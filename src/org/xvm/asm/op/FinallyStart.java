package org.xvm.asm.op;


import org.xvm.asm.Op;
import org.xvm.asm.Scope;

import org.xvm.runtime.Frame;


/**
 * FINALLY ; begin a "finally" handler (implicit EXIT/ENTER and an exception var)
 */
public class FinallyStart
        extends Op
    {
    /**
     * Construct a FINALLY op.
     */
    public FinallyStart()
        {
        }

    @Override
    public int getOpCode()
        {
        return OP_FINALLY;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        frame.exitScope();

        int iScope = frame.enterScope(m_nNextVar);

        // this op-code can only be reached by the normal flow of execution,
        // while upon an exception, the GuardAll would jump to the very next op
        // (called from Frame.findGuard) with an exception at anNextVar[iScope] + 1,
        // so we need to reserve the slot (unassigned) when coming in normally;
        // presence or absence of the exception will be checked by the FinallyEnd
        frame.f_anNextVar[iScope]++;

        return iPC + 1;
        }

    @Override
    public void simulate(Scope scope)
        {
        scope.exit();
        scope.enter();

        m_nNextVar = scope.getCurVars();

        scope.allocVar();
        }

    private int m_nNextVar;
    }
