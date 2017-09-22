package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.Op;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;

import org.xvm.runtime.template.xBoolean;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * IS_ZERO rvalue-int, lvalue-return ; T == 0 -> Boolean
 */
public class IsZero
        extends Op
    {
    private final int f_nValue;
    private final int f_nRetValue;

    public IsZero(int nValue, int nRet)
        {
        f_nValue = nValue;
        f_nRetValue = nRet;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public IsZero(DataInput in, Constant[] aconst)
            throws IOException
        {
        f_nValue = readPackedInt(in);
        f_nRetValue = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.write(OP_IS_ZERO);
        writePackedLong(out, f_nValue);
        writePackedLong(out, f_nRetValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_IS_ZERO;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            JavaLong hValue = (JavaLong) frame.getArgument(f_nValue);

            if (hValue == null)
                {
                return R_REPEAT;
                }

            frame.assignValue(f_nRetValue, xBoolean.makeHandle(hValue.getValue() == 0));
            return iPC + 1;
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }
    }
