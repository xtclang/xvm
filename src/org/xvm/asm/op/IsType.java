package org.xvm.asm.op;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.asm.Op;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * IS_TYPE  rvalue, rvalue-type, lvalue-return ; T instanceof Type -> Boolean
 *
 * @author gg 2017.03.08
 */
public class IsType extends Op
    {
    private final int f_nValue;
    private final int f_nType;
    private final int f_nRetValue;

    public IsType(int nValue, int nType, int nRet)
        {
        f_nValue = nValue;
        f_nType = nType;
        f_nRetValue = nRet;
        }

    public IsType(DataInput in)
            throws IOException
        {
        f_nValue = in.readInt();
        f_nType = in.readInt();
        f_nRetValue = in.readInt();
        }

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.write(OP_IS_TYPE);
        out.writeInt(f_nValue);
        out.writeInt(f_nType);
        out.writeInt(f_nRetValue);
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            ObjectHandle hValue1 = frame.getArgument(f_nValue);
            ObjectHandle hValue2 = frame.getArgument(f_nType);

            if (hValue1 == null || hValue2 == null)
                {
                return R_REPEAT;
                }

            // TODO
            return iPC + 1;
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }
    }
