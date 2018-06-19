package org.xvm.asm.op;


import java.io.DataInput;
import java.io.IOException;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.OpTest;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xOrdered;


/**
 * IS_GT rvalue, rvalue, lvalue-return ; T > T -> Boolean
 */
public class IsGt
        extends OpTest
    {
    /**
     * Construct an IS_GT op.
     *
     * @param nValue1  the first value to compare
     * @param nValue2  the second value to compare
     * @param nRet     the location to store the Boolean result
     *
     * @deprecated
     */
    public IsGt(int nValue1, int nValue2, int nRet)
        {
        super(null, null, null);

        m_nValue1   = nValue1;
        m_nValue2   = nValue2;
        m_nRetValue = nRet;
        }

    /**
     * Construct an IS_GT op based on the specified arguments.
     *
     * @param arg1       the first value Argument
     * @param arg2       the second value Argument
     * @param argReturn  the location to store the Boolean result
     */
    public IsGt(Argument arg1, Argument arg2, Argument argReturn)
        {
        super(arg1, arg2, argReturn);
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
        super(in, aconst);
        }

    @Override
    public int getOpCode()
        {
        return OP_IS_GT;
        }

    @Override
    protected boolean isBinaryOp()
        {
        return true;
        }

    @Override
    protected int completeBinaryOp(Frame frame, TypeConstant type,
                                   ObjectHandle hValue1, ObjectHandle hValue2)
        {
        switch (type.callCompare(frame, hValue1, hValue2, A_STACK))
            {
            case R_NEXT:
                return frame.assignValue(m_nRetValue, xBoolean.makeHandle(
                        frame.popStack() == xOrdered.GREATER));

            case R_CALL:
                frame.m_frameNext.setContinuation(frameCaller ->
                    frameCaller.assignValue(m_nRetValue, xBoolean.makeHandle(
                            frameCaller.popStack() == xOrdered.GREATER)));
                return R_CALL;

            case R_EXCEPTION:
                return R_EXCEPTION;

            default:
                throw new IllegalStateException();
            }
        }
    }
