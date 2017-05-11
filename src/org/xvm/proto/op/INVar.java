package org.xvm.proto.op;

import org.xvm.asm.constants.CharStringConstant;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.Op;
import org.xvm.proto.ServiceContext;
import org.xvm.proto.TypeComposition;

/**
 * INVAR CONST_CLASS, CONST_STRING, rvalue-src ; (next register is an initialized named variable)
 *
 * @author gg 2017.03.08
 */
public class INVar extends Op
    {
    final private int f_nClassConstId;
    final private int f_nNameConstId;
    final private int f_nArgValue;

    public INVar(int nClassConstId, int nNamedConstId, int nValue)
        {
        f_nClassConstId = nClassConstId;
        f_nNameConstId = nNamedConstId;
        f_nArgValue = nValue;
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
