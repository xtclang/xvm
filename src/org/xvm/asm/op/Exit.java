package org.xvm.asm.op;


import org.xvm.asm.Op;

import org.xvm.runtime.Frame;


/**
 * EXIT (variable scope end)
 *
 * @author gg 2017.03.08
 */
public class Exit extends Op
    {
    public Exit()
        {
        }

    @Override
    public int getOpCode()
        {
        return OP_EXIT;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        frame.exitScope();

        return iPC + 1;
        }
    }
