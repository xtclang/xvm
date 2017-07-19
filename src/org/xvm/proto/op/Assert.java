package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.OpCallable;

import org.xvm.proto.template.xBoolean.BooleanHandle;
import org.xvm.proto.template.xException;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * ASSERT rvalue
 *
 * @author gg 2017.03.08
 */
public class Assert extends OpCallable
    {
    private final int f_nValue;

    public Assert(int nValue)
        {
        f_nValue = nValue;
        }

    public Assert(DataInput in)
            throws IOException
        {
        f_nValue = in.readInt();
        }

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.write(OP_ASSERT);
        out.writeInt(f_nValue);
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        ExceptionHandle hException;
        try
            {
            BooleanHandle hTest = (BooleanHandle) frame.getArgument(f_nValue);
            if (hTest == null)
                {
                return R_REPEAT;
                }

            if (hTest.get())
                {
                return iPC + 1;
                }

            hException = xException.makeHandle("Assertion failed");
            }
        catch (ExceptionHandle.WrapperException e)
            {
            hException = e.getExceptionHandle();
            }

        frame.m_hException = hException;
        return R_EXCEPTION;
        }
    }
