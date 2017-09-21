package org.xvm.asm.op;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.asm.Op;

import org.xvm.proto.template.IndexSupport;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * I_PREINC rvalue-target, rvalue-index, lvalue-return ; T = T[Ti]
 *
 * @author gg 2017.03.08
 */
public class IPreInc extends Op
    {
    private final int f_nTargetValue;
    private final int f_nIndexValue;
    private final int f_nRetValue;

    public IPreInc(int nTarget, int nIndex, int nRet)
        {
        f_nTargetValue = nTarget;
        f_nIndexValue = nIndex;
        f_nRetValue = nRet;
        }

    public IPreInc(DataInput in)
            throws IOException
        {
        f_nTargetValue = in.readInt();
        f_nIndexValue = in.readInt();
        f_nRetValue = in.readInt();
        }

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.write(OP_I_PREINC);
        out.writeInt(f_nTargetValue);
        out.writeInt(f_nIndexValue);
        out.writeInt(f_nRetValue);
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            ObjectHandle hTarget = frame.getArgument(f_nTargetValue);
            long lIndex = frame.getIndex(f_nIndexValue);

            if (hTarget == null || lIndex == -1)
                {
                return R_REPEAT;
                }

            IndexSupport template = (IndexSupport) hTarget.f_clazz.f_template;

            return template.invokePreInc(frame, hTarget,lIndex, f_nRetValue);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }
    }
