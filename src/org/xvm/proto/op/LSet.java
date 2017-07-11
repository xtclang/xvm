package org.xvm.proto.op;

import org.xvm.asm.PropertyStructure;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.OpProperty;
import org.xvm.proto.TypeComposition;

/**
 * LSET CONST_PROPERTY, rvalue ; local set (target=this)
 *
 * @author gg 2017.03.08
 */
public class LSet extends OpProperty
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
        try
            {
            ObjectHandle hTarget = frame.getThis();
            ObjectHandle hValue = frame.getArgument(f_nValue);
            if (hTarget == null || hValue == null)
                {
                return R_REPEAT;
                }

            TypeComposition clazz = hTarget.f_clazz;

            PropertyStructure property = getPropertyStructure(frame, clazz, f_nPropConstId);

            return clazz.f_template.setPropertyValue(frame, hTarget, property, hValue);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            frame.m_hException = e.getExceptionHandle();
            return R_EXCEPTION;
            }
        }
    }
