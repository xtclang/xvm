package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.OpInvocable;

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
    public int process(Frame frame, int iPC)
        {
        // all the logic is actually implemented by Frame.findGuard()
        return iPC + 1;
        }
    }
