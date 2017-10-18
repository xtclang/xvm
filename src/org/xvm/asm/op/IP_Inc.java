package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.Op;

import org.xvm.asm.constants.PropertyConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * IP_INC lvalue-target ; in-place increment; no result
 */
public class IP_Inc
        extends Op
    {
    /**
     * Construct an IP_INC op.
     *
     * @param nArg  indicates the incrementable target
     */
    public IP_Inc(int nArg)
        {
        m_nArgValue = nArg;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public IP_Inc(DataInput in, Constant[] aconst)
            throws IOException
        {
        m_nArgValue = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        out.writeByte(OP_IP_INC);
        writePackedLong(out, m_nArgValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_IP_INC;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            if (m_nArgValue >= 0)
                {
                // operation on a register
                ObjectHandle hValue = frame.getArgument(m_nArgValue);
                if (hValue == null)
                    {
                    return R_REPEAT;
                    }

                return hValue.f_clazz.f_template.invokeNext(frame, hValue, m_nArgValue);
                }
            else
                {
                // operation on a local property
                ObjectHandle hTarget = frame.getThis();

                PropertyConstant constProperty = (PropertyConstant)
                        frame.f_context.f_pool.getConstant(-m_nArgValue);

                return hTarget.f_clazz.f_template.invokePostInc(
                        frame, hTarget, constProperty.getName(), Frame.RET_UNUSED);
                }
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    private int m_nArgValue;
    }