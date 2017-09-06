package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.Op;

import org.xvm.proto.ObjectHandle.ExceptionHandle;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * ADD rvalue-target, rvalue-second, lvalue-return   ; T + T -> T
 *
 * @author gg 2017.03.08
 */
public class Add extends Op
    {
    private final int f_nTargetValue;
    private final int f_nArgValue;
    private final int f_nRetValue;

    public Add(int nTarget, int nArg, int nRet)
        {
        f_nTargetValue = nTarget;
        f_nArgValue = nArg;
        f_nRetValue = nRet;
        }

    public Add(DataInput in)
            throws IOException
        {
        f_nTargetValue = in.readInt();
        f_nArgValue = in.readInt();
        f_nRetValue = in.readInt();
        }
    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.write(OP_ADD);
        out.writeInt(f_nTargetValue);
        out.writeInt(f_nArgValue);
        out.writeInt(f_nRetValue);
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            ObjectHandle hTarget = frame.getArgument(f_nTargetValue);
            ObjectHandle hArg = frame.getArgument(f_nArgValue);

            if (hTarget == null || hArg == null)
                {
                return R_REPEAT;
                }

            return hTarget.f_clazz.f_template.invokeAdd(frame, hTarget, hArg, f_nRetValue);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }
    }
