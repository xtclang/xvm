package org.xvm.asm.op;


import java.io.DataInput;
import java.io.IOException;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.OpTest;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;


/**
 * IS_EQ rvalue, rvalue, lvalue-return ; T == T -> Boolean
 */
public class IsEq
        extends OpTest
    {
    /**
     * Construct an IS_EQ op based on the specified arguments.
     *
     * @param arg1       the first value Argument
     * @param arg2       the second value Argument
     * @param argReturn  the location to store the Boolean result
     */
    public IsEq(Argument arg1, Argument arg2, Argument argReturn)
        {
        super(arg1, arg2, argReturn);
        }

    /**
     * Construct an IS_EQ op based on the specified arguments and a common type.
     *
     * @param arg1       the first value Argument
     * @param arg2       the second value Argument
     * @param argReturn  the location to store the Boolean result
     * @param type       the common type
     */
    public IsEq(Argument arg1, Argument arg2, Argument argReturn, TypeConstant type)
        {
        super(arg1, arg2, argReturn);

        m_typeCommon = type;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public IsEq(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);
        }

    @Override
    public int getOpCode()
        {
        return OP_IS_EQ;
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
        return type.callEquals(frame, hValue1, hValue2, m_nRetValue);
        }
    }
