package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.Frame.Guard;
import org.xvm.proto.Op;

/**
 * ENDGUARD op-code.
 *
 * @author gg 2017.03.08
 */
public class GuardEnd extends Op
    {
    public GuardEnd()
        {
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        frame.f_aiRegister[I_GUARD]--;

        // ++ Exit
        frame.f_aiRegister[I_SCOPE]--;
        // --

        return iPC + 1;
        }
    }
