package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.Op;

import org.xvm.proto.template.xBoolean;
import org.xvm.proto.template.xNullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * IS_NNULL rvalue, lvalue-return ; T != null -> Boolean
 *
 * @author gg 2017.03.08
 */
public class IsNNull extends Op
    {
    private final int f_nValue;
    private final int f_nRetValue;

    public IsNNull(int nValue, int nRet)
        {
        f_nValue = nValue;
        f_nRetValue = nRet;
        }

    public IsNNull(DataInput in)
            throws IOException
        {
        f_nValue = in.readInt();
        f_nRetValue = in.readInt();
        }

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.write(OP_IS_NNULL);
        out.writeInt(f_nValue);
        out.writeInt(f_nRetValue);
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            ObjectHandle hValue = frame.getArgument(f_nValue);

            if (hValue == null)
                {
                return R_REPEAT;
                }

            frame.assignValue(f_nRetValue, hValue != xNullable.NULL ?
                            xBoolean.TRUE : xBoolean.FALSE);
            return iPC + 1;
            }
        catch (ExceptionHandle.WrapperException e)
            {
            frame.m_hException = e.getExceptionHandle();
            return R_EXCEPTION;
            }
        }
    }
