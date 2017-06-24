package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.Frame.AllGuard;
import org.xvm.proto.Frame.Guard;
import org.xvm.proto.Op;

/**
 * GUARD_ALL rel_addr ; ENTER
 *
 * @author gg 2017.03.08
 */
public class GuardAll extends Op
    {
    private final int f_nFinallyRelAddress;

    private AllGuard m_guard; // cached struct

    public GuardAll(int nRelAddress)
        {
        f_nFinallyRelAddress = nRelAddress;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        int iScope = frame.enterScope();

        Guard guard = m_guard;
        if (guard == null)
            {
            guard = m_guard = new AllGuard(iPC, iScope, f_nFinallyRelAddress);
            }
        frame.pushGuard(guard);

        return iPC + 1;
        }
    }
