package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Op;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;


/**
 * THROW rvalue
 *
 * @author gg 2017.03.08
 */
public class Throw extends Op
    {
    private final int f_nArgValue;

    public Throw(int nValue)
        {
        f_nArgValue = nValue;
        }

    public Throw(DataInput in)
            throws IOException
        {
        f_nArgValue = in.readInt();
        }

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.write(OP_THROW);
        out.writeInt(f_nArgValue);
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            // there are no "const" exceptions
            ExceptionHandle hException = (ExceptionHandle) frame.getArgument(f_nArgValue);
            if (hException == null)
                {
                return R_REPEAT;
                }

            return frame.raiseException(hException);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }
    }
