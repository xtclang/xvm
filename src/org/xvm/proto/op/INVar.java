package org.xvm.proto.op;

import org.xvm.asm.constants.CharStringConstant;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
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
        ServiceContext context = frame.f_context;

        TypeComposition clazz = context.f_types.ensureComposition(frame, f_nClassConstId);
        CharStringConstant constName = (CharStringConstant)
                context.f_pool.getConstant(f_nNameConstId);

        try
            {
            ObjectHandle hArg = frame.getArgument(f_nArgValue);
            if (hArg == null)
                {
                return R_REPEAT;
                }

            frame.introduceVar(clazz, constName.getValue(), Frame.VAR_STANDARD, hArg);

            return iPC + 1;
            }
        catch (ObjectHandle.ExceptionHandle.WrapperException e)
            {
            frame.m_hException = e.getExceptionHandle();
            return R_EXCEPTION;
            }
        }
    }
