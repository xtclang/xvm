package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.OpInvocable;

/**
 * FINALLY ; begin a "finally" handler (implicit ENTER and an exception var)
 *
 * @author gg 2017.03.08
 */
public class FinallyStart extends OpInvocable
    {
    public FinallyStart()
        {
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        int iScope = frame.enterScope();

        // if coming from Frame.findGuard(), there is an exception at
        // anNextVar[iScope] + 1

        // we need to reserve the slot (unassigned) when coming in normally
        frame.f_anNextVar[iScope]++;

        return iPC + 1;
        }
    }
