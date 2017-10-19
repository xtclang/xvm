package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.Op;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;

import org.xvm.runtime.template.xBoolean.BooleanHandle;
import org.xvm.runtime.template.xException;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * ASSERT_V rvalue, STRING, #vals(rvalue)
 */
public class AssertV
        extends Op
    {
    /**
     * Construct an ASSERT_V op.
     *
     * @param nValue   the r-value to test
     * @param nTextId  the text to print on assertion failure
     * @param anValue  the values to print on assertion failure
     */
    public AssertV(int nValue, int nTextId, int[] anValue)
        {
        m_nValue       = nValue;
        m_nTextConstId = nTextId;
        m_anValue      = anValue;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public AssertV(DataInput in, Constant[] aconst)
            throws IOException
        {
        m_nValue       = readPackedInt(in);
        m_nTextConstId = readPackedInt(in);
        m_anValue      = readIntArray(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        out.writeByte(OP_ASSERT_V);
        writePackedLong(out, m_nValue);
        writePackedLong(out, m_nTextConstId);
        writeIntArray(out, m_anValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_ASSERT_V;
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

            StringBuilder sb = new StringBuilder("Assertion failed: ");
            sb.append(frame.getString(m_nTextConstId))
              .append(" (");

            for (int i = 0, c = m_anValue.length; i < c; i++)
                {
                if (i > 0)
                    {
                    sb.append(", ");
                    }
                int nValue = m_anValue[i];

                Frame.VarInfo info = frame.getVarInfo(nValue);
                ObjectHandle hValue = frame.getArgument(nValue);

                sb.append(info.getName())
                  .append('=')
                  .append(hValue);
                }
            sb.append(')');

            return frame.raiseException(xException.makeHandle(sb.toString()));
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    private int   m_nValue;
    private int   m_nTextConstId;
    private int[] m_anValue;
    }
