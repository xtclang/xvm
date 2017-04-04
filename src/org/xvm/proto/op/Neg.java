package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.OpInvocable;
import org.xvm.proto.TypeCompositionTemplate;

/**
 * NEG rvalue-target, lvalue-return   ; -T -> T
 *
 * @author gg 2017.03.08
 */
public class Neg extends OpInvocable
    {
    private final int f_nTargetValue;
    private final int f_nRetValue;

    public Neg(int nTarget, int nRet)
        {
        f_nTargetValue = nTarget;
        f_nRetValue = nRet;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        ObjectHandle hTarget = frame.f_ahVar[f_nTargetValue];
        ObjectHandle[] ahRet = new ObjectHandle[1];

        TypeCompositionTemplate template = hTarget.f_clazz.f_template;

        ObjectHandle hException = template.invokeNeg(frame, hTarget, ahRet);

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
