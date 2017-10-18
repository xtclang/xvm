package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.Op;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;

import org.xvm.runtime.template.xBoolean;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * IS_ZERO rvalue-int, lvalue-return ; T == 0 -> Boolean
 */
public class IsZero
        extends Op
    {
    /**
     * Construct a IS_ZERO op.
     *
     * @param nValue  the value to test
     * @param nRet    the location to store the Boolean result
     */
    public IsZero(int nValue, int nRet)
        {
        m_nValue    = nValue;
        m_nRetValue = nRet;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public IsZero(DataInput in, Constant[] aconst)
            throws IOException
        {
        m_nValue    = readPackedInt(in);
        m_nRetValue = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        out.writeByte(OP_IS_ZERO);
        writePackedLong(out, m_nValue);
        writePackedLong(out, m_nRetValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_IS_ZERO;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            JavaLong hValue = (JavaLong) frame.getArgument(m_nValue);
            if (hValue == null)
                {
                return R_REPEAT;
                }

            return frame.assignValue(m_nRetValue, xBoolean.makeHandle(hValue.getValue() == 0));
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    private int m_nValue;
    private int m_nRetValue;
    }
