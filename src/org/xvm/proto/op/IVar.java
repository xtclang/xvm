package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.Op;

/**
 * IVAR CONST_CLASS ; (next register is an initialized anonymous variable)
 *
 * @author gg 2017.03.08
 */
public class IVar extends Op
    {
    final private int f_nClassConstId;
    final private int f_nValueConstId;

    public IVar(int nClassConstId, int nValueConstId)
        {
        f_nClassConstId = nClassConstId;
        f_nValueConstId = nValueConstId;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        int iScope   = frame.f_aiRegister[I_SCOPE];
        int nNextVar = frame.f_anNextVar[iScope];

        frame.f_ahVars[nNextVar] =
                frame.f_context.f_heap.resolveConstHandle(f_nClassConstId, f_nValueConstId); // TODO: cache this

        frame.f_anNextVar[iScope] = nNextVar + 1;

        return iPC + 1;
        }

    }
