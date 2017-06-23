package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.Op;

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
    public int process(Frame frame, int iPC)
        {
        frame.enterScope();

        return iPC + 1;
        }
    }
