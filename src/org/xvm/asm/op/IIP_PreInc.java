package org.xvm.asm.op;


import java.io.DataInput;
import java.io.IOException;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.OpIndex;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;

import org.xvm.runtime.template.IndexSupport;


/**
 * IIP_INCB rvalue-target, rvalue-ix, lvalue ; ++T[ix] -> T
 */
public class IIP_PreInc
        extends OpIndex
    {
    /**
     * Construct an IIP_INCB op.
     *
     * @param nTarget  the target array
     * @param nIndex   the index of the value to increment
     * @param nRet     the location to store the pre-incremented value
     *
     * @deprecated
     */
    public IIP_PreInc(int nTarget, int nIndex, int nRet)
        {
        super(null, null, null);

        m_nTarget = nTarget;
        m_nIndex = nIndex;
        m_nRetValue = nRet;
        }

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
    protected int complete(Frame frame, ObjectHandle hTarget, ObjectHandle.JavaLong hIndex)
        {
        IndexSupport template = (IndexSupport) hTarget.getOpSupport();

        return template.invokePreInc(frame, hTarget, hIndex.getValue(), m_nRetValue);
        }
    }
