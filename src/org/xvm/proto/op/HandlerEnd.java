package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.Op;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * END_HANDLER rel-addr ; finish an exception handler with a jump
 *
 * @author gg 2017.03.08
 */
public class HandlerEnd extends Op
    {
    private final int f_nRelAddr;

    public HandlerEnd(int iRelAddr)
        {
        f_nRelAddr = iRelAddr;
        }

    public HandlerEnd(DataInput in)
            throws IOException
        {
        f_nRelAddr = in.readInt();
        }

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.write(OP_END_HANDLER);
        out.writeInt(f_nRelAddr);
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        frame.exitScope();

        return iPC + f_nRelAddr;
        }
    }