package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.OpInvocable;
import org.xvm.proto.TypeCompositionTemplate;
import org.xvm.proto.TypeCompositionTemplate.PropertyTemplate;

/**
 * P_SET rvalue-target, CONST_PROPERTY, rvalue
 *
 * @author gg 2017.03.08
 */
public class PSet extends OpInvocable
    {
    private final int f_nTarget;
    private final int f_nPropConstId;
    private final int f_nValue;

    public PSet(int nTarget, int nPropId, int nValue)
        {
        f_nTarget = nTarget;
        f_nPropConstId = nPropId;
        f_nValue = nValue;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            ObjectHandle hTarget = frame.getArgument(f_nTarget);
            ObjectHandle hValue = frame.getArgument(f_nValue);
            if (hTarget == null || hValue == null)
                {
                return R_REPEAT;
                }

            TypeCompositionTemplate template = hTarget.f_clazz.f_template;

            PropertyTemplate property = getPropertyTemplate(frame, template, f_nPropConstId);

            return template.setPropertyValue(frame, hTarget, property, hValue);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            frame.m_hException = e.getExceptionHandle();
            return R_EXCEPTION;
            }
        }
    }
