package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.Frame.Guard;
import org.xvm.proto.Op;

/**
 * GUARD op-code.
 *
 * @author gg 2017.03.08
 */
public class GuardStart extends Op
    {
    final int[] f_anClassConstId;
    final int[] f_anCatchAddress;

    public GuardStart(int nClassConstId, int nCatchAddress)
        {
        f_anClassConstId = new int[] {nClassConstId};
        f_anCatchAddress = new int[] {nCatchAddress};
        }

    public GuardStart(int[] anClassConstId, int[] anCatch)
        {
        assert anClassConstId.length == anCatch.length;

        f_anClassConstId = anClassConstId;
        f_anCatchAddress = anCatch;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        // ++ Enter
        int iScope = frame.f_aiRegister[I_SCOPE]++;

        frame.f_anNextVar[iScope] = frame.f_anNextVar[iScope-1];
        // --

        int iGuard = frame.f_aiRegister[I_GUARD]++;

        Guard[] aGuard = frame.m_aGuard;
        if (iGuard == -1)
            {
            aGuard = frame.m_aGuard = new Frame.Guard[frame.f_function.m_cScopes];
            }

        aGuard[iGuard] = new Guard(iScope, f_anClassConstId, f_anCatchAddress);

        return iPC + 1;
        }
    }
