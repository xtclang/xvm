package org.xvm.proto.op;

import org.xvm.proto.*;
import org.xvm.proto.TypeCompositionTemplate.MethodTemplate;

/**
 * INVOKE_01 rvalue-target, rvalue-method
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
        ObjectHandle hTarget = frame.f_ahVar[f_nTargetValue];

        TypeCompositionTemplate template = hTarget.f_clazz.f_template;

        MethodTemplate method = getMethodTemplate(frame, template, -f_nMethodId);

        ObjectHandle[] ahRet;
        ObjectHandle hException;

        if (method.isNative())
            {
            ahRet = new ObjectHandle[1];
            hException = template.invokeNative01(frame, hTarget, method, ahRet);
            }
        else
            {
            ObjectHandle[] ahVars = new ObjectHandle[method.m_cVars];
            Frame frameNew = frame.f_context.createFrame(frame, method, hTarget, ahVars);

            hException = frameNew.execute();
            ahRet = frameNew.f_ahReturn;
            }

        if (hException == null)
            {
            frame.f_ahVar[f_nRetValue] = ahRet[0];
            return iPC + 1;
            }
        else
            {
            frame.m_hException = hException;
            return RETURN_EXCEPTION;
            }
        }
    }
