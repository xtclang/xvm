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
        m_nValue = nValue;
        m_nTextConstId = nTextId;
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
        m_nValue = readPackedInt(in);
        m_nTextConstId = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        out.writeByte(OP_ASSERT_M);
        writePackedLong(out, m_nValue);
        writePackedLong(out, m_nTextConstId);
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
            BooleanHandle hTest = (BooleanHandle) frame.getArgument(m_nValue);
            if (hTest == null)
                {
                return R_REPEAT;
                }

            if (hTest.get())
                {
                return iPC + 1;
                }

            return frame.raiseException(
                    xException.makeHandle("Assertion failed: " + frame.getString(m_nTextConstId)));
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    private int m_nValue;
    private int m_nTextConstId;
    }
