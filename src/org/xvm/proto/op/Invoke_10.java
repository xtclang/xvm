package org.xvm.proto.op;

import org.xvm.proto.*;

import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.TypeCompositionTemplate.MethodTemplate;
import org.xvm.proto.template.xFunction;

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
        ObjectHandle hTarget = frame.f_ahVar[f_nTargetValue];

        TypeCompositionTemplate template = hTarget.f_clazz.f_template;

        MethodTemplate method = getMethodTemplate(frame, template, -f_nMethodId);

        ObjectHandle hArg = f_nArgValue >= 0 ? frame.f_ahVar[f_nArgValue] :
                Utils.resolveConst(frame, f_nArgValue);

        ExceptionHandle hException;

        if (method.isNative())
            {
            hException = template.invokeNative10(frame, hTarget, method, hArg);
            }
        else if (template.isService())
            {
            hException = xFunction.makeAsyncHandle(method).
                    call(frame, new ObjectHandle[]{hTarget, hArg}, Utils.OBJECTS_NONE);
            }
        else
            {
            ObjectHandle[] ahVar = new ObjectHandle[method.m_cVars];
            ahVar[1] = hArg;

            hException = frame.f_context.createFrame(frame, method, hTarget, ahVar).execute();
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
