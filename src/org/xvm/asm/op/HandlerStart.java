package org.xvm.asm.op;

import org.xvm.proto.Frame;
import org.xvm.asm.OpInvocable;

import java.io.DataOutput;
import java.io.IOException;

/**
 * HANDLER ; begin an exception handler (implicit ENTER)
 *
 * @author gg 2017.03.08
 */
public class HandlerStart extends OpInvocable
    {
    public HandlerStart()
        {
        }

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.write(OP_HANDLER);
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        // all the logic is actually implemented by Frame.findGuard()
        return iPC + 1;
        }
    }
