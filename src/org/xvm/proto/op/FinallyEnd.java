package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.Op;

/**
 * END_HANDLER rel-addr ; finish an exception handler with a jump
 *
 * @author gg 2017.03.08
 */
public class FinallyEnd extends Op
    {
    public FinallyEnd()
        {
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        // a possible exception sits in the first variable of this scope,
        // which is the same as the "next" variable in the previous scope
        int nException = frame.f_anNextVar[frame.m_iScope - 1];

        ExceptionHandle hException = (ExceptionHandle) frame.f_ahVar[nException];
        if (hException == null)
            {
            frame.exitScope();
            return iPC + 1;
            }

        // re-throw
        frame.m_hException = hException;
        return R_EXCEPTION;
        }
    }