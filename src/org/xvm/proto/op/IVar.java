package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.Op;

/**
 * IVAR op-code.
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
    public int process(Frame frame, int iPC, int[] aiRegister, int[] anScopeNextVar)
        {
        int iScope = aiRegister[I_SCOPE];
        int nNextVar = anScopeNextVar[iScope];

        frame.f_ahVars[nNextVar] = frame.f_context.f_heap.resolveConstHandle(f_nClassConstId, f_nValueConstId); // TODO: cache this

        anScopeNextVar[iScope] = nNextVar+1;
        return iPC + 1;
        }

    }
