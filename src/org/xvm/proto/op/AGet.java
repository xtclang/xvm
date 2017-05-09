package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.Op;

/**
 * A_GET rvalue-target, rvalue-index, lvalue-return ; T = T[Ti]
 *
 * @author gg 2017.03.08
 */
public class AGet extends Op
    {
    private final int f_nTargetValue;
    private final int f_nIndexValue;
    private final int f_nRetValue;

    public AGet(int nTarget, int nArg, int nRet)
        {
        f_nTargetValue = nTarget;
        f_nIndexValue = nArg;
        f_nRetValue = nRet;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        ExceptionHandle hException;

        try
            {
            ObjectHandle hTarget = frame.getArgument(f_nTargetValue);
            ObjectHandle hIndex = frame.getArgument(f_nIndexValue);

            hException = hTarget.f_clazz.f_template.invokeAdd(frame, hTarget, hIndex, f_nRetValue);
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
