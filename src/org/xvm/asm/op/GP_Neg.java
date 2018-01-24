package org.xvm.asm.op;


import java.io.DataInput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.OpGeneral;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;


/**
 * GP_NEG rvalue, lvalue   ; -T -> T
 */
public class GP_Neg
        extends OpGeneral
    {
    /**
     * Construct a GP_NEG op.
     *
     * @param nArg  the r-value target to negate
     * @param nRet  the l-value to store the result in
     *
     * @deprecated
     */
    public GP_Neg(int nArg, int nRet)
        {
        super((Argument) null, null);

        m_nArgValue = nArg;
        m_nRetValue = nRet;
        }

    /**
     * Construct a GP_NEG op for the passed arguments.
     *
     * @param argValue  the Argument to negate
     * @param argResult  the Argument to store the result in
     */
    public GP_Neg(Argument argValue, Argument argResult)
        {
        super(argValue, argResult);
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public GP_Neg(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);
        }

    @Override
    public int getOpCode()
        {
        return OP_GP_NEG;
        }

    @Override
    protected boolean isBinaryOp()
        {
        return false;
        }

    @Override
    protected int completeUnary(Frame frame, ObjectHandle hTarget)
        {
        return hTarget.getOpSupport().invokeNeg(frame, hTarget, m_nRetValue);
        }
    }
