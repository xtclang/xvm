package org.xvm.proto.op;

import org.xvm.proto.*;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.TypeCompositionTemplate.MethodTemplate;
import org.xvm.proto.template.xFunction;

/**
 * INVOKE_01 rvalue-target, rvalue-method, lvalue-return
 *
 * @author gg 2017.03.08
 */
public class Invoke_01 extends OpInvocable
    {
    private final int f_nTargetValue;
    private final int f_nMethodId;
    private final int f_nRetValue;

    public Invoke_01(int nTarget, int nMethodId, int nRet)
        {
        f_nTargetValue = nTarget;
        f_nMethodId = nMethodId;
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

            MethodTemplate method = getMethodTemplate(frame, template, f_nMethodId);

            if (method.isNative())
                {
                hException = template.invokeNative(frame, hTarget, method, Utils.OBJECTS_NONE, f_nRetValue);
                }
            else if (template.isService())
                {
                hException = xFunction.makeAsyncHandle(method).
                        call1(frame, new ObjectHandle[]{hTarget}, f_nRetValue);
                }
            else
                {
                ObjectHandle[] ahVar = new ObjectHandle[method.m_cVars];

                hException = frame.f_context.
                        createFrame1(frame, method, hTarget, ahVar, f_nRetValue).execute();
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
