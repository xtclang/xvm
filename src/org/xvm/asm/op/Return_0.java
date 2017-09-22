package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.Op;

import org.xvm.runtime.Frame;


/**
 * RETURN_0 ; (no return value)
 */
public class Return_0
        extends Op
    {
    public static final Return_0 INSTANCE = new Return_0();

    public Return_0()
        {
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public Return_0(DataInput in, Constant[] aconst)
            throws IOException
        {
        }

    @Override
    public int getOpCode()
        {
        return OP_RETURN_0;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        return R_RETURN;
        }
    }
