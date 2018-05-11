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
 * I_VAR rvalue-target, rvalue-ix, lvalue ; Var<T> = &T[ix]
 */
public class I_Var
        extends OpIndex
    {
    /**
     * Construct an I_VAR op.
     *
     * @param nTarget  the target array
     * @param nIndex   the index of the value in the array
     * @param nRet     the location to store the reference to the value in the array
     *
     * @deprecated
     */
    public I_Var(int nTarget, int nIndex, int nRet)
        {
        super(null, null, null);

        m_nTarget = nTarget;
        m_nIndex = nIndex;
        m_nRetValue = nRet;
        }

    /**
     * Construct an I_VAR op for the passed arguments.
     *
     * @param argTarget  the target Argument
     * @param argIndex   the index Argument
     * @param argReturn  the Argument to store the result into
     */
    public I_Var(Argument argTarget, Argument argIndex, Argument argReturn)
        {
        super(argTarget, argIndex, argReturn);
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public I_Var(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);
        }

    @Override
    public int getOpCode()
        {
        return OP_I_REF;
        }

    @Override
    protected void introduceAssignVar(Frame frame, int nIndex)
        {
        frame.introduceElementRef(m_nTarget, nIndex);
        }

    @Override
    protected int complete(Frame frame, ObjectHandle hTarget, JavaLong hIndex)
        {
        IndexSupport template = (IndexSupport) hTarget.getOpSupport();

        return template.makeRef(frame, hTarget, hIndex.getValue(), false, m_nRetValue);
        }
    }
