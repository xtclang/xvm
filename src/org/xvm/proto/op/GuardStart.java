package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.Frame.Guard;
import org.xvm.proto.Frame.MultiGuard;
import org.xvm.proto.Op;

/**
 * GUARD #handlers:(CONST_CLASS, rel_addr) ; ENTER
 *
 * @author gg 2017.03.08
 */
public class GuardStart extends Op
    {
    private final int[] f_anClassConstId;
    private final int[] f_anNameConstId;
    private final int[] f_anCatchRelAddress;

    private MultiGuard m_guard; // cached struct

    public GuardStart(int nClassConstId, int nNameConstId, int nCatchAddress)
        {
        f_anClassConstId = new int[] {nClassConstId};
        f_anNameConstId = new int[] {nNameConstId};
        f_anCatchRelAddress = new int[] {nCatchAddress};
        }

    public GuardStart(int[] anClassConstId, int[] anNameConstId, int[] anCatch)
        {
        assert anClassConstId.length == anCatch.length;

        f_anClassConstId = anClassConstId;
        f_anNameConstId   = anNameConstId;
        f_anCatchRelAddress = anCatch;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        int iScope = frame.enterScope();

        Guard guard = m_guard;
        if (guard == null)
            {
            guard = m_guard = new MultiGuard(iPC, iScope,
                    f_anClassConstId, f_anNameConstId, f_anCatchRelAddress);
            }
        frame.pushGuard(guard);

        return iPC + 1;
        }
    }
