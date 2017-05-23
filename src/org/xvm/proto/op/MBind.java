package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.OpInvocable;
import org.xvm.proto.TypeCompositionTemplate;
import org.xvm.proto.TypeCompositionTemplate.MethodTemplate;
import org.xvm.proto.template.xFunction;

/**
 * MBIND rvalue-target, CONST-METHOD, lvalue-fn-result
 *
 * @author gg 2017.03.08
 */
public class MBind extends OpInvocable
    {
    private final int f_nTargetValue;
    private final int f_nMethodId;
    private final int f_nResultValue;

    public MBind(int nTarget, int nMethodId, int nRet)
        {
        f_nTargetValue = nTarget;
        f_nMethodId = nMethodId;
        f_nResultValue = nRet;
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

            TypeCompositionTemplate template = hTarget.f_clazz.f_template;

            MethodTemplate method = getMethodTemplate(frame, template, f_nMethodId);

            return frame.assignValue(f_nResultValue, template.isService() ?
                    xFunction.makeAsyncHandle(method).bind(0, hTarget) :
                    xFunction.makeHandle(method).bind(0, hTarget));
            }
        catch (ExceptionHandle.WrapperException e)
            {
            frame.m_hException = e.getExceptionHandle();
            return R_EXCEPTION;
            }
        }
    }
