package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.OpInvocable;
import org.xvm.proto.TypeCompositionTemplate;

import org.xvm.proto.ObjectHandle.ExceptionHandle;

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
        ObjectHandle hTarget = frame.f_ahVar[f_nTargetValue];
        ObjectHandle hArg = frame.f_ahVar[f_nArgValue];
        ObjectHandle[] ahRet = new ObjectHandle[1];

        TypeCompositionTemplate template = hTarget.f_clazz.f_template;

        ExceptionHandle hException = template.invokeAdd(frame, hTarget, hArg, ahRet);

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
