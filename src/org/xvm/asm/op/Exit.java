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
    public boolean isNecessary()
        {
        // enter and exit are considered to be necessary, because the iPC does not have to pass
        // through them in order for their effect to be felt; however, a pair of enter/exit that
        // are not actually used (no vars inside them) will be removed because they are redundant
        return true;
        }

    @Override
    public void simulate(Scope scope)
        {
        scope.exit(this);
        }
    }
