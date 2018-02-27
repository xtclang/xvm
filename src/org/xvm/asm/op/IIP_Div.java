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
 * IIP_DIV rvalue-target, rvalue-ix, rvalue2 ; T[ix] *= T
 */
public class IIP_Div
        extends OpIndexInPlace
    {
    /**
     * Construct an IIP_DIV op for the passed target.
     *
     * @param argTarget  the target Argument
     * @param argIndex   the index Argument
     * @param argValue   the value Argument
     */
    protected IIP_Div(Argument argTarget, Argument argIndex, Argument argValue)
        {
        super(argTarget, argIndex, argValue);
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public IIP_Div(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);
        }

    @Override
    public int getOpCode()
        {
        return OP_IIP_DIV;
        }

    @Override
    protected int complete(Frame frame, ObjectHandle hTarget, JavaLong hIndex, ObjectHandle hValue)
        {
        IndexSupport template = (IndexSupport) hTarget.getOpSupport();
        long lIndex = hIndex.getValue();

        try
            {
            ObjectHandle hCurrent = template.extractArrayValue(hTarget, lIndex);

            switch (hCurrent.getOpSupport().invokeDiv(frame, hCurrent, hValue, Frame.RET_LOCAL))
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
                        return hException == null ? Op.R_NEXT : frameCaller.raiseException(hException);
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