package org.xvm.asm.op;

import org.xvm.proto.Frame;
import org.xvm.asm.Op;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * RETURN_1 rvalue
 *
 * @author gg 2017.03.08
 */
public class Return_1 extends Op
    {
    private final int f_nArgValue;

    public Return_1(int nValue)
        {
        f_nArgValue = nValue;
        }

    public Return_1(DataInput in)
            throws IOException
        {
        f_nArgValue = in.readInt();
        }

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.write(OP_RETURN_1);
        out.writeInt(f_nArgValue);
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
    }
