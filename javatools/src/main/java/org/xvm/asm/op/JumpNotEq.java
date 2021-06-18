package org.xvm.asm.op;


import java.io.DataInput;
import java.io.IOException;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.Op;
import org.xvm.asm.OpCondJump;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;

import org.xvm.runtime.template.xBoolean.BooleanHandle;


/**
 * JMP_NEQ rvalue1, rvalue2, addr ; jump if value1 is NOT equal to value2
 */
public class JumpNotEq
        extends OpCondJump
    {
    /**
     * Construct a JMP_NEQ op.
     *
     * @param arg1  the first argument to compare
     * @param arg2  the second argument to compare
     * @param op    the op to conditionally jump to
     */
    public JumpNotEq(Argument arg1, Argument arg2, Op op)
        {
        super(arg1, arg2, op);
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public JumpNotEq(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);
        }

    @Override
    public int getOpCode()
        {
        return OP_JMP_NEQ;
        }

    @Override
    protected boolean isBinaryOp()
        {
        return true;
        }

    @Override
    protected int completeBinaryOp(Frame frame, int iPC, TypeConstant type,
                                   ObjectHandle hValue1, ObjectHandle hValue2)
        {
        switch (type.callEquals(frame, hValue1, hValue2, A_STACK))
            {
            case R_NEXT:
                {
                BooleanHandle hValue = (BooleanHandle) frame.popStack();
                return hValue.get() ? iPC + 1 : jump(frame, iPC + m_ofJmp, m_cExits);
                }

            case R_CALL:
                frame.m_frameNext.addContinuation(frameCaller ->
                    {
                    BooleanHandle hValue = (BooleanHandle) frameCaller.popStack();
                    return hValue.get() ? iPC + 1 : jump(frameCaller, iPC + m_ofJmp, m_cExits);
                    });
                return R_CALL;

            case R_EXCEPTION:
                return R_EXCEPTION;

            default:
                throw new IllegalStateException();
            }
        }
    }
