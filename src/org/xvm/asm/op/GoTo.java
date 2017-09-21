package org.xvm.asm.op;

import org.xvm.proto.Frame;
import org.xvm.asm.Op;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * GOTO abs-addr
 *
 * @author gg 2017.03.08
 */
public class GoTo extends Op
    {
    private final int f_nAbsAddr;

    public GoTo(int nAbsAddr)
        {
        f_nAbsAddr = nAbsAddr;
        }

    public GoTo(DataInput in)
            throws IOException
        {
        f_nAbsAddr = in.readInt();
        }

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.write(OP_GOTO);
        out.writeInt(f_nAbsAddr);
        }


    @Override
    public int process(Frame frame, int iPC)
        {
        return f_nAbsAddr;
        }
    }
