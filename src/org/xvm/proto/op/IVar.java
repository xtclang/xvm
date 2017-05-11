package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.Op;
import org.xvm.proto.ServiceContext;
import org.xvm.proto.TypeComposition;

/**
 * IVAR CONST_CLASS, rvalue-src ; (next register is an initialized anonymous variable)
 *
 * @author gg 2017.03.08
 */
public class IVar extends Op
    {
    final private int f_nClassConstId;
    final private int f_nArgValue;

    public IVar(int nClassConstId, int nValue)
        {
        f_nClassConstId = nClassConstId;
        f_nArgValue = nValue;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        int iScope   = frame.f_aiIndex[I_SCOPE];
        int nNextVar = frame.f_anNextVar[iScope];

        ServiceContext context = frame.f_context;
        TypeComposition clazz = context.f_types.ensureConstComposition(f_nClassConstId);

        frame.f_aInfo[nNextVar] = new Frame.VarInfo(clazz, false);

        ExceptionHandle hException;
        try
            {
            hException = frame.assignValue(nNextVar, frame.getArgument(f_nArgValue));
            }
        catch (ObjectHandle.ExceptionHandle.WrapperException e)
            {
            hException = e.getExceptionHandle();
            }

        if (hException == null)
            {
            frame.f_anNextVar[iScope] = nNextVar + 1;
            return iPC + 1;
            }
        else
            {
            frame.m_hException = hException;
            return RETURN_EXCEPTION;
            }
        }

    }
