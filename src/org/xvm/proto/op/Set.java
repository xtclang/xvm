package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.OpInvocable;
import org.xvm.proto.TypeCompositionTemplate;
import org.xvm.proto.TypeCompositionTemplate.PropertyTemplate;
import org.xvm.proto.Utils;

/**
 * Get rvalue-target, CONST_PROPERTY, rvalue
 *
 * @author gg 2017.03.08
 */
public class Set extends OpInvocable
    {
    private final int f_nTargetValue;
    private final int f_nPropConstId;
    private final int f_nValue;

    public Set(int nTarget, int nPropId, int nValue)
        {
        f_nTargetValue = nTarget;
        f_nPropConstId = nPropId;
        f_nValue = nValue;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        ObjectHandle hTarget = frame.f_ahVar[f_nTargetValue];
        TypeCompositionTemplate template = hTarget.f_clazz.f_template;

        PropertyTemplate property = getPropertyTemplate(frame, template, -f_nPropConstId);

        ObjectHandle hValue = f_nValue >= 0 ? frame.f_ahVar[f_nValue] :
                Utils.resolveConst(frame, f_nValue);

        ExceptionHandle hException;

        if (hTarget.isStruct())
            {
            hException = template.setField(hTarget, property.f_sName, hValue);
            }
        else
            {
            hException = template.setProperty(property, property.m_templateSet, frame, hTarget, hValue);
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
