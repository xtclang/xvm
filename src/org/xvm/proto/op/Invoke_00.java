package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.OpInvocable;
import org.xvm.proto.TypeCompositionTemplate;
import org.xvm.proto.TypeCompositionTemplate.MethodTemplate;
import org.xvm.proto.Utils;

import org.xvm.proto.template.xFunction;
import org.xvm.proto.template.xService.ServiceHandle;

/**
 * INVOKE_00 rvalue-target, rvalue-method
 *
 * @author gg 2017.03.08
 */
public class Invoke_00 extends OpInvocable
    {
    private final int f_nTargetValue;
    private final int f_nMethodId;

    public Invoke_00(int nTarget, int nMethodId)
        {
        f_nTargetValue = nTarget;
        f_nMethodId = nMethodId;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            ObjectHandle hTarget = frame.getArgument(f_nTargetValue);
            if (hTarget == null)
                {
                return R_REPEAT;
                }

            TypeCompositionTemplate template = hTarget.f_clazz.f_template;

            MethodTemplate method = getMethodTemplate(frame, template, f_nMethodId);

            if (method.isNative())
                {
                return template.invokeNative(frame, hTarget, method,
                        Utils.OBJECTS_NONE, Frame.RET_UNUSED);
                }

            if (template.isService() && frame.f_context != ((ServiceHandle) hTarget).m_context)
                {
                return xFunction.makeAsyncHandle(method).
                        call1(frame, new ObjectHandle[]{hTarget}, Frame.RET_UNUSED);
                }

            ObjectHandle[] ahVar = new ObjectHandle[method.m_cVars];

            return frame.call1(method, hTarget, ahVar, Frame.RET_UNUSED);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            frame.m_hException = e.getExceptionHandle();
            return R_EXCEPTION;
            }
        }
    }
