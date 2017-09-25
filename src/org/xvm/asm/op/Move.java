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
 * MOV rvalue-src, lvalue-dest
 */
public class Move
        extends Op
    {
    /**
     * Construct a MOV op.
     *
     * @param nFrom  the source location
     * @param nTo    the destination location
     */
    public Move(int nFrom, int nTo)
        {
        f_nToValue   = nTo;
        f_nFromValue = nFrom;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public Move(DataInput in, Constant[] aconst)
            throws IOException
        {
        f_nFromValue = readPackedInt(in);
        f_nToValue   = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out)
    throws IOException
        {
        out.writeByte(OP_MOV);
        writePackedLong(out, f_nFromValue);
        writePackedLong(out, f_nToValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_MOV;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            ObjectHandle hValue = frame.getArgument(f_nFromValue);
            if (hValue == null)
                {
                return R_REPEAT;
                }

            return frame.assignValue(f_nToValue, hValue);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    final private int f_nFromValue;
    final private int f_nToValue;
    }
