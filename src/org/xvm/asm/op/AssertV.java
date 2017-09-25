package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.Op;

import org.xvm.asm.constants.StringConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;

import org.xvm.runtime.template.xBoolean.BooleanHandle;
import org.xvm.runtime.template.xException;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * ASSERT_V rvalue, CONST_STRING, #vals(rvalue)
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
        f_nValue       = nValue;
        f_nTextConstId = nTextId;
        f_anValue      = anValue;
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
        f_nValue       = readPackedInt(in);
        f_nTextConstId = readPackedInt(in);
        f_anValue      = readIntArray(in);
        }

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.writeByte(OP_ASSERT_V);
        writePackedLong(out, f_nValue);
        writePackedLong(out, f_nTextConstId);
        writeIntArray(out, f_anValue);
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
            BooleanHandle hTest = (BooleanHandle) frame.getArgument(f_nValue);
            if (hTest == null)
                {
                return R_REPEAT;
                }

            if (hTest.get())
                {
                return iPC + 1;
                }

            StringConstant constText = (StringConstant)
                    frame.f_context.f_pool.getConstant(f_nTextConstId);

            StringBuilder sb = new StringBuilder("Assertion failed: ");
            sb.append(constText.getValueString())
              .append(" (");

            for (int i = 0, c = f_anValue.length; i < c; i++)
                {
                if (i > 0)
                    {
                    sb.append(", ");
                    }
                int nValue = f_anValue[i];

                Frame.VarInfo info = frame.getVarInfo(nValue);
                ObjectHandle hValue = frame.getArgument(nValue);

                sb.append(info.f_sVarName)
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

    private final int   f_nValue;
    private final int   f_nTextConstId;
    private final int[] f_anValue;
    }
