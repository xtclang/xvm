package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
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

        try
            {
            ObjectHandle hArg = frame.getArgument(f_nArgValue);
            if (hArg == null)
                {
                return R_WAIT;
                }
            frame.introduceVar(nNextVar, clazz, null, Frame.VAR_STANDARD, hArg);

            frame.f_anNextVar[iScope] = nNextVar + 1;
            return iPC + 1;
            }
        catch (ObjectHandle.ExceptionHandle.WrapperException e)
            {
            frame.m_hException = e.getExceptionHandle();
            return R_EXCEPTION;
            }
        }

    }
