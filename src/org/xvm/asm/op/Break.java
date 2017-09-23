package org.xvm.asm.op;


import java.io.DataInput;
import java.io.IOException;

import org.xvm.asm.Constant;
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

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public Break(DataInput in, Constant[] aconst)
            throws IOException
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
