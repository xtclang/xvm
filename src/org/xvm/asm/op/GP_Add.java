package org.xvm.asm.op;


import java.io.DataInput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.OpGeneral;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;


/**
 * GP_ADD rvalue1, rvalue2, lvalue ; T + T -> T
 */
public class GP_Add
        extends OpGeneral
    {
    /**
     * Construct a GP_ADD op.
     *
     * @param nTarget  the first r-value, which will implement the add
     * @param nArg     the second r-value
     * @param nRet     the l-value to store the result into
     *
     * @deprecated
     */
    public GP_Add(int nTarget, int nArg, int nRet)
        {
        super(null, null, null);

        m_nTarget   = nTarget;
        m_nArgValue = nArg;
        m_nRetValue = nRet;
        }

    /**
     * Construct a GP_ADD op for the passed arguments.
     *
     * @param argTarget  the target Argument
     * @param argValue   the second value Argument
     * @param argReturn  the Argument to store the result into
     */
    public GP_Add(Argument argTarget, Argument argValue, Argument argReturn)
        {
        super(argTarget, argValue, argReturn);
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public GP_Add(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);
        }

    @Override
    public int getOpCode()
        {
        return OP_GP_ADD;
        }

    protected int completeBinary(Frame frame, ObjectHandle hTarget, ObjectHandle hArg)
        {
        return hTarget.f_clazz.f_template.invokeAdd(frame, hTarget, hArg, m_nRetValue);
        }
    }
