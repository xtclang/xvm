package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.Op;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * JMP_NZERO rvalue, addr ; jump if value is NOT zero
 */
public class JumpNotZero
        extends Op
    {
    /**
     * Construct a JMP_NZERO op.
     *
     * @param nValue    the value to test
     * @param nRelAddr  the relative address to jump to
     */
    public JumpNotZero(int nValue, int nRelAddr)
        {
        m_nValue   = nValue;
        m_nRelAddr = nRelAddr;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public JumpNotZero(DataInput in, Constant[] aconst)
            throws IOException
        {
        m_nValue   = readPackedInt(in);
        m_nRelAddr = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        out.writeByte(OP_JMP_NZERO);
        writePackedLong(out, m_nValue);
        writePackedLong(out, m_nRelAddr);
        }

    @Override
    public int getOpCode()
        {
        return OP_JMP_NZERO;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            JavaLong hTest = (JavaLong) frame.getArgument(m_nValue);
            if (hTest == null)
                {
                return R_REPEAT;
                }

            return hTest.getValue() == 0 ? iPC + 1 : iPC + m_nRelAddr;
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    private int m_nValue;
    private int m_nRelAddr;
    }
