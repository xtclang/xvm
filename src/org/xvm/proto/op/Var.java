package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.Op;

/**
 * VAR CONST_CLASS  ; (next register is an uninitialized anonymous variable)
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
    public int process(Frame frame, int iPC)
        {
        int iScope = frame.f_aiRegister[I_SCOPE];
        int nNextVar = frame.f_anNextVar[iScope];

        frame.f_ahVar[nNextVar] = frame.f_context.f_heap.ensureHandle(f_nClassConst); // TODO: cache this

        frame.f_anNextVar[iScope] = nNextVar+1;
        return iPC + 1;
        }

    }
