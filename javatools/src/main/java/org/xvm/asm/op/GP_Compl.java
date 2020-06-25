package org.xvm.asm.op;


import java.io.DataInput;
import java.io.IOException;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.OpGeneral;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;


/**
 * GP_COMPL rvalue, lvalue   ; ~T -> T
 */
public class GP_Compl
        extends OpGeneral
    {
    /**
     * Construct a GP_COMPL op for the passed arguments.
     *
     * @param argValue   the Argument to calculate the complement of
     * @param argResult  the Argument to store the result in
     */
    public GP_Compl(Argument argValue, Argument argResult)
        {
        super(argValue, argResult);
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public GP_Compl(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);
        }

    @Override
    public int getOpCode()
        {
        return OP_GP_COMPL;
        }

    @Override
    protected boolean isBinaryOp()
        {
        return false;
        }

    @Override
    protected int completeUnary(Frame frame, ObjectHandle hTarget)
        {
        return hTarget.getOpSupport().invokeCompl(frame, hTarget, m_nRetValue);
        }
    }
