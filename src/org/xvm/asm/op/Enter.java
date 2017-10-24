package org.xvm.asm.op;


import org.xvm.asm.Op;
import org.xvm.asm.Scope;

import org.xvm.runtime.Frame;


/**
 * ENTER ; (variable scope begin)
 */
public class Enter
        extends Op
    {
    /**
     * Construct an ENTER op.
     */
    public Enter()
        {
        }

    @Override
    public int getOpCode()
        {
        return OP_ENTER;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        frame.enterScope();

        return iPC + 1;
        }

    @Override
    public void simulate(Scope scope)
        {
        scope.enter();
        }
    }
