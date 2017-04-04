package org.xvm.proto.op;

import org.xvm.proto.*;
import org.xvm.proto.TypeCompositionTemplate.MethodTemplate;

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
        ObjectHandle hTarget = frame.f_ahVar[f_nTargetValue];

        TypeCompositionTemplate template = hTarget.f_clazz.f_template;

        MethodTemplate method = getMethodTemplate(frame, template, -f_nMethodId);

        ObjectHandle hArg = f_nArgValue >= 0 ? frame.f_ahVar[f_nArgValue] :
                Utils.resolveConst(frame, method.m_argTypeName[0], f_nArgValue);

        ObjectHandle[] ahReturn;
        ObjectHandle hException;

        if (method.isNative())
            {
            ahReturn = new ObjectHandle[1];
            hException = template.invokeNative11(frame, hTarget, method, hArg, ahReturn);
            }
        else
            {
            ObjectHandle[] ahVars = new ObjectHandle[method.m_cVars];
            ahVars[1] = hArg;

            Frame frameNew = frame.f_context.createFrame(frame, method, hTarget, ahVars);
            hException = frameNew.execute();
            ahReturn = frameNew.f_ahReturn;
            }

        if (hException == null)
            {
            frame.f_ahVar[f_nRetValue] = ahReturn[0];
            return iPC + 1;
            }
        else
            {
            frame.m_hException = hException;
            return RETURN_EXCEPTION;
            }
        }

    }
