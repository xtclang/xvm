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
 * IP_INCA lvalue-target, lvalue ; T++ -> T
 */
public class IP_PostInc
        extends Op
    {
    /**
     * Construct a IP_INCA op.
     *
     * @param nArg  the location to increment
     * @param nRet  the location to store the post-incremented value
     */
    public IP_PostInc(int nArg, int nRet)
        {
        m_nArgValue = nArg;
        m_nRetValue = nRet;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public IP_PostInc(DataInput in, Constant[] aconst)
            throws IOException
        {
        m_nArgValue = readPackedInt(in);
        m_nRetValue = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        out.writeByte(OP_IP_INCA);
        writePackedLong(out, m_nArgValue);
        writePackedLong(out, m_nRetValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_IP_INCA;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            int nArg = m_nArgValue;
            if (nArg >= 0)
                {
                // operation on a register
                ObjectHandle hValue = frame.getArgument(nArg);
                if (hValue == null)
                    {
                    return R_REPEAT;
                    }

                if (hValue.f_clazz.f_template.invokeNext(frame, hValue, Frame.RET_LOCAL) == R_EXCEPTION)
                    {
                    return R_EXCEPTION;
                    }

                ObjectHandle hValueNew = frame.getFrameLocal();
                return m_nRetValue == nArg
                    ? frame.assignValue(nArg, hValueNew)
                    : frame.assignValues(new int[]{m_nRetValue, nArg}, hValue, hValueNew);
                }
            else
                {
                // operation on a local property
                ObjectHandle hTarget = frame.getThis();

                PropertyConstant constProperty = (PropertyConstant)
                        frame.f_context.f_pool.getConstant(-nArg);

                return hTarget.f_clazz.f_template.invokePostInc(
                        frame, hTarget, constProperty.getName(), m_nRetValue);
                }
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    private int m_nArgValue;
    private int m_nRetValue;
    }