package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.Op;
import org.xvm.proto.TypeComposition;
import org.xvm.proto.template.xBoolean.BooleanHandle;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * JMP_NEQ rvalue, rvalue, rel-addr ; jump if value is NOT equal
 *
 * @author gg 2017.03.08
 */
public class JumpNotEq extends Op
    {
    private final int f_nValue1;
    private final int f_nValue2;
    private final int f_nRelAddr;

    public JumpNotEq(int nValue1, int nValue2, int nRelAddr)
        {
        f_nValue1 = nValue1;
        f_nValue2 = nValue2;
        f_nRelAddr = nRelAddr;
        }

    public JumpNotEq(DataInput in)
            throws IOException
        {
        f_nValue1 = in.readInt();
        f_nValue2 = in.readInt();
        f_nRelAddr = in.readInt();
        }

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.write(OP_JMP_NEQ);
        out.writeInt(f_nValue1);
        out.writeInt(f_nValue2);
        out.writeInt(f_nRelAddr);
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            ObjectHandle hTest1 = frame.getArgument(f_nValue1);
            ObjectHandle hTest2 = frame.getArgument(f_nValue2);

            if (hTest1 == null || hTest2 == null)
                {
                return R_REPEAT;
                }

            TypeComposition clz1 = frame.getArgumentClass(f_nValue1);
            TypeComposition clz2 = frame.getArgumentClass(f_nValue2);

            if (clz1 != clz2)
                {
                // this should've not compiled
                throw new IllegalStateException();
                }

            int iResult = clz1.callEquals(frame, hTest1, hTest2, Frame.RET_LOCAL);

            if (iResult == R_EXCEPTION)
                {
                return R_EXCEPTION;
                }

            BooleanHandle hValue = (BooleanHandle) frame.getFrameLocal();

            return hValue.get() ? iPC + 1 : iPC + f_nRelAddr;
            }
        catch (ExceptionHandle.WrapperException e)
            {
            frame.m_hException = e.getExceptionHandle();
            return R_EXCEPTION;
            }
        }
    }
