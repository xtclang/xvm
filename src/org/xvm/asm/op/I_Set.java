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
 * I_SET rvalue-target, rvalue-ix, rvalue ; T[ix] = T
 */
public class I_Set
        extends Op
    {
    /**
     * Construct an I_SET op.
     *
     * @param nTarget  the target indexed object
     * @param nIndex   the index
     * @param nValue   the value to store
     */
    public I_Set(int nTarget, int nIndex, int nValue)
        {
        m_nTargetValue = nTarget;
        m_nIndexValue  = nIndex;
        m_nValue       = nValue;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public I_Set(DataInput in, Constant[] aconst)
            throws IOException
        {
        m_nTargetValue = readPackedInt(in);
        m_nIndexValue  = readPackedInt(in);
        m_nValue       = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
    throws IOException
        {
        out.writeByte(OP_I_SET);
        writePackedLong(out, m_nTargetValue);
        writePackedLong(out, m_nIndexValue);
        writePackedLong(out, m_nValue);
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
            ObjectHandle hTarget = frame.getArgument(m_nTargetValue);
            long         lIndex  = frame.getIndex(m_nIndexValue);
            ObjectHandle hArg    = frame.getArgument(m_nValue);
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

    private int m_nTargetValue;
    private int m_nIndexValue;
    private int m_nValue;
    }