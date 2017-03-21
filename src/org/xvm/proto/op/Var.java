package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.Op;

/**
 * VAR op-code.
 *
 * @author gg 2017.03.08
 */
public class Var extends Op
    {
    private final int f_nClassConst;

    public Var(int nClassConst)
        {
        f_nClassConst = nClassConst;
        }

    @Override
    public int process(Frame frame, int iPC, int[] aiRegister, int[] anScopeNextVar)
        {
        int iScope = aiRegister[I_SCOPE];
        int nNextVar = anScopeNextVar[iScope];

        frame.f_ahVars[nNextVar] = frame.f_context.f_heap.ensureHandle(f_nClassConst); // TODO: cache this

        anScopeNextVar[iScope] = nNextVar+1;
        return iPC + 1;
        }

    }
