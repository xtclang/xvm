package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.Op;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * GP_ADD rvalue1, rvalue2, lvalue   ; T + T -> T
 */
public class GP_Add
        extends Op
    {
    /**
     * Construct a GP_ADD op.
     *
     * @param nTarget  the first r-value, which will implement the add
     * @param nArg     the second r-value
     * @param nRet     the l-value to store the result into
     */
    public GP_Add(int nTarget, int nArg, int nRet)
        {
        f_nTargetValue = nTarget;
        f_nArgValue    = nArg;
        f_nRetValue    = nRet;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public GP_Add(DataInput in, Constant[] aconst)
            throws IOException
        {
        f_nTargetValue = readPackedInt(in);
        f_nArgValue    = readPackedInt(in);
        f_nRetValue    = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        out.writeByte(OP_GP_ADD);
        writePackedLong(out, f_nTargetValue);
        writePackedLong(out, f_nArgValue);
        writePackedLong(out, f_nRetValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_GP_ADD;
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

    private final int f_nTargetValue;
    private final int f_nArgValue;
    private final int f_nRetValue;
    }
