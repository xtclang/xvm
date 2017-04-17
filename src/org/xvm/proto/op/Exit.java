package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.Op;

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
    public int process(Frame frame, int iPC)
        {
        int iScope = frame.f_aiIndex[I_SCOPE]--;

        frame.clearScope(iScope);

        return iPC + 1;
        }
    }
