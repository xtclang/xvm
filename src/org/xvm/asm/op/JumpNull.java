package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.Op;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;

import org.xvm.runtime.template.xNullable;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * JMP_NULL rvalue, addr ; jump if value is null
 */
public class JumpNull
        extends Op
    {
    /**
     * Construct a JMP_NULL op.
     *
     * @param nValue    the Nullable value to test
     * @param nRelAddr  the relative address to jump to
     */
    public JumpNull(int nValue, int nRelAddr)
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
    public JumpNull(DataInput in, Constant[] aconst)
            throws IOException
        {
        m_nValue   = readPackedInt(in);
        m_nRelAddr = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        out.writeByte(OP_JMP_NULL);
        writePackedLong(out, m_nValue);
        writePackedLong(out, m_nRelAddr);
        }

    @Override
    public int getOpCode()
        {
        return OP_JMP_NULL;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            ObjectHandle hTest = frame.getArgument(m_nValue);

            return hTest == xNullable.NULL ? iPC + m_nRelAddr : iPC + 1;
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    private int m_nValue;
    private int m_nRelAddr;
    }
