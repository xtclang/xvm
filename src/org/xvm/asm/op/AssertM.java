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
 * ASSERT_M rvalue, STRING
 */
public class AssertM
        extends Op
    {
    /**
     * Construct an ASSERT_T op.
     *
     * @param nValue   the r-value to test
     * @param nTextId  the text to display on assertion failure
     */
    public AssertM(int nValue, int nTextId)
        {
        f_nValue = nValue;
        f_nTextConstId = nTextId;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public AssertM(DataInput in, Constant[] aconst)
            throws IOException
        {
        f_nValue = readPackedInt(in);
        f_nTextConstId = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        out.writeByte(OP_ASSERT_M);
        writePackedLong(out, f_nValue);
        writePackedLong(out, f_nTextConstId);
        }

    @Override
    public int getOpCode()
        {
        return OP_ASSERT_M;
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

            return frame.raiseException(
                    xException.makeHandle("Assertion failed: " + frame.getString(f_nTextConstId)));
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    private final int f_nValue;
    private final int f_nTextConstId;
    }
