package org.xvm.proto.op;

import org.xvm.asm.constants.PropertyConstant;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.OpProperty;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * P_SET rvalue-target, CONST_PROPERTY, rvalue
 *
 * @author gg 2017.03.08
 */
public class PSet extends OpProperty
    {
    private final int f_nTarget;
    private final int f_nPropConstId;
    private final int f_nValue;

    public PSet(int nTarget, int nPropId, int nValue)
        {
        f_nTarget = nTarget;
        f_nPropConstId = nPropId;
        f_nValue = nValue;
        }

    public PSet(DataInput in)
            throws IOException
        {
        f_nTarget = in.readInt();
        f_nPropConstId = in.readInt();
        f_nValue = in.readInt();
        }

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.write(OP_P_SET);
        out.writeInt(f_nTarget);
        out.writeInt(f_nPropConstId);
        out.writeInt(f_nValue);
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            ObjectHandle hTarget = frame.getArgument(f_nTarget);
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
            frame.m_hException = e.getExceptionHandle();
            return R_EXCEPTION;
            }
        }
    }
