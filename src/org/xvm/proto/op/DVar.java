package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.Op;

import org.xvm.proto.template.xRef.RefHandle;

/**
 * DVAR CONST_REF_CLASS ; next register is an anonymous "dynamic reference" variable
 *
 * @author gg 2017.03.08
 */
public class DVar extends Op
    {
    final private int f_nClassConstId;

    public DVar(int nClassConstId)
        {
        f_nClassConstId = nClassConstId;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        int iScope   = frame.f_aiIndex[I_SCOPE];
        int nNextVar = frame.f_anNextVar[iScope];

        RefHandle hRef = (RefHandle) frame.f_context.f_heapGlobal.ensureHandle(f_nClassConstId);

        frame.introduceVar(nNextVar, hRef.f_clazz, null, Frame.VAR_DYNAMIC_REF, hRef);

        frame.f_anNextVar[iScope] = nNextVar + 1;

        return iPC + 1;
        }
    }
