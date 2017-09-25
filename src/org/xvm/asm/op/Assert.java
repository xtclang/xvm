package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.Op;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;

import org.xvm.runtime.template.xBoolean.BooleanHandle;
import org.xvm.runtime.template.xException;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * ASSERT rvalue
 */
public class Assert
        extends Op
    {
    /**
     * Construct an ASSERT op.
     *
     * @param nValue  the r-value of the assertion expression
     */
    public Assert(int nValue)
        {
        f_nValue = nValue;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public Assert(DataInput in, Constant[] aconst)
            throws IOException
        {
        f_nValue = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.writeByte(OP_ASSERT);
        writePackedLong(out, f_nValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_ASSERT;
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

            if (hTest.get())
                {
                return iPC + 1;
                }

            return frame.raiseException(xException.makeHandle("Assertion failed"));
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    private final int f_nValue;
    }
