package org.xvm.proto.op;

import org.xvm.asm.constants.CharStringConstant;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.OpCallable;

import org.xvm.proto.template.xBoolean.BooleanHandle;
import org.xvm.proto.template.xException;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * ASSERT rvalue, CONST_STRING
 *
 * @author gg 2017.03.08
 */
public class AssertT extends OpCallable
    {
    private final int f_nValue;
    private final int f_nTextConstId;

    public AssertT(int nValue, int nTextId)
        {
        f_nValue = nValue;
        f_nTextConstId = nTextId;
        }

    public AssertT(DataInput in)
            throws IOException
        {
        f_nValue = in.readInt();
        f_nTextConstId = in.readInt();
        }

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.write(OP_ASSERT_T);
        out.writeInt(f_nValue);
        out.writeInt(f_nTextConstId);
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        ExceptionHandle hException;
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

            CharStringConstant constText = (CharStringConstant)
                    frame.f_context.f_pool.getConstant(-f_nTextConstId);

            hException = xException.makeHandle("Assertion failed: " + constText.getValueString());
            }
        catch (ExceptionHandle.WrapperException e)
            {
            hException = e.getExceptionHandle();
            }

        frame.m_hException = hException;
        return R_EXCEPTION;
        }
    }
