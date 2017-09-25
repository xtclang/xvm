package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.OpProperty;

import org.xvm.asm.constants.PropertyConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * INC rvalue-target ; in-place increment; no result
 */
public class Inc
        extends OpProperty
    {
    /**
     * Construct an INC op.
     *
     * @param nArg  indicates the incrementable object to increment
     */
    public Inc(int nArg)
        {
        f_nArgValue = nArg;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public Inc(DataInput in, Constant[] aconst)
            throws IOException
        {
        f_nArgValue = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out)
    throws IOException
        {
        out.writeByte(OP_INC);
        writePackedLong(out, f_nArgValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_INC;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            if (f_nArgValue >= 0)
                {
                // operation on a register
                ObjectHandle hValue = frame.getArgument(f_nArgValue);
                if (hValue == null)
                    {
                    return R_REPEAT;
                    }

                return hValue.f_clazz.f_template.invokeNext(frame, hValue, f_nArgValue);
                }
            else
                {
                // operation on a local property
                ObjectHandle hTarget = frame.getThis();

                PropertyConstant constProperty = (PropertyConstant)
                        frame.f_context.f_pool.getConstant(-f_nArgValue);

                return hTarget.f_clazz.f_template.invokePostInc(
                        frame, hTarget, constProperty.getName(), Frame.RET_UNUSED);
                }
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    private final int f_nArgValue;
    }