package org.xvm.proto.op;

import org.xvm.proto.*;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.TypeCompositionTemplate.MethodTemplate;
import org.xvm.proto.template.xFunction;

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
        ExceptionHandle hException;
        try
            {
            ObjectHandle hTarget = frame.getArgument(f_nTargetValue);

            TypeCompositionTemplate template = hTarget.f_clazz.f_template;

            MethodTemplate method = getMethodTemplate(frame, template, f_nMethodId);

            if (method.isNative())
                {
                hException = template.invokeNative(frame, hTarget, method, Utils.OBJECTS_NONE, Frame.R_UNUSED);
                }
            else if (template.isService())
                {
                hException = xFunction.makeAsyncHandle(method).
                        call1(frame, new ObjectHandle[]{hTarget}, Frame.R_UNUSED);
                }
            else
                {
                ObjectHandle[] ahVar = new ObjectHandle[method.m_cVars];

                hException = frame.call1(method, hTarget, ahVar, Frame.R_UNUSED);
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
