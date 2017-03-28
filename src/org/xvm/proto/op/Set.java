package org.xvm.proto.op;

import org.xvm.proto.*;
import org.xvm.proto.TypeCompositionTemplate.MethodTemplate;
import org.xvm.proto.TypeCompositionTemplate.PropertyTemplate;

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

    public Set(int nTarget, int nPropId, int nRet)
        {
        f_nTargetValue = nTarget;
        f_nPropConstId = nPropId;
        f_nValue = nRet;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        ObjectHandle hTarget = frame.f_ahVars[f_nTargetValue];
        TypeCompositionTemplate template = hTarget.f_clazz.f_template;

        PropertyTemplate property = getPropertyTemplate(frame, template, f_nPropConstId);
        MethodTemplate method = property.m_templateSet;

        ObjectHandle hArg = f_nValue >= 0 ? frame.f_ahVars[f_nValue] :
                resolveConst(frame, method.m_argTypeName[0], -f_nValue);

        if (method == null)
            {
            template.setProperty(hTarget, property.f_sName, hArg);
            }
        else
            {
            // almost identical to the second part of Invoke_10
            ObjectHandle[] ahRet = new ObjectHandle[1];
            ObjectHandle[] ahVars = new ObjectHandle[method.m_cVars];

            ObjectHandle hException = new Frame(frame.f_context, frame, hTarget, method, ahVars, ahRet).execute();

            if (hException != null)
                {
                frame.m_hException = hException;
                return RETURN_EXCEPTION;
                }
            }
        return iPC + 1;
        }
    }
