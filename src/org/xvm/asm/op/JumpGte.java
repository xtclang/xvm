package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.Op;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.xOrdered;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * JMP_GTE rvalue1, rvalue2, addr ; jump if value is greater than or equal than value2
 */
public class JumpGte
        extends Op
    {
    /**
     * Construct a JMP_GTE op.
     *
     * @param nValue1   the first value to compare
     * @param nValue2   the second value to compare
     * @param nRelAddr  the relative address to jump to
     */
    public JumpGte(int nValue1, int nValue2, int nRelAddr)
        {
        m_nValue1  = nValue1;
        m_nValue2  = nValue2;
        m_nRelAddr = nRelAddr;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public JumpGte(DataInput in, Constant[] aconst)
            throws IOException
        {
        m_nValue1  = readPackedInt(in);
        m_nValue2  = readPackedInt(in);
        m_nRelAddr = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        out.writeByte(OP_JMP_GTE);
        writePackedLong(out, m_nValue1);
        writePackedLong(out, m_nValue2);
        writePackedLong(out, m_nRelAddr);
        }

    @Override
    public int getOpCode()
        {
        return OP_JMP_GTE;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            ObjectHandle hTest1 = frame.getArgument(m_nValue1);
            ObjectHandle hTest2 = frame.getArgument(m_nValue2);
            if (hTest1 == null || hTest2 == null)
                {
                return R_REPEAT;
                }

            TypeComposition clz1 = frame.getArgumentClass(m_nValue1);
            TypeComposition clz2 = frame.getArgumentClass(m_nValue2);
            if (clz1 != clz2)
                {
                // this shouldn't have compiled
                throw new IllegalStateException();
                }

            switch (clz1.callCompare(frame, hTest1, hTest2, Frame.RET_LOCAL))
                {
                case R_EXCEPTION:
                    return R_EXCEPTION;

                case R_NEXT:
                    return frame.getFrameLocal() == xOrdered.LESSER ?
                            iPC + 1 : iPC + m_nRelAddr;

                case R_CALL:
                    frame.m_frameNext.setContinuation(frameCaller ->
                        frame.getFrameLocal() == xOrdered.LESSER ?
                            iPC + 1 : iPC + m_nRelAddr);
                    return R_CALL;

                default:
                    throw new IllegalStateException();
                }
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    private int m_nValue1;
    private int m_nValue2;
    private int m_nRelAddr;
    }
