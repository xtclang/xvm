package org.xvm.asm.op;


import java.io.DataInput;
import java.io.IOException;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.OpTest;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;

import org.xvm.runtime.template.xBoolean;


/**
 * IS_NZERO rvalue-int, lvalue-return ; T != 0 -> Boolean
 */
public class IsNotZero
        extends OpTest
    {
    /**
     * Construct an IS_NZERO op.
     *
     * @param nValue  the value to test
     * @param nRet    the location to store the Boolean result
     *
     * @deprecated
     */
    public IsNotZero(int nValue, int nRet)
        {
        super((Argument) null, null);

        m_nValue1   = nValue;
        m_nRetValue = nRet;
        }

    /**
     * Construct an IS_NZERO op based on the specified arguments.
     *
     * @param arg        the value Argument
     * @param argReturn  the location to store the Boolean result
     */
     public IsNotZero(Argument arg, Argument argReturn)
        {
        super(arg, argReturn);
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public IsNotZero(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);
        }

    @Override
    public int getOpCode()
        {
        return OP_IS_NZERO;
        }

    @Override
    protected int completeUnaryOp(Frame frame, ObjectHandle hValue)
        {
        return frame.assignValue(m_nRetValue,
            xBoolean.makeHandle(((JavaLong) hValue).getValue() != 0));
        }
    }
