package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.Op;

import org.xvm.proto.template.xBoolean.BooleanHandle;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * JMP_TRUE rvalue-bool, rel-addr ; jump if value is true
 *
 * @author gg 2017.03.08
 */
public class JumpTrue extends Op
    {
    private final int f_nValue;
    private final int f_nRelAddr;

    public JumpTrue(int nValue, int nRelAddr)
        {
        f_nValue = nValue;
        f_nRelAddr = nRelAddr;
        }

    public JumpTrue(DataInput in)
            throws IOException
        {
        f_nValue = in.readInt();
        f_nRelAddr = in.readInt();
        }

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.write(OP_JMP_TRUE);
        out.writeInt(f_nValue);
        out.writeInt(f_nRelAddr);
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            BooleanHandle hTest = (BooleanHandle) frame.getArgument(f_nValue);
            if (hTest == null)
                {
                return R_REPEAT;
                }

            return hTest.get() ? iPC + f_nRelAddr : iPC + 1;
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }
    }
