package org.xvm.asm.op;


import java.io.DataInput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.OpIndex;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;

import org.xvm.runtime.template.IndexSupport;


/**
 * IIP_INCA rvalue-target, rvalue-ix, lvalue ; T[ix]++ -> T
 */
public class IIP_PostInc
        extends OpIndex
    {
    /**
     * Construct an IIP_INCA op for the passed arguments.
     *
     * @param argTarget  the target Argument
     * @param argIndex   the index Argument
     * @param argReturn  the Argument to store the result into
     */
    public IIP_PostInc(Argument argTarget, Argument argIndex, Argument argReturn)
        {
        super(argTarget, argIndex, argReturn);
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public IIP_PostInc(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);
        }

    @Override
    public int getOpCode()
        {
        return OP_IIP_INCA;
        }

    @Override
    protected int complete(Frame frame, ObjectHandle hTarget, ObjectHandle.JavaLong hIndex)
        {
        IndexSupport template = (IndexSupport) hTarget.getOpSupport();

        return template.invokePostInc(frame, hTarget, hIndex.getValue(), m_nRetValue);
        }
    }
