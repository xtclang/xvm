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
 * IS_GTE rvalue, rvalue, lvalue-return ; T >= T -> Boolean
 */
public class IsGte
        extends OpTest
    {
    /**
     * Construct an IS_GTE op based on the specified arguments.
     *
     * @param arg1       the first value Argument
     * @param arg2       the second value Argument
     * @param argReturn  the location to store the Boolean result
     */
    public IsGte(Argument arg1, Argument arg2, Argument argReturn)
        {
        super(arg1, arg2, argReturn);
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public IsGte(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);
        }

    @Override
    public int getOpCode()
        {
        return OP_IS_GTE;
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
                        frame.popStack() != xOrdered.LESSER));

            case R_CALL:
                frame.m_frameNext.addContinuation(frameCaller ->
                    frameCaller.assignValue(m_nRetValue, xBoolean.makeHandle(
                            frameCaller.popStack() != xOrdered.LESSER)));
                return R_CALL;

            case R_EXCEPTION:
                return R_EXCEPTION;

            default:
                throw new IllegalStateException();
            }
        }
    }
