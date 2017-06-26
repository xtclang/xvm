package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.OpInvocable;

/**
 * FINALLY ; begin a "finally" handler (implicit EXIT/ENTER and an exception var)
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
        frame.exitScope();

        int iScope = frame.enterScope();

        // this op-code can only be reached by the normal flow of execution,
        // while upon an exception, the GuardAll would jump to the very next op
        // (called from Frame.findGuard) with an exception at anNextVar[iScope] + 1,
        // so we need to reserve the slot (unassigned) when coming in normally;
        // presence or absence of the exception will be checked by the FinallyEnd
        frame.f_anNextVar[iScope]++;

        return iPC + 1;
        }
    }
