package org.xvm.asm.op;


import java.io.DataInput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.OpIndex;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;

import org.xvm.runtime.template.IndexSupport;


/**
 * I_GET rvalue-target, rvalue-ix, lvalue ; T = T[ix]
 */
public class I_Get
        extends OpIndex
    {
    /**
     * Construct an I_GET op.
     *
     * @param nTarget  the target indexed object
     * @param nIndex   the index
     * @param nRet     the location to store the resulting reference
     *
     * @deprecated
     */
    public I_Get(int nTarget, int nIndex, int nRet)
        {
        super(null, null, null);

        m_nTarget = nTarget;
        m_nIndex = nIndex;
        m_nRetValue = nRet;
        }

    /**
     * Construct an I_GET op for the passed arguments.
     *
     * @param argTarget  the target Argument
     * @param argIndex   the index Argument
     * @param argReturn  the Argument to store the result into
     */
    public I_Get(Argument argTarget, Argument argIndex, Argument argReturn)
        {
        super(argTarget, argIndex, argReturn);
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public I_Get(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);
        }

    @Override
    public int getOpCode()
        {
        return OP_I_GET;
        }

    @Override
    protected int complete(Frame frame, ObjectHandle hTarget, JavaLong hIndex)
        {
        IndexSupport template = (IndexSupport) hTarget.getOpSupport();

        try
            {
            return frame.assignValue(m_nRetValue,
                    template.extractArrayValue(hTarget, hIndex.getValue()));
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }
    }
