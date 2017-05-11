package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle.ArrayHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.Op;
import org.xvm.proto.template.xArray;

/**
 * A_REF rvalue-target, rvalue-index, lvalue-return ; Ref<T> = &T[Ti]
 *
 * @author gg 2017.03.08
 */
public class ARef extends Op
    {
    private final int f_nTargetValue;
    private final int f_nIndexValue;
    private final int f_nRetValue;

    public ARef(int nTarget, int nIndex, int nRet)
        {
        f_nTargetValue = nTarget;
        f_nIndexValue = nIndex;
        f_nRetValue = nRet;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        ExceptionHandle hException;

        try
            {
            ArrayHandle hTarget = (ArrayHandle) frame.getArgument(f_nTargetValue);
            xArray template = (xArray) hTarget.f_clazz.f_template;

            long lIndex = frame.getIndex(f_nIndexValue);

            hException = template.getArrayValue(frame, hTarget, lIndex, f_nRetValue);
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
