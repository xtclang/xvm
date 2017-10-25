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
public class IIP_Add
        extends OpIndexInPlace
    {
    /**
     * Construct an IIP_ADD op.
     *
     * @param nTarget  the target indexed object
     * @param nIndex   the index
     * @param nValue   the value to store
     *
     * @deprecated
     */
    public IIP_Add(int nTarget, int nIndex, int nValue)
        {
        super(null, null, null);

        m_nTarget = nTarget;
        m_nIndex  = nIndex;
        m_nValue  = nValue;
        }

    /**
     * Construct an IIP_ADD op for the passed target.
     *
     * @param argTarget  the target Argument
     * @param argIndex   the index Argument
     * @param argValue   the value Argument
     */
    protected IIP_Add(Argument argTarget, Argument argIndex, Argument argValue)
        {
        super(argTarget, argIndex, argValue);
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public IIP_Add(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);
        }

    @Override
    public int getOpCode()
        {
        return OP_IIP_ADD;
        }

    @Override
    protected int complete(Frame frame, ObjectHandle hTarget, JavaLong hIndex, ObjectHandle hValue)
        {
        IndexSupport template = (IndexSupport) hTarget.f_clazz.f_template;
        long lIndex = hIndex.getValue();

        try
            {
            ObjectHandle hCurrent = template.extractArrayValue(hTarget, lIndex);

            switch (hCurrent.f_clazz.f_template.invokeAdd(frame, hCurrent, hValue, Frame.RET_LOCAL))
                {
                case R_NEXT:
                    {
                    ExceptionHandle hException = template.assignArrayValue(hTarget, lIndex, hValue);
                    return hException == null ? Op.R_NEXT : frame.raiseException(hException);
                    }

                case R_CALL:
                    frame.m_frameNext.setContinuation(frameCaller ->
                        {
                        ExceptionHandle hException = template.assignArrayValue(hTarget, lIndex, hValue);
                        return hException == null ? Op.R_NEXT : frame.raiseException(hException);
                        });
                    return R_CALL;

                case R_EXCEPTION:
                    return R_EXCEPTION;

                default:
                    throw new IllegalStateException();
                }
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }
    }