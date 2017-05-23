package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.Op;

import org.xvm.proto.template.IndexSupport;

/**
 * A_REF rvalue-target, rvalue-index, lvalue-return ; Ref<T> = &T[Ti]
 *
 * @author gg 2017.03.08
 */
public class IRef extends Op
    {
    private final int f_nTargetValue;
    private final int f_nIndexValue;
    private final int f_nRetValue;

    public IRef(int nTarget, int nIndex, int nRet)
        {
        f_nTargetValue = nTarget;
        f_nIndexValue = nIndex;
        f_nRetValue = nRet;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            ObjectHandle hTarget = frame.getArgument(f_nTargetValue);
            if (hTarget == null)
                {
                return R_WAIT;
                }

            IndexSupport template = (IndexSupport) hTarget.f_clazz.f_template;

            return template.makeRef(frame, hTarget,
                    frame.getIndex(f_nIndexValue), f_nRetValue);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            frame.m_hException = e.getExceptionHandle();
            return R_EXCEPTION;
            }
        }
    }
