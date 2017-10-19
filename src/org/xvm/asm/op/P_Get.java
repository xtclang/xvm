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
 * P_GET PROPERTY, rvalue-target, lvalue
 */
public class P_Get
        extends OpProperty
    {
    /**
     * Construct a P_GET op.
     *
     * @param nPropId  the property to get
     * @param nTarget  the target object
     * @param nRet     the location to store the result
     */
    public P_Get(int nPropId, int nTarget, int nRet)
        {
        super(nPropId);
        m_nTarget      = nTarget;
        m_nRetValue    = nRet;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public P_Get(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(readPackedInt(in));
        m_nTarget      = readPackedInt(in);
        m_nRetValue    = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        out.writeByte(OP_P_GET);
        writePackedLong(out, m_nPropId);
        writePackedLong(out, m_nTarget);
        writePackedLong(out, m_nRetValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_P_GET;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            ObjectHandle hTarget = frame.getArgument(m_nTarget);
            if (hTarget == null)
                {
                return R_REPEAT;
                }

            PropertyConstant constProperty = (PropertyConstant)
                    frame.f_context.f_pool.getConstant(m_nPropId);

            return hTarget.f_clazz.f_template.getPropertyValue(
                    frame, hTarget, constProperty.getName(), m_nRetValue);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    private int m_nTarget;
    private int m_nRetValue;
    }
