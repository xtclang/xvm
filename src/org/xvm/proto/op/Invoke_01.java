package org.xvm.proto.op;

import org.xvm.proto.*;
import org.xvm.proto.TypeCompositionTemplate.MethodTemplate;

/**
 * INVOKE_01 op-code.
 *
 * @author gg 2017.03.08
 */
public class Invoke_01 extends OpInvocable
    {
    private final int f_nTargetValue;
    protected final int f_nMethodValue;
    private final int f_nRetValue;

    public Invoke_01(int nTarget, int nMethod, int nRet)
        {
        f_nMethodValue = nMethod;
        f_nTargetValue = nTarget;
        f_nRetValue = nRet;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        ObjectHandle hTarget = frame.f_ahVars[f_nTargetValue];

        TypeCompositionTemplate template = hTarget.f_clazz.f_template;

        MethodTemplate method = getMethodTemplate(frame, template, f_nMethodValue);

        ObjectHandle[] ahRet = new ObjectHandle[1];
        ObjectHandle hException;

        if (method.isNative())
            {
            hException = template.invokeNative01(frame, hTarget, method, ahRet);
            }
        else
            {
            ObjectHandle[] ahVars = new ObjectHandle[method.m_cVars];

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
