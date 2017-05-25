package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.OpInvocable;
import org.xvm.proto.TypeCompositionTemplate;

/**
 * NEG rvalue-target, lvalue-return   ; -T -> T
 *
 * @author gg 2017.03.08
 */
public class Neg extends OpInvocable
    {
    private final int f_nArgValue;
    private final int f_nRetValue;

    public Neg(int nArg, int nRet)
        {
        f_nArgValue = nArg;
        f_nRetValue = nRet;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            ObjectHandle hTarget = frame.getArgument(f_nArgValue);
            if (hTarget == null)
                {
                return R_REPEAT;
                }

            TypeCompositionTemplate template = hTarget.f_clazz.f_template;

            return template.invokeNeg(frame, hTarget, f_nRetValue);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            frame.m_hException = e.getExceptionHandle();
            return R_EXCEPTION;
            }
        }
    }
