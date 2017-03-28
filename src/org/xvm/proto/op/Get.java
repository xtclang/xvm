package org.xvm.proto.op;

import org.xvm.proto.*;
import org.xvm.proto.TypeCompositionTemplate.PropertyTemplate;
import org.xvm.proto.TypeCompositionTemplate.MethodTemplate;

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
        ObjectHandle hTarget = frame.f_ahVars[f_nTargetValue];

        TypeCompositionTemplate template = hTarget.f_clazz.f_template;

        PropertyTemplate property = getPropertyTemplate(frame, template, f_nPropConstId);

        MethodTemplate method = property.m_templateGet;

        if (method == null)
            {
            frame.f_ahVars[f_nRetValue] = template.getProperty(hTarget, property.f_sName);
            }
        else
            {
            // almost identical to the second part of Invoke_01
            ObjectHandle[] ahRet = new ObjectHandle[1];
            ObjectHandle hException;

            if (method.isNative())
                {
                hException = template.invokeNative01(frame, hTarget, method, ahRet);
                }
            else
                {
                ObjectHandle[] ahVars = new ObjectHandle[method.m_cVars];

                hException = new Frame(frame.f_context, frame, hTarget, method, ahVars, ahRet).execute();
                }

            if (hException == null)
                {
                frame.f_ahVars[f_nRetValue] = ahRet[0];
                }
            else
                {
                frame.m_hException = hException;
                return RETURN_EXCEPTION;
                }
            }
        return iPC + 1;
        }
    }
