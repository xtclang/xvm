package org.xvm.asm.op;


import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Op;

import org.xvm.runtime.Frame;


/**
 * ENTER ; (variable scope begin)
 *
 * @author gg 2017.03.08
 */
public class Enter extends Op
    {
    public Enter()
        {
        }

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.write(OP_ENTER);
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        frame.enterScope();

        return iPC + 1;
        }
    }
