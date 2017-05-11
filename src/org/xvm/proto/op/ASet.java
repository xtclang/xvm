package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ArrayHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.Op;
import org.xvm.proto.template.xArray;

/**
 * A_SET rvalue-target, rvalue-index, rvalue-new-value ; T[Ti] = T
 *
 * @author gg 2017.03.08
 */
public class ASet extends Op
    {
    private final int f_nTargetValue;
    private final int f_nIndexValue;
    private final int f_nValue;

    public ASet(int nTarget, int nIndex, int nValue)
        {
        f_nTargetValue = nTarget;
        f_nIndexValue = nIndex;
        f_nValue = nValue;
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
            ObjectHandle hValue = frame.getArgument(f_nValue);

            hException = template.setArrayValue(frame, hTarget, lIndex, hValue);
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
