package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.Op;

import org.xvm.runtime.Frame;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * RETURN_1 rvalue
 */
public class Return_1
        extends Op
    {
    /**
     * Construct a RETURN_1 op.
     *
     * @param nValue  the value to return
     */
    public Return_1(int nValue)
        {
        f_nArgValue = nValue;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public Return_1(DataInput in, Constant[] aconst)
            throws IOException
        {
        f_nArgValue = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
    throws IOException
        {
        out.writeByte(OP_RETURN_1);
        writePackedLong(out, f_nArgValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_RETURN_1;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        int iRet = frame.f_iReturn;

        if (iRet >= 0 || iRet == Frame.RET_LOCAL)
            {
            return frame.returnValue(iRet, f_nArgValue);
            }

        switch (iRet)
            {
            case Frame.RET_UNUSED:
                return R_RETURN;

            case Frame.RET_MULTI:
                throw new IllegalStateException();

            default:
                return frame.returnTuple(-iRet - 1, new int[] {f_nArgValue});
            }
        }

    private final int f_nArgValue;
    }
