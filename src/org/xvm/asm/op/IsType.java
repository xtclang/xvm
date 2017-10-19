package org.xvm.asm.op;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.Op;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * IS_TYPE  rvalue, rvalue-type, lvalue-return ; T instanceof Type -> Boolean
 */
public class IsType
        extends Op
    {
    /**
     * Construct an IS_TYPE op.
     *
     * @param nValue  the value to test
     * @param nType   the type to test for
     * @param nRet    the location to store the Boolean result
     */
    public IsType(int nValue, int nType, int nRet)
        {
        m_nValue    = nValue;
        m_nType     = nType;
        m_nRetValue = nRet;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public IsType(DataInput in, Constant[] aconst)
            throws IOException
        {
        m_nValue    = readPackedInt(in);
        m_nType     = readPackedInt(in);
        m_nRetValue = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        out.writeByte(OP_IS_TYPE);
        writePackedLong(out, m_nValue);
        writePackedLong(out, m_nType);
        writePackedLong(out, m_nRetValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_IS_TYPE;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            ObjectHandle hValue1 = frame.getArgument(m_nValue);
            ObjectHandle hValue2 = frame.getArgument(m_nType);
            if (hValue1 == null || hValue2 == null)
                {
                return R_REPEAT;
                }

            // TODO
            return iPC + 1;
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    private int m_nValue;
    private int m_nType;
    private int m_nRetValue;
    }
