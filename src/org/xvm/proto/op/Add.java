package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.OpInvocable;

import org.xvm.proto.ObjectHandle.ExceptionHandle;

/**
 * ADD rvalue-target, rvalue-second, lvalue-return   ; T + T -> T
 *
 * @author gg 2017.03.08
 */
public class Add extends OpInvocable
    {
    private final int f_nTargetValue;
    private final int f_nArgValue;
    private final int f_nRetValue;

    public Add(int nTarget, int nArg, int nRet)
        {
        f_nTargetValue = nTarget;
        f_nArgValue = nArg;
        f_nRetValue = nRet;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        ExceptionHandle hException;

        try
            {
            ObjectHandle hTarget = frame.getArgument(f_nTargetValue);
            ObjectHandle hArg = frame.getArgument(f_nArgValue);

            hException = hTarget.f_clazz.f_template.invokeAdd(frame, hTarget, hArg, f_nRetValue);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            hException = e.getExceptionHandle();
            }

        if (hException == null)
            {
            return iPC + 1;
            }
        else
            {
            frame.m_hException = hException;
            return RETURN_EXCEPTION;
            }
        }
    }
