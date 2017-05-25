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
        int iScope = ++frame.m_iScope;

        // start where the previous scope ended
        frame.f_anNextVar[iScope] = frame.f_anNextVar[iScope - 1];

        return iPC + 1;
        }
    }
