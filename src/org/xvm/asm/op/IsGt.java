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
 * IS_GT rvalue, rvalue, lvalue-return ; T > T -> Boolean
 */
public class IsGt
        extends Op
    {
    /**
     * Construct an IS_GT op.
     *
     * @param nValue1  the first value to compare
     * @param nValue2  the second value to compare
     * @param nRet     the location to store the Boolean result
     */
    public IsGt(int nValue1, int nValue2, int nRet)
        {
        f_nValue1   = nValue1;
        f_nValue2   = nValue2;
        f_nRetValue = nRet;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public IsGt(DataInput in, Constant[] aconst)
            throws IOException
        {
        f_nValue1   = readPackedInt(in);
        f_nValue2   = readPackedInt(in);
        f_nRetValue = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out)
    throws IOException
        {
        out.writeByte(OP_IS_GT);
        writePackedLong(out, f_nValue1);
        writePackedLong(out, f_nValue2);
        writePackedLong(out, f_nRetValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_IS_GT;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            ObjectHandle hValue1 = frame.getArgument(f_nValue1);
            ObjectHandle hValue2 = frame.getArgument(f_nValue2);
            if (hValue1 == null || hValue2 == null)
                {
                return R_REPEAT;
                }

            TypeComposition clz1 = frame.getArgumentClass(f_nValue1);
            TypeComposition clz2 = frame.getArgumentClass(f_nValue2);
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
                    return frame.assignValue(f_nRetValue, xBoolean.makeHandle(
                            frame.getFrameLocal() == xOrdered.GREATER));

                case R_CALL:
                    frame.m_frameNext.setContinuation(frameCaller ->
                        frame.assignValue(f_nRetValue, xBoolean.makeHandle(
                                frameCaller.getFrameLocal() == xOrdered.GREATER)));
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

    private final int f_nValue1;
    private final int f_nValue2;
    private final int f_nRetValue;
    }
