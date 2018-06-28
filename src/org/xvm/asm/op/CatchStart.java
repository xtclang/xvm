package org.xvm.asm.op;


import org.xvm.asm.Op;
import org.xvm.asm.Scope;

import org.xvm.runtime.Frame;


/**
 * CATCH ; begin an exception handler (implicit ENTER)
 */
public class CatchStart
        extends Op
    {
    /**
     * Construct a CATCH op.
     */
    public CatchStart()
        {
        }

    @Override
    public int getOpCode()
        {
        return OP_CATCH;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        // all the logic is actually implemented by Frame.findGuard()
        return iPC + 1;
        }

    @Override
    public void simulate(Scope scope)
        {
        scope.enter();
        scope.allocVar(); // the exception
        }
    }
