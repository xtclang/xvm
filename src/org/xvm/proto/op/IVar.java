package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.Op;
import org.xvm.proto.ServiceContext;
import org.xvm.proto.TypeComposition;

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
        int iScope   = frame.f_aiIndex[I_SCOPE];
        int nNextVar = frame.f_anNextVar[iScope];

        ServiceContext context = frame.f_context;
        TypeComposition clazz = context.f_types.ensureConstComposition(f_nClassConstId);

        frame.f_aInfo[nNextVar] = new Frame.VarInfo(clazz, false);

        // constant assignment must not fail
        frame.assignValue(nNextVar, context.f_heapGlobal.ensureConstHandle(f_nValueConstId));

        frame.f_anNextVar[iScope] = nNextVar + 1;

        return iPC + 1;
        }

    }
