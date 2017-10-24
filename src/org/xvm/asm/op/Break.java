package org.xvm.asm.op;


import org.xvm.asm.Op;

import org.xvm.runtime.Frame;


/**
 * BREAK - a "no op" at runtime, but a breakpoint in the debugger.
 */
public class Break
        extends Op
    {
    /**
     * Construct a BREAK op.
     */
    public Break()
        {
        }

    @Override
    public int getOpCode()
        {
        return OP_BREAK;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        return iPC + 1;
        }
    }
