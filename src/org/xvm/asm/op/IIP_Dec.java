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
 * IIP_DEC rvalue-target, rvalue-ix ; --T[ix] (no result)
 */
public class IIP_Dec
        extends OpIndex
    {
    /**
     * Construct an IIP_INC op for the passed arguments.
     *
     * @param argTarget  the target Argument
     * @param argIndex   the index Argument
     */
    public IIP_Dec(Argument argTarget, Argument argIndex)
        {
        super(argTarget, argIndex);
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public IIP_Dec(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);
        }

    @Override
    public int getOpCode()
        {
        return OP_IIP_DEC;
        }

    @Override
    protected boolean isAssignOp()
        {
        return false;
        }

    @Override
    protected int complete(Frame frame, ObjectHandle hTarget, JavaLong hIndex)
        {
        IndexSupport template = (IndexSupport) hTarget.getOpSupport();

        return template.invokePreDec(frame, hTarget, hIndex.getValue(), A_IGNORE);
        }
    }
