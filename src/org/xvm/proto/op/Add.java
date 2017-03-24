package org.xvm.proto.op;

import org.xvm.proto.*;
import org.xvm.proto.TypeCompositionTemplate.MethodTemplate;

/**
 * INVOKE_11 rvalue-target, rvalue-method, rvalue-param, lvalue-return
 *
 * @author gg 2017.03.08
 */
public class Add extends OpInvocable
    {
    private final int f_nTargetValue;
    private final int f_nMethodValue;
    private final int f_nArgValue;
    private final int f_nRetValue;

    public Add(int nTarget, int nMethod, int nArg, int nRet)
        {
        f_nTargetValue = nTarget;
        f_nMethodValue = nMethod;
        f_nArgValue = nArg;
        f_nRetValue = nRet;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        ObjectHandle hTarget = frame.f_ahVars[f_nTargetValue];

        TypeCompositionTemplate template = hTarget.f_clazz.f_template;

        MethodTemplate method = getMethodTemplate(frame, template, f_nMethodValue);

        ObjectHandle hArg = f_nArgValue >= 0 ? frame.f_ahVars[f_nArgValue] : resolveConstArgument(frame, 0, f_nArgValue);
        ObjectHandle[] ahRet = new ObjectHandle[1];
        ObjectHandle hException;

        if (method.isNative())
            {
            hException = template.invokeNative11(frame, hTarget, method, hArg, ahRet);
            }
        else
            {
            ObjectHandle[] ahVars = new ObjectHandle[method.m_cVars];
            ahVars[1] = hArg;

            hException = new Frame(frame.f_context, frame, hTarget, method, ahVars, ahRet).execute();
            }

        if (hException == null)
            {
            frame.f_ahVars[f_nRetValue] = ahRet[0];
            return iPC + 1;
            }
        else
            {
            frame.m_hException = hException;
            return RETURN_EXCEPTION;
            }
        }

    }
