package org.xvm.proto.op;

import org.xvm.asm.MethodStructure;
import org.xvm.proto.*;
import org.xvm.proto.ObjectHandle.ExceptionHandle;

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

            ClassTemplate template = hTarget.f_clazz.f_template;

            MethodStructure method = getMethodStructure(frame, template, f_nMethodId);

            if (frame.f_adapter.isNative(method))
                {
                return template.invokeNative(frame, hTarget, method,
                        Utils.OBJECTS_NONE, Frame.RET_UNUSED);
                }

            if (template.isService() && frame.f_context != ((ServiceHandle) hTarget).m_context)
                {
                return xFunction.makeAsyncHandle(method).
                        call1(frame, new ObjectHandle[]{hTarget}, Frame.RET_UNUSED);
                }

            ObjectHandle[] ahVar = new ObjectHandle[frame.f_adapter.getVarCount(method)];

            return frame.call1(method, hTarget, ahVar, Frame.RET_UNUSED);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            frame.m_hException = e.getExceptionHandle();
            return R_EXCEPTION;
            }
        }
    }
