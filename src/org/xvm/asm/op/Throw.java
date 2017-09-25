package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.Op;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * THROW rvalue
 */
public class Throw
        extends Op
    {
    /**
     * Construct a THROW op.
     *
     * @param nValue  the exception to throw
     */
    public Throw(int nValue)
        {
        f_nArgValue = nValue;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public Throw(DataInput in, Constant[] aconst)
            throws IOException
        {
        f_nArgValue = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.writeByte(OP_THROW);
        writePackedLong(out, f_nArgValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_THROW;
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

    private final int f_nArgValue;
    }
