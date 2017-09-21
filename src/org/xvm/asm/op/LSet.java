package org.xvm.asm.op;

import org.xvm.asm.constants.PropertyConstant;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.asm.OpProperty;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

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

    public LSet(DataInput in)
            throws IOException
        {
        f_nPropConstId = in.readInt();
        f_nValue = in.readInt();
        }

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.write(OP_L_SET);
        out.writeInt(f_nPropConstId);
        out.writeInt(f_nValue);
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

            PropertyConstant constProperty = (PropertyConstant)
                    frame.f_context.f_pool.getConstant(f_nPropConstId);

            return hTarget.f_clazz.f_template.setPropertyValue(
                    frame, hTarget, constProperty.getName(), hValue);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }
    }
