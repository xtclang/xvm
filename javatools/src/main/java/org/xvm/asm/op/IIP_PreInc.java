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
 * IIP_INCB rvalue-target, rvalue-ix, lvalue ; ++T[ix] -> T
 */
public class IIP_PreInc
        extends OpIndex
    {
    /**
     * Construct an IIP_INCB op for the passed arguments.
     *
     * @param argTarget  the target Argument
     * @param argIndex   the index Argument
     * @param argReturn  the Argument to store the result into
     */
    public IIP_PreInc(Argument argTarget, Argument argIndex, Argument argReturn)
        {
        super(argTarget, argIndex, argReturn);
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public IIP_PreInc(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);
        }

    @Override
    public int getOpCode()
        {
        return OP_IIP_INCB;
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

        return template.invokePreInc(frame, hTarget, lIndex, m_nRetValue);
        }
    }
