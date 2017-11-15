package org.xvm.asm.op;


import java.io.DataInput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.Op;
import org.xvm.asm.OpIndexInPlace;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;

import org.xvm.runtime.template.IndexSupport;


/**
 * I_SET rvalue-target, rvalue-ix, rvalue ; T[ix] = T
 */
public class I_Set
        extends OpIndexInPlace
    {
    /**
     * Construct an I_SET op.
     *
     * @param nTarget  the target indexed object
     * @param nIndex   the index
     * @param nValue   the value to store
     *
     * @deprecated
     */
    public I_Set(int nTarget, int nIndex, int nValue)
        {
        super(null, null, null);

        m_nTarget = nTarget;
        m_nIndex  = nIndex;
        m_nValue  = nValue;
        }

    /**
     * Construct an I_SET op for the passed target.
     *
     * @param argTarget  the target Argument
     * @param argIndex   the index Argument
     * @param argValue   the value Argument
     */
    public I_Set(Argument argTarget, Argument argIndex, Argument argValue)
        {
        super(argTarget, argIndex, argValue);
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
        super(in, aconst);
        }

    @Override
    public int getOpCode()
        {
        return OP_I_SET;
        }

    @Override
    protected int complete(Frame frame, ObjectHandle hTarget, JavaLong hIndex, ObjectHandle hValue)
        {
        IndexSupport template = (IndexSupport) hTarget.f_clazz.f_template;

        ExceptionHandle hException = template.assignArrayValue(hTarget, hIndex.getValue(), hValue);

        return hException == null ? Op.R_NEXT : frame.raiseException(hException);
        }
    }