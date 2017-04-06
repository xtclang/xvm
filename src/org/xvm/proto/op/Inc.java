package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.OpInvocable;
import org.xvm.proto.TypeCompositionTemplate;

/**
 * INC lvalue-target
 *
 * @author gg 2017.03.08
 */
public class Inc extends OpInvocable
    {
    private final int f_nTargetValue;

    public Inc(int nTarget)
        {
        f_nTargetValue = nTarget;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        ObjectHandle hTarget = frame.f_ahVar[f_nTargetValue];
        ObjectHandle[] ahRet = new ObjectHandle[1];

        TypeCompositionTemplate template = hTarget.f_clazz.f_template;

        ExceptionHandle hException = template.invokeInc(frame, hTarget, ahRet);

        if (hException == null)
            {
            frame.f_ahVar[f_nTargetValue] = ahRet[0];
            return iPC + 1;
            }
        else
            {
            frame.m_hException = hException;
            return RETURN_EXCEPTION;
            }
        }

    }
