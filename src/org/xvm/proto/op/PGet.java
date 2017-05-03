package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.OpInvocable;
import org.xvm.proto.TypeCompositionTemplate;
import org.xvm.proto.TypeCompositionTemplate.PropertyTemplate;

/**
 * P_GET rvalue-target, CONST_PROPERTY, lvalue
 *
 * @author gg 2017.03.08
 */
public class PGet extends OpInvocable
    {
    private final int f_nTarget;
    private final int f_nPropConstId;
    private final int f_nRetValue;

    public PGet(int nTarget, int nPropId, int nRet)
        {
        f_nTarget = nTarget;
        f_nPropConstId = nPropId;
        f_nRetValue = nRet;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        ExceptionHandle hException;

        try
            {
            ObjectHandle hTarget = frame.getArgument(f_nTarget);

            TypeCompositionTemplate template = hTarget.f_clazz.f_template;

            PropertyTemplate property = getPropertyTemplate(frame, template, f_nPropConstId);

            hException = template.getPropertyValue(frame, hTarget, property, f_nRetValue);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            hException = e.getExceptionHandle();
            }

        if (hException == null)
            {
            return iPC + 1;
            }
        else
            {
            frame.m_hException = hException;
            return RETURN_EXCEPTION;
            }
        }
    }
