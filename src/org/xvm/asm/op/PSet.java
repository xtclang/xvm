package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.OpProperty;

import org.xvm.asm.constants.PropertyConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * P_SET rvalue-target, CONST_PROPERTY, rvalue
 */
public class PSet
        extends OpProperty
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

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public PSet(DataInput in, Constant[] aconst)
            throws IOException
        {
        f_nTarget = readPackedInt(in);
        f_nPropConstId = readPackedInt(in);
        f_nValue = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.write(OP_P_SET);
        writePackedLong(out, f_nTarget);
        writePackedLong(out, f_nPropConstId);
        writePackedLong(out, f_nValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_P_SET;
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
            return frame.raiseException(e);
            }
        }
    }
