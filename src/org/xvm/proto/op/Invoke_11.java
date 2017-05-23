package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.OpInvocable;
import org.xvm.proto.TypeCompositionTemplate;
import org.xvm.proto.TypeCompositionTemplate.MethodTemplate;

import org.xvm.proto.template.xFunction;
import org.xvm.proto.template.xService.ServiceHandle;

/**
 * INVOKE_11 rvalue-target, rvalue-method, rvalue-param, lvalue-return
 *
 * @author gg 2017.03.08
 */
public class Invoke_11 extends OpInvocable
    {
    private final int f_nTargetValue;
    private final int f_nMethodId;
    private final int f_nArgValue;
    private final int f_nRetValue;

    public Invoke_11(int nTarget, int nMethodId, int nArg, int nRet)
        {
        f_nTargetValue = nTarget;
        f_nMethodId = nMethodId;
        f_nArgValue = nArg;
        f_nRetValue = nRet;
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
                return R_WAIT;
                }

            TypeCompositionTemplate template = hTarget.f_clazz.f_template;

            MethodTemplate method = getMethodTemplate(frame, template, f_nMethodId);

            if (method.isNative())
                {
                return template.invokeNative(frame, hTarget, method, hArg, f_nRetValue);
                }

            if (template.isService() && frame.f_context != ((ServiceHandle) hTarget).m_context)
                {
                xFunction.makeAsyncHandle(method).
                        call1(frame, new ObjectHandle[]{hTarget, hArg}, f_nRetValue);
                return iPC + 1;
                }

            ObjectHandle[] ahVar = new ObjectHandle[method.m_cVars];
            ahVar[1] = hArg;

            frame.m_frameNext = frame.f_context.
                    createFrame1(frame, method, hTarget, ahVar, f_nRetValue);
            return R_CALL;
            }
        catch (ExceptionHandle.WrapperException e)
            {
            frame.m_hException = e.getExceptionHandle();
            return R_EXCEPTION;
            }
        }
    }
