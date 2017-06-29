package org.xvm.proto.op;

import org.xvm.asm.MethodStructure;
import org.xvm.proto.ClassTemplate;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.OpInvocable;

import org.xvm.proto.template.xFunction;
import org.xvm.proto.template.xService.ServiceHandle;

/**
 * INVOKE_10 rvalue-target, rvalue-method, rvalue-param
 *
 * @author gg 2017.03.08
 */
public class Invoke_10 extends OpInvocable
    {
    private final int f_nTargetValue;
    private final int f_nMethodId;
    private final int f_nArgValue;

    public Invoke_10(int nTarget, int nMethodId, int nArg)
        {
        f_nTargetValue = nTarget;
        f_nMethodId = nMethodId;
        f_nArgValue = nArg;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            ObjectHandle hTarget = frame.getArgument(f_nTargetValue);
            ObjectHandle hArg = frame.getArgument(f_nArgValue);

            if (hTarget == null || hArg == null)
                {
                return R_REPEAT;
                }

            ClassTemplate template = hTarget.f_clazz.f_template;
            MethodStructure method = getMethodStructure(frame, template, f_nMethodId);

            if (frame.f_adapter.isNative(method))
                {
                return template.invokeNative(frame, hTarget, method, hArg, Frame.RET_UNUSED);
                }

            if (template.isService() && frame.f_context != ((ServiceHandle) hTarget).m_context)
                {
                return xFunction.makeAsyncHandle(method).
                        call1(frame, new ObjectHandle[]{hTarget, hArg}, Frame.RET_UNUSED);
                }

            ObjectHandle[] ahVar = new ObjectHandle[frame.f_adapter.getVarCount(method)];
            ahVar[1] = hArg;

            return frame.call1(method, hTarget, ahVar, Frame.RET_UNUSED);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            frame.m_hException = e.getExceptionHandle();
            return R_EXCEPTION;
            }
        }
    }
