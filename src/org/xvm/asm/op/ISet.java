package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.Op;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;

import org.xvm.runtime.template.IndexSupport;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * A_SET rvalue-target, rvalue-index, rvalue-new-value ; T[Ti] = T
 */
public class ISet
        extends Op
    {
    /**
     * Construct an I_SET op.
     *
     * @param nTarget  the target indexed object
     * @param nIndex   the index
     * @param nValue   the value to store
     */
    public ISet(int nTarget, int nIndex, int nValue)
        {
        f_nTargetValue = nTarget;
        f_nIndexValue  = nIndex;
        f_nValue       = nValue;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public ISet(DataInput in, Constant[] aconst)
            throws IOException
        {
        f_nTargetValue = readPackedInt(in);
        f_nIndexValue  = readPackedInt(in);
        f_nValue       = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
    throws IOException
        {
        out.writeByte(OP_I_SET);
        writePackedLong(out, f_nTargetValue);
        writePackedLong(out, f_nIndexValue);
        writePackedLong(out, f_nValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_I_SET;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        ExceptionHandle hException;

        try
            {
            ObjectHandle hTarget = frame.getArgument(f_nTargetValue);
            long         lIndex  = frame.getIndex(f_nIndexValue);
            ObjectHandle hArg    = frame.getArgument(f_nValue);
            if (hTarget == null || hArg == null || lIndex == -1)
                {
                return R_REPEAT;
                }

            IndexSupport template = (IndexSupport) hTarget.f_clazz.f_template;

            hException = template.assignArrayValue(hTarget, lIndex, hArg);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            hException = e.getExceptionHandle();
            }

        return hException == null ? iPC + 1 : frame.raiseException(hException);
        }

    private final int f_nTargetValue;
    private final int f_nIndexValue;
    private final int f_nValue;
    }