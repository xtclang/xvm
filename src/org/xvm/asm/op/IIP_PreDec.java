package org.xvm.asm.op;


import java.io.DataInput;
import java.io.IOException;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.OpIndex;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;

import org.xvm.runtime.template.IndexSupport;


/**
 * IIP_DECB rvalue-target, rvalue-ix, lvalue ; --T[ix] -> T
 */
public class IIP_PreDec
        extends OpIndex
    {
    /**
     * Construct an IIP_DECB op for the passed arguments.
     *
     * @param argTarget  the target Argument
     * @param argIndex   the index Argument
     * @param argReturn  the Argument to store the result into
     */
    public IIP_PreDec(Argument argTarget, Argument argIndex, Argument argReturn)
        {
        super(argTarget, argIndex, argReturn);
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public IIP_PreDec(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);
        }

    @Override
    public int getOpCode()
        {
        return OP_IIP_DECB;
        }

    @Override
    protected int complete(Frame frame, ObjectHandle hTarget, ObjectHandle hIndex)
        {
        IndexSupport template = (IndexSupport) hTarget.getOpSupport();
        long         lIndex   = ((JavaLong) hIndex).getValue();

        if (frame.isNextRegister(m_nRetValue))
            {
            frame.introduceElementVar(m_nTarget, (int) lIndex);
            }

        return template.invokePreDec(frame, hTarget, lIndex, m_nRetValue);
        }
    }
