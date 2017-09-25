package org.xvm.asm.op;


import java.io.DataInput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.Op;

import org.xvm.runtime.Frame;


/**
 * NOP - a "no op".
 */
public class Nop extends Op
    {
    /**
     * Construct a NOP op.
     */
    public Nop()
        {
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public Nop(DataInput in, Constant[] aconst)
            throws IOException
        {
        }

    @Override
    public int getOpCode()
        {
        return OP_NOP;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        return iPC + 1;
        }
    }
