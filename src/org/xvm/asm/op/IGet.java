package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.Op;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;

import org.xvm.runtime.template.IndexSupport;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * A_GET rvalue-target, rvalue-index, lvalue-return ; T = T[Ti]
 */
public class IGet
        extends Op
    {
    private final int f_nTargetValue;
    private final int f_nIndexValue;
    private final int f_nRetValue;

    public IGet(int nTarget, int nIndex, int nRet)
        {
        f_nTargetValue = nTarget;
        f_nIndexValue = nIndex;
        f_nRetValue = nRet;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public IGet(DataInput in, Constant[] aconst)
            throws IOException
        {
        f_nTargetValue = readPackedInt(in);
        f_nIndexValue = readPackedInt(in);
        f_nRetValue = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.write(OP_I_GET);
        writePackedLong(out, f_nTargetValue);
        writePackedLong(out, f_nIndexValue);
        writePackedLong(out, f_nRetValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_I_GET;
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

            return frame.assignValue(f_nRetValue,
                    template.extractArrayValue(hTarget, lIndex));
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }
    }
