package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.Op;

import org.xvm.proto.template.IndexSupport;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * A_SET rvalue-target, rvalue-index, rvalue-new-value ; T[Ti] = T
 *
 * @author gg 2017.03.08
 */
public class ISet extends Op
    {
    private final int f_nTargetValue;
    private final int f_nIndexValue;
    private final int f_nValue;

    public ISet(int nTarget, int nIndex, int nValue)
        {
        f_nTargetValue = nTarget;
        f_nIndexValue = nIndex;
        f_nValue = nValue;
        }

    public ISet(DataInput in)
            throws IOException
        {
        f_nTargetValue = in.readInt();
        f_nIndexValue = in.readInt();
        f_nValue = in.readInt();
        }

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.write(OP_I_SET);
        out.writeInt(f_nTargetValue);
        out.writeInt(f_nIndexValue);
        out.writeInt(f_nValue);
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        ExceptionHandle hException;

        try
            {
            ObjectHandle hTarget = frame.getArgument(f_nTargetValue);
            long lIndex = frame.getIndex(f_nIndexValue);
            ObjectHandle hArg = frame.getArgument(f_nValue);

            if (hTarget == null || hArg == null || lIndex == -1)
                {
                return R_REPEAT;
                }

            IndexSupport template = (IndexSupport) hTarget.f_clazz.f_template;

            hException = template.assignArrayValue(hTarget, lIndex, hArg);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            hException = e.getExceptionHandle();
            }

        if (hException == null)
            {
            return iPC + 1;
            }

        frame.m_hException = hException;
        return R_EXCEPTION;
        }
    }