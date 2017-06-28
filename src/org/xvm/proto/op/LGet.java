package org.xvm.proto.op;

import org.xvm.asm.PropertyStructure;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.OpInvocable;
import org.xvm.proto.ClassTemplate;

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

        ClassTemplate template = hTarget.f_clazz.f_template;

        PropertyStructure property = getPropertyStructure(frame, template, f_nPropConstId);

        return template.getPropertyValue(frame, hTarget, property, f_nRetValue);
        }
    }
