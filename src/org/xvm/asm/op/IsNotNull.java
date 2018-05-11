package org.xvm.asm.op;


import java.io.DataInput;
import java.io.IOException;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.OpTest;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xNullable;


/**
 * IS_NNULL rvalue, lvalue-return ; T != null -> Boolean
 */
public class IsNotNull
        extends OpTest
    {
    /**
     * Construct an IS_NNULL op.
     *
     * @param nValue  the Nullable value to test
     * @param nRet    the location to store the Boolean result
     *
     * @deprecated
     */
    public IsNotNull(int nValue, int nRet)
        {
        super((Argument) null, null);

        m_nValue1   = nValue;
        m_nRetValue = nRet;
        }

    /**
     * Construct an IS_NNULL op based on the specified arguments.
     *
     * @param arg        the value Argument
     * @param argReturn  the location to store the Boolean result
     */
     public IsNotNull(Argument arg, Argument argReturn)
        {
        super(arg, argReturn);
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public IsNotNull(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);
        }

    @Override
    public int getOpCode()
        {
        return OP_IS_NNULL;
        }

    @Override
    protected int completeUnaryOp(Frame frame, ObjectHandle hValue)
        {
        return frame.assignValue(m_nRetValue, xBoolean.makeHandle(hValue != xNullable.NULL));
        }
    }
