package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.OpInvocable;
import org.xvm.proto.TypeCompositionTemplate;
import org.xvm.proto.TypeCompositionTemplate.PropertyTemplate;

/**
 * LGET CONST_PROPERTY, lvalue ; local get (target=this)
 *
 * @author gg 2017.03.08
 */
public class LGet extends OpInvocable
    {
    private final int f_nPropConstId;
    private final int f_nRetValue;

    public LGet(int nPropId, int nRet)
        {
        f_nPropConstId = nPropId;
        f_nRetValue = nRet;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        ObjectHandle hTarget = frame.getThis();

        TypeCompositionTemplate template = hTarget.f_clazz.f_template;

        PropertyTemplate property = getPropertyTemplate(frame, template, f_nPropConstId);

        return template.getPropertyValue(frame, hTarget, property, f_nRetValue);
        }
    }
