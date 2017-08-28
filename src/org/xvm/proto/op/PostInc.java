package org.xvm.proto.op;

import org.xvm.asm.constants.PropertyConstant;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.OpProperty;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * POSTINC lvalue-target, lvalue-return ; T++ -> T
 *
 * @author gg 2017.03.08
 */
public class PostInc extends OpProperty
    {
    private final int f_nArgValue;
    private final int f_nRetValue;

    public PostInc(int nArg, int nRet)
        {
        f_nArgValue = nArg;
        f_nRetValue = nRet;
        }

    public PostInc(DataInput in)
            throws IOException
        {
        f_nArgValue = in.readInt();
        f_nRetValue = in.readInt();
        }

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.write(OP_POSTINC);
        out.writeInt(f_nArgValue);
        out.writeInt(f_nRetValue);
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

                if (hValue.f_clazz.f_template.
                        invokePreInc(frame, hValue, null, Frame.RET_LOCAL) == R_EXCEPTION)
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
            frame.m_hException = e.getExceptionHandle();
            return R_EXCEPTION;
            }
        }
    }