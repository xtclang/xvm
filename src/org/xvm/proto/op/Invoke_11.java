package org.xvm.proto.op;

import org.xvm.proto.*;
import org.xvm.proto.TypeCompositionTemplate.MethodTemplate;

/**
 * INVOKE_11 op-code.
 *
 * @author gg 2017.03.08
 */
public class Invoke_11 extends OpInvocable
    {
    private final int f_nTargetValue;
    private final int f_nArgValue;
    private final int f_nRetValue;

    public Invoke_11(int nTarget, int nMethod, int nArg, int nRet)
        {
        super(nMethod);

        f_nTargetValue = nTarget;
        f_nArgValue = nArg;
        f_nRetValue = nRet;
        }

    @Override
    public int process(Frame frame, int iPC, int[] aiRegister, int[] anScopeNextVar)
        {
        ObjectHandle hTarget = frame.f_ahVars[f_nTargetValue];
        ObjectHandle hArg    = frame.f_ahVars[f_nArgValue];

        TypeCompositionTemplate template = hTarget.m_clazz.f_template;

        MethodTemplate method = getMethodTemplate(frame, template);

        ObjectHandle[] ahRet = new ObjectHandle[1]; // either an exception or a return handle
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
