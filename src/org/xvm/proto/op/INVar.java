package org.xvm.proto.op;

import org.xvm.asm.constants.CharStringConstant;

import org.xvm.proto.Frame;
import org.xvm.proto.Op;
import org.xvm.proto.ServiceContext;
import org.xvm.proto.TypeComposition;

/**
 * INVAR CONST_CLASS, CONST_STRING, CONST_* ; (next register is an initialized named variable)
 *
 * @author gg 2017.03.08
 */
public class INVar extends Op
    {
    final private int f_nClassConstId;
    final private int f_nNameConstId;
    final private int f_nValueConstId;

    public INVar(int nClassConstId, int nNamedConstId, int nValueConstId)
        {
        f_nClassConstId = nClassConstId;
        f_nNameConstId = nNamedConstId;
        f_nValueConstId = nValueConstId;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        int iScope   = frame.f_aiIndex[I_SCOPE];
        int nNextVar = frame.f_anNextVar[iScope];

        ServiceContext context = frame.f_context;

        TypeComposition clazz = context.f_types.ensureConstComposition(f_nClassConstId);
        CharStringConstant constName = (CharStringConstant)
                context.f_constantPool.getConstantValue(f_nNameConstId);

        frame.f_aInfo[nNextVar] = new Frame.VarInfo(clazz, constName.getValue(), false);

        // constant assignment must not fail
        frame.assignValue(nNextVar,
                frame.f_context.f_heapGlobal.ensureConstHandle(f_nValueConstId));

        frame.f_anNextVar[iScope] = nNextVar + 1;

        return iPC + 1;
        }
    }
