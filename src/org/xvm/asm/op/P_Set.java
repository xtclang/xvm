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
 * P_SET PROPERTY, rvalue-target, rvalue
 */
public class P_Set
        extends OpProperty
    {
    /**
     * Construct a P_SET op.
     *
     * @param nPropId  the property to set
     * @param nTarget  the target object
     * @param nValue   the value to store in the property
     */
    public P_Set(int nPropId, int nTarget, int nValue)
        {
        super(nPropId);
        m_nTarget      = nTarget;
        m_nValue       = nValue;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public P_Set(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(readPackedInt(in));
        m_nTarget      = readPackedInt(in);
        m_nValue       = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        out.writeByte(OP_P_SET);
        writePackedLong(out, m_nPropId);
        writePackedLong(out, m_nTarget);
        writePackedLong(out, m_nValue);
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
            ObjectHandle hTarget = frame.getArgument(m_nTarget);
            ObjectHandle hValue = frame.getArgument(m_nValue);
            if (hTarget == null || hValue == null)
                {
                return R_REPEAT;
                }

            PropertyConstant constProperty = (PropertyConstant)
                    frame.f_context.f_pool.getConstant(m_nPropId);

            return hTarget.f_clazz.f_template.setPropertyValue(
                    frame, hTarget, constProperty.getName(), hValue);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    private int m_nTarget;
    private int m_nValue;
    }
