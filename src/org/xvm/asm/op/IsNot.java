package org.xvm.asm.op;

import org.xvm.proto.ObjectHandle.ExceptionHandle;

import org.xvm.proto.Frame;
import org.xvm.asm.Op;

import org.xvm.proto.template.xBoolean;
import org.xvm.proto.template.xBoolean.BooleanHandle;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * IS_NOT rvalue, lvalue-return ; !T -> Boolean
 *
 * @author gg 2017.03.08
 */
public class IsNot extends Op
    {
    private final int f_nValue;
    private final int f_nRetValue;

    public IsNot(int nValue, int nRet)
        {
        f_nValue = nValue;
        f_nRetValue = nRet;
        }

    public IsNot(DataInput in)
            throws IOException
        {
        f_nValue = in.readInt();
        f_nRetValue = in.readInt();
        }

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.write(OP_IS_NOT);
        out.writeInt(f_nValue);
        out.writeInt(f_nRetValue);
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            BooleanHandle hValue = (BooleanHandle) frame.getArgument(f_nValue);

            if (hValue == null)
                {
                return R_REPEAT;
                }

            frame.assignValue(f_nRetValue, xBoolean.not(hValue));

            return iPC + 1;
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }
    }
