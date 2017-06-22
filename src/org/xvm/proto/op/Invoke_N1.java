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
 * INVOKE_N1  rvalue-target, CONST-METHOD, #params:(rvalue), lvalue-return
 *
 * @author gg 2017.03.08
 */
public class Invoke_N1 extends OpInvocable
    {
    private final int f_nTargetValue;
    private final int f_nMethodId;
    private final int[] f_anArgValue;
    private final int f_nRetValue;

    public Invoke_N1(int nTarget, int nMethodId, int[] anArg, int nRet)
        {
        f_nTargetValue = nTarget;
        f_nMethodId = nMethodId;
        f_anArgValue = anArg;
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
                return R_REPEAT;
                }

            TypeCompositionTemplate template = hTarget.f_clazz.f_template;
            MethodTemplate method = getMethodTemplate(frame, template, f_nMethodId);

            if (method.isNative())
                {
                ObjectHandle[] ahArg = frame.getArguments(f_anArgValue, method.m_cVars, 0);
                if (ahArg == null)
                    {
                    return R_REPEAT;
                    }
                return template.invokeNative(frame, hTarget, method, ahArg, f_nRetValue);
                }

            ObjectHandle[] ahVar = frame.getArguments(f_anArgValue, method.m_cVars, 1);
            if (ahVar == null)
                {
                return R_REPEAT;
                }

            if (template.isService() && frame.f_context != ((ServiceHandle) hTarget).m_context)
                {
                ahVar[0] = hTarget;
                return xFunction.makeAsyncHandle(method).call1(frame, ahVar, f_nRetValue);
                }

            return frame.call1(method, hTarget, ahVar, f_nRetValue);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            frame.m_hException = e.getExceptionHandle();
            return R_EXCEPTION;
            }
        }
    }
