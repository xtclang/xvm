package org.xvm.asm.op;


import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.OpInvocable;

import org.xvm.runtime.Frame;


/**
 * NOP
 *
 * @author gg 2017.03.08
 */
public class Nop extends OpInvocable
    {
    public Nop()
        {
        }

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.write(OP_NOP);
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        return iPC + 1;
        }
    }
