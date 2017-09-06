package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.Op;

import org.xvm.proto.ObjectHandle.JavaLong;
import org.xvm.proto.ObjectHandle.ExceptionHandle;

import org.xvm.proto.template.xBoolean;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * IS_ZERO rvalue-int, lvalue-return ; T == 0 -> Boolean
 *
 * @author gg 2017.03.08
 */
public class IsZero extends Op
    {
    private final int f_nValue;
    private final int f_nRetValue;

    public IsZero(int nValue, int nRet)
        {
        f_nValue = nValue;
        f_nRetValue = nRet;
        }

    public IsZero(DataInput in)
            throws IOException
        {
        f_nValue = in.readInt();
        f_nRetValue = in.readInt();
        }

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.write(OP_IS_ZERO);
        out.writeInt(f_nValue);
        out.writeInt(f_nRetValue);
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            JavaLong hValue = (JavaLong) frame.getArgument(f_nValue);

            if (hValue == null)
                {
                return R_REPEAT;
                }

            frame.assignValue(f_nRetValue, xBoolean.makeHandle(hValue.getValue() == 0));
            return iPC + 1;
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }
    }
