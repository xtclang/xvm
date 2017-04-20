package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.OpInvocable;
import org.xvm.proto.TypeCompositionTemplate;
import org.xvm.proto.TypeCompositionTemplate.PropertyTemplate;

/**
 * GET rvalue-target, CONST_PROPERTY, lvalue
 *
 * @author gg 2017.03.08
 */
public class Get extends OpInvocable
    {
    private final int f_nTargetValue;
    private final int f_nPropConstId;
    private final int f_nRetValue;

    public Get(int nTarget, int nPropId, int nRet)
        {
        f_nTargetValue = nTarget;
        f_nPropConstId = nPropId;
        f_nRetValue = nRet;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        ObjectHandle hTarget = frame.f_ahVar[f_nTargetValue];

        TypeCompositionTemplate template = hTarget.f_clazz.f_template;

        PropertyTemplate property = getPropertyTemplate(frame, template, -f_nPropConstId);

        ExceptionHandle hException;

        if (hTarget.isStruct())
            {
            frame.f_ahVar[f_nRetValue] = template.getField(hTarget, property.f_sName);
            hException = null;
            }
        else
            {
            hException = template.getProperty(
                property, property.m_templateGet, frame, hTarget, frame.f_ahVar, f_nRetValue);
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
