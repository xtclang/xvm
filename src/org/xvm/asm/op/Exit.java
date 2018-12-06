package org.xvm.asm.op;


import org.xvm.asm.Op;
import org.xvm.asm.Scope;

import org.xvm.runtime.Frame;


/**
 * EXIT ; (variable scope end)
 */
public class Exit
        extends Op
    {
    /**
     * Constructor.
     */
    public Exit()
        {
        }

    @Override
    public int getOpCode()
        {
        return OP_EXIT;
        }

    @Override
    public boolean isExit()
        {
        return true;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        frame.exitScope();

        return iPC + 1;
        }

    @Override
    public void simulate(Scope scope)
        {
        scope.exit();
        }
    }
