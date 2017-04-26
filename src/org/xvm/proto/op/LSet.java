package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.OpInvocable;
import org.xvm.proto.TypeCompositionTemplate;
import org.xvm.proto.TypeCompositionTemplate.PropertyTemplate;

/**
 * LSET CONST_PROPERTY, rvalue ; local set (target=this)
 *
 * @author gg 2017.03.08
 */
public class LSet extends OpInvocable
    {
    private final int f_nPropConstId;
    private final int f_nValue;

    public LSet(int nPropId, int nValue)
        {
        f_nPropConstId = nPropId;
        f_nValue = nValue;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        ExceptionHandle hException;
        try
            {
            ObjectHandle hTarget = frame.getThis();
            ObjectHandle hValue = frame.getArgument(f_nValue);

            TypeCompositionTemplate template = hTarget.f_clazz.f_template;

            PropertyTemplate property = getPropertyTemplate(frame, template, -f_nPropConstId);

            if (hTarget.isStruct())
                {
                hException = template.setField(hTarget, property, hValue);
                }
            else
                {
                hException = template.setProperty(frame, hTarget, property, hValue);
                }
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
