package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.OpInvocable;
import org.xvm.proto.TypeCompositionTemplate;
import org.xvm.proto.TypeCompositionTemplate.MethodTemplate;
import org.xvm.proto.Utils;
import org.xvm.proto.template.xFunction;

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
        ExceptionHandle hException;

        try
            {
            ObjectHandle hTarget = frame.getArgument(f_nTargetValue);

            TypeCompositionTemplate template = hTarget.f_clazz.f_template;

            MethodTemplate method = getMethodTemplate(frame, template, -f_nMethodId);

            ObjectHandle hArg = frame.getArgument(f_nArgValue);

            if (method.isNative())
                {
                hException = template.invokeNative11(frame, hTarget, method, hArg, null, f_nRetValue);
                }
            else if (template.isService())
                {
                ObjectHandle[] ahReturn = new ObjectHandle[1];

                hException = xFunction.makeAsyncHandle(method).
                        call(frame, new ObjectHandle[]{hTarget, hArg}, ahReturn);

                if (hException == null)
                    {
                    hException = frame.assignValue(f_nRetValue, ahReturn[0]);
                    }
                }
            else
                {
                ObjectHandle[] ahVar = new ObjectHandle[method.m_cVars];
                ahVar[1] = hArg;

                Frame frameNew = frame.f_context.createFrame(frame, method, hTarget, ahVar);

                hException = frameNew.execute();

                if (hException == null)
                    {
                    hException = frame.assignValue(f_nRetValue, frameNew.f_ahReturn[0]);
                    }
                }
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
