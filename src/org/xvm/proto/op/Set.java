package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.OpInvocable;
import org.xvm.proto.TypeCompositionTemplate;
import org.xvm.proto.TypeCompositionTemplate.MethodTemplate;
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
        MethodTemplate method = property.m_templateSet;

        ObjectHandle hArg = f_nValue >= 0 ? frame.f_ahVar[f_nValue] :
                Utils.resolveConst(frame, method.m_argTypeName[0], -f_nValue);

        if (method == null)
            {
            template.setProperty(hTarget, property.f_sName, hArg);
            }
        else
            {
            // almost identical to the second part of Invoke_10
            ObjectHandle[] ahVar = new ObjectHandle[method.m_cVars];

            ExceptionHandle hException = frame.f_context.createFrame(frame, method, hTarget, ahVar).execute();

            if (hException != null)
                {
                frame.m_hException = hException;
                return RETURN_EXCEPTION;
                }
            }
        return iPC + 1;
        }
    }
