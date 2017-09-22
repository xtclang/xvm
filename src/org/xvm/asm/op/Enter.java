package org.xvm.asm.op;


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
    }
