package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
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

        ObjectHandle hRef = frame.f_context.f_heapGlobal.ensureHandle(f_nClassConstId);

        assert hRef instanceof RefHandle;

        Frame.VarInfo info = new Frame.VarInfo(hRef.f_clazz, null, true);

        frame.f_aInfo[nNextVar] = info;
        frame.f_ahVar[nNextVar] = hRef;

        frame.f_anNextVar[iScope] = nNextVar + 1;

        return iPC + 1;
        }
    }
