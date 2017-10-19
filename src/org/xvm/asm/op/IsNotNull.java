package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.Op;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xNullable;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * IS_NNULL rvalue, lvalue-return ; T != null -> Boolean
 */
public class IsNotNull
        extends Op
    {
    /**
     * Construct an IS_NNULL op.
     *
     * @param nValue  the Nullable value to test
     * @param nRet    the location to store the Boolean result
     */
    public IsNotNull(int nValue, int nRet)
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
    public IsNotNull(DataInput in, Constant[] aconst)
            throws IOException
        {
        m_nValue    = readPackedInt(in);
        m_nRetValue = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        out.writeByte(OP_IS_NNULL);
        writePackedLong(out, m_nValue);
        writePackedLong(out, m_nRetValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_IS_NNULL;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            ObjectHandle hValue = frame.getArgument(m_nValue);
            if (hValue == null)
                {
                return R_REPEAT;
                }

            return frame.assignValue(m_nRetValue, xBoolean.makeHandle(hValue != xNullable.NULL));
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    private int m_nValue;
    private int m_nRetValue;
    }
