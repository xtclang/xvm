package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.Op;

import java.io.DataOutput;
import java.io.IOException;

/**
 * EXIT (variable scope end)
 *
 * @author gg 2017.03.08
 */
public class Exit extends Op
    {
    public Exit()
        {
        }

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.write(OP_EXIT);
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        frame.exitScope();

        return iPC + 1;
        }
    }
