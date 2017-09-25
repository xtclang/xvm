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
 * POSTINC lvalue-target, lvalue-return ; T++ -> T
 */
public class PostInc
        extends OpProperty
    {
    /**
     * Construct a POST_INC op.
     *
     * @param nArg  the location to increment
     * @param nRet  the location to store the post-incremented value
     */
    public PostInc(int nArg, int nRet)
        {
        f_nArgValue = nArg;
        f_nRetValue = nRet;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public PostInc(DataInput in, Constant[] aconst)
            throws IOException
        {
        f_nArgValue = readPackedInt(in);
        f_nRetValue = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.writeByte(OP_POSTINC);
        writePackedLong(out, f_nArgValue);
        writePackedLong(out, f_nRetValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_POSTINC;
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

                if (hValue.f_clazz.f_template.invokeNext(frame, hValue, Frame.RET_LOCAL) == R_EXCEPTION)
                    {
                    return R_EXCEPTION;
                    }

                ObjectHandle hValueNew = frame.getFrameLocal();
                if (frame.assignValue(f_nRetValue, hValue) == R_EXCEPTION ||
                    frame.assignValue(f_nArgValue, hValueNew) == R_EXCEPTION)
                    {
                    return R_EXCEPTION;
                    }
                return iPC + 1;
                }
            else
                {
                // operation on a local property
                ObjectHandle hTarget = frame.getThis();

                PropertyConstant constProperty = (PropertyConstant)
                        frame.f_context.f_pool.getConstant(-f_nArgValue);

                return hTarget.f_clazz.f_template.invokePostInc(
                        frame, hTarget, constProperty.getName(), f_nRetValue);
                }
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    private final int f_nArgValue;
    private final int f_nRetValue;
    }