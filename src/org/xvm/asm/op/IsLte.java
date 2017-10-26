package org.xvm.asm.op;


import java.io.DataInput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.OpTest;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xOrdered;


/**
 * IS_LTE rvalue, rvalue, lvalue-return ; T <= T -> Boolean
 */
public class IsLte
        extends OpTest
    {
    /**
     * Construct an IS_LTE op.
     *
     * @param nValue1  the first value to compare
     * @param nValue2  the second value to compare
     * @param nRet     the location to store the Boolean result
     *
     * @deprecated
     */
    public IsLte(int nValue1, int nValue2, int nRet)
        {
        super(null, null, null);

        m_nValue1   = nValue1;
        m_nValue2   = nValue2;
        m_nRetValue = nRet;
        }

    /**
     * Construct an IS_LTE op based on the specified arguments.
     *
     * @param arg1       the first value Argument
     * @param arg2       the second value Argument
     * @param argReturn  the location to store the Boolean result
     */
    public IsLte(Argument arg1, Argument arg2, Argument argReturn)
        {
        super(arg1, arg2, argReturn);
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public IsLte(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);
        }

    @Override
    public int getOpCode()
        {
        return OP_IS_LTE;
        }

    @Override
    protected boolean isBinaryOp()
        {
        return true;
        }

    @Override
    protected int completeBinaryOp(Frame frame, TypeComposition clz,
                                   ObjectHandle hValue1, ObjectHandle hValue2)
        {
        switch (clz.callCompare(frame, hValue1, hValue2, Frame.RET_LOCAL))
            {
            case R_NEXT:
                return frame.assignValue(m_nRetValue, xBoolean.makeHandle(
                        frame.getFrameLocal() != xOrdered.LESSER));

            case R_CALL:
                frame.m_frameNext.setContinuation(frameCaller ->
                    frameCaller.assignValue(m_nRetValue, xBoolean.makeHandle(
                            frameCaller.getFrameLocal() != xOrdered.GREATER)));
                return R_CALL;

            case R_EXCEPTION:
                return R_EXCEPTION;

            default:
                throw new IllegalStateException();
            }
        }
    }
