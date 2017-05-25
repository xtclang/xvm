package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.OpInvocable;
import org.xvm.proto.TypeCompositionTemplate;
import org.xvm.proto.TypeCompositionTemplate.PropertyTemplate;

/**
 * P_PREINC rvalue-target, CONST_PROPERTY, lvalue ; same as PREINC for a register
 *
 * @author gg 2017.03.08
 */
public class PPreInc extends OpInvocable
    {
    private final int f_nTarget;
    private final int f_nPropConstId;
    private final int f_nRetValue;

    public PPreInc(int nTarget, int nArg, int nRet)
        {
        f_nTarget = nTarget;
        f_nPropConstId = nArg;
        f_nRetValue = nRet;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            ObjectHandle hTarget = frame.getArgument(f_nTarget);
            if (hTarget == null)
                {
                return R_REPEAT;
                }

            TypeCompositionTemplate template = hTarget.f_clazz.f_template;

            PropertyTemplate property = getPropertyTemplate(frame, template, f_nPropConstId);

            return template.invokePreInc(frame, hTarget, property, f_nRetValue);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            frame.m_hException = e.getExceptionHandle();
            return R_EXCEPTION;
            }
        }
    }