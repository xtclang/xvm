package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.OpInvocable;
import org.xvm.proto.TypeCompositionTemplate;
import org.xvm.proto.TypeCompositionTemplate.MethodTemplate;

/**
 * ADD rvalue-target, rvalue-second, lvalue-return   ; T + T -> T
 *
 * @author gg 2017.03.08
 */
public class Add extends OpInvocable
    {
    private final int f_nTargetValue;
    private final int f_nArgValue;
    private final int f_nRetValue;

    public Add(int nTarget, int nArg, int nRet)
        {
        f_nTargetValue = nTarget;
        f_nArgValue = nArg;
        f_nRetValue = nRet;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        ObjectHandle hTarget = frame.f_ahVars[f_nTargetValue];

        TypeCompositionTemplate template = hTarget.f_clazz.f_template;

        ObjectHandle hArg = f_nArgValue >= 0 ? frame.f_ahVars[f_nArgValue] : resolveConst(frame, hTarget.f_clazz, f_nArgValue);
        ObjectHandle[] ahRet = new ObjectHandle[1];
        ObjectHandle hException;

        hException = template.invokeAdd(frame, hTarget, hArg, ahRet);

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
