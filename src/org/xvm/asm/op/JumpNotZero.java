package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.Op;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * JMP_NZERO rvalue-int, rel-addr ; jump if value is NOT zero
 */
public class JumpNotZero
        extends Op
    {
    private final int f_nValue;
    private final int f_nRelAddr;

    public JumpNotZero(int nValue, int nRelAddr)
        {
        f_nValue = nValue;
        f_nRelAddr = nRelAddr;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public JumpNotZero(DataInput in, Constant[] aconst)
            throws IOException
        {
        f_nValue = readPackedInt(in);
        f_nRelAddr = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.writeByte(OP_JMP_NZERO);
        writePackedLong(out, f_nValue);
        writePackedLong(out, f_nRelAddr);
        }

    @Override
    public int getOpCode()
        {
        return OP_JMP_NZERO;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            JavaLong hTest = (JavaLong) frame.getArgument(f_nValue);

            if (hTest == null)
                {
                return R_REPEAT;
                }

            return hTest.getValue() == 0 ? iPC + 1 : iPC + f_nRelAddr;
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }
    }
