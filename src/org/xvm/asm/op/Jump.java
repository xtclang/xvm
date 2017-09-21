package org.xvm.asm.op;

import org.xvm.proto.Frame;
import org.xvm.asm.Op;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * JUMP rel-addr
 *
 * @author gg 2017.03.08
 */
public class Jump extends Op
    {
    private final int f_nRelAddr;

    public Jump(int nRelAddr)
        {
        f_nRelAddr = nRelAddr;
        }

    public Jump(DataInput in)
            throws IOException
        {
        f_nRelAddr = in.readInt();
        }

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.write(OP_JMP);
        out.writeInt(f_nRelAddr);
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        return iPC + f_nRelAddr;
        }
    }
