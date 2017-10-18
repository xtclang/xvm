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
 * I_REF rvalue-target, rvalue-ix, lvalue ; Ref<T> = &T[ix]
 */
public class I_Ref
        extends Op
    {
    /**
     * Construct an I_REF op.
     *
     * @param nTarget  the target array
     * @param nIndex   the index of the value in the array
     * @param nRet     the location to store the reference to the value in the array
     */
    public I_Ref(int nTarget, int nIndex, int nRet)
        {
        m_nTargetValue = nTarget;
        m_nIndexValue = nIndex;
        m_nRetValue = nRet;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public I_Ref(DataInput in, Constant[] aconst)
            throws IOException
        {
        m_nTargetValue = readPackedInt(in);
        m_nIndexValue  = readPackedInt(in);
        m_nRetValue    = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        out.writeByte(OP_I_REF);
        writePackedLong(out, m_nTargetValue);
        writePackedLong(out, m_nIndexValue);
        writePackedLong(out, m_nRetValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_I_REF;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            ObjectHandle hTarget = frame.getArgument(m_nTargetValue);
            long lIndex = frame.getIndex(m_nIndexValue);

            if (hTarget == null || lIndex == -1)
                {
                return R_REPEAT;
                }

            IndexSupport template = (IndexSupport) hTarget.f_clazz.f_template;

            return template.makeRef(frame, hTarget, lIndex, m_nRetValue);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    private int m_nTargetValue;
    private int m_nIndexValue;
    private int m_nRetValue;
    }
