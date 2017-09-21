package org.xvm.asm.op;

import org.xvm.proto.Frame;
import org.xvm.asm.Op;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * END_GUARD rel_addr
 *
 * @author gg 2017.03.08
 */
public class GuardEnd extends Op
    {
    private final int f_nRelAddr;

    public GuardEnd(int iRelAddr)
        {
        f_nRelAddr = iRelAddr;
        }

    public GuardEnd(DataInput in)
            throws IOException
        {
        f_nRelAddr = in.readInt();
        }

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.write(OP_END_GUARD);
        out.writeInt(f_nRelAddr);
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        frame.popGuard();

        frame.exitScope();

        return iPC + f_nRelAddr;
        }
    }
