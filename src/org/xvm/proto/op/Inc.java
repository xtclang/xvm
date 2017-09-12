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
 * INC rvalue-target ; in-place increment; no result
 *
 * @author gg 2017.03.08
 */
public class Inc extends OpProperty
    {
    private final int f_nArgValue;

    public Inc(int nArg)
        {
        f_nArgValue = nArg;
        }

    public Inc(DataInput in)
            throws IOException
        {
        f_nArgValue = in.readInt();
        }

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.write(OP_INC);
        out.writeInt(f_nArgValue);
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
    }