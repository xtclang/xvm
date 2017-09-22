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
 * LSET CONST_PROPERTY, rvalue ; local set (target=this)
 */
public class LSet
        extends OpProperty
    {
    private final int f_nPropConstId;
    private final int f_nValue;

    public LSet(int nPropId, int nValue)
        {
        f_nPropConstId = nPropId;
        f_nValue = nValue;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public LSet(DataInput in, Constant[] aconst)
            throws IOException
        {
        f_nPropConstId = readPackedInt(in);
        f_nValue = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.write(OP_L_SET);
        writePackedLong(out, f_nPropConstId);
        writePackedLong(out, f_nValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_L_SET;
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
