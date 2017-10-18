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

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xOrdered;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * IS_LT rvalue, rvalue, lvalue-return ; T < T -> Boolean
 */
public class IsLt
        extends Op
    {
    /**
     * Construct an IS_LT op.
     *
     * @param nValue1  the first value to compare
     * @param nValue2  the second value to compare
     * @param nRet     the location to store the Boolean result
     */
    public IsLt(int nValue1, int nValue2, int nRet)
        {
        m_nValue1   = nValue1;
        m_nValue2   = nValue2;
        m_nRetValue = nRet;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public IsLt(DataInput in, Constant[] aconst)
            throws IOException
        {
        m_nValue1   = readPackedInt(in);
        m_nValue2   = readPackedInt(in);
        m_nRetValue = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        out.writeByte(OP_IS_LT);
        writePackedLong(out, m_nValue1);
        writePackedLong(out, m_nValue2);
        writePackedLong(out, m_nRetValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_IS_LT;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            ObjectHandle hValue1 = frame.getArgument(m_nValue1);
            ObjectHandle hValue2 = frame.getArgument(m_nValue2);
            if (hValue1 == null || hValue2 == null)
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

            switch (clz1.callCompare(frame, hValue1, hValue2, Frame.RET_LOCAL))
                {
                case R_EXCEPTION:
                    return R_EXCEPTION;

                case R_NEXT:
                    return frame.assignValue(m_nRetValue, xBoolean.makeHandle(
                            frame.getFrameLocal() != xOrdered.LESSER));

                case R_CALL:
                    frame.m_frameNext.setContinuation(frameCaller ->
                        frame.assignValue(m_nRetValue, xBoolean.makeHandle(
                                frameCaller.getFrameLocal() == xOrdered.LESSER)));
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
    private int m_nRetValue;
    }
