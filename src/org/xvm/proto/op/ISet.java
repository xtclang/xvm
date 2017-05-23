package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.Op;

import org.xvm.proto.template.IndexSupport;

/**
 * A_SET rvalue-target, rvalue-index, rvalue-new-value ; T[Ti] = T
 *
 * @author gg 2017.03.08
 */
public class ISet extends Op
    {
    private final int f_nTargetValue;
    private final int f_nIndexValue;
    private final int f_nValue;

    public ISet(int nTarget, int nIndex, int nValue)
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
            ObjectHandle hTarget = frame.getArgument(f_nTargetValue);
            ObjectHandle hArg = frame.getArgument(f_nValue);
            if (hTarget == null || hArg == null)
                {
                return R_WAIT;
                }

            IndexSupport template = (IndexSupport) hTarget.f_clazz.f_template;

            hException = template.assignArrayValue(hTarget,
                    frame.getIndex(f_nIndexValue), hArg);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            frame.m_hException = e.getExceptionHandle();
            return R_EXCEPTION;
            }

        if (hException == null)
            {
            return iPC + 1;
            }
        else
            {
            frame.m_hException = hException;
            return R_EXCEPTION;
            }
        }
    }
