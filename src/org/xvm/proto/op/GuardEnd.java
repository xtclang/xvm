package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.Op;

/**
 * ENDGUARD rel_addr
 *
 * @author gg 2017.03.08
 */
public class GuardEnd extends Op
    {
    private final int f_nRelAddr;

    public GuardEnd(int iRelAddr)
        {
        f_nRelAddr = iRelAddr;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        frame.m_iGuard--;

        // ++ Exit
        int iScope = frame.m_iScope--;

        frame.clearScope(iScope);
        // --

        return iPC + f_nRelAddr;
        }
    }
