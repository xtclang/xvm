package org.xvm.asm.op;


import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Op;

import org.xvm.runtime.Frame;


/**
 * RETURN_0 ; (no return value)
 *
 * @author gg 2017.03.08
 */
public class Return_0 extends Op
    {
    public static final Return_0 INSTANCE = new Return_0();

    public Return_0()
        {
        }

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.write(OP_RETURN_0);
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        return R_RETURN;
        }
    }
