package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.Op;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xBoolean.BooleanHandle;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * IS_NOT rvalue, lvalue-return ; !T -> Boolean
 */
public class IsNot
        extends Op
    {
    /**
     * Construct an IS_NOT op.
     *
     * @param nValue  the input Boolean value
     * @param nRet    the location to store the Boolean result
     */
    public IsNot(int nValue, int nRet)
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
    public IsNot(DataInput in, Constant[] aconst)
            throws IOException
        {
        m_nValue    = readPackedInt(in);
        m_nRetValue = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        out.writeByte(OP_IS_NOT);
        writePackedLong(out, m_nValue);
        writePackedLong(out, m_nRetValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_IS_NOT;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            BooleanHandle hValue = (BooleanHandle) frame.getArgument(m_nValue);
            if (hValue == null)
                {
                return R_REPEAT;
                }

            frame.assignValue(m_nRetValue, xBoolean.not(hValue));

            return iPC + 1;
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    private int m_nValue;
    private int m_nRetValue;
    }
