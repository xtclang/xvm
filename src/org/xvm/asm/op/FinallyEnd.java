package org.xvm.asm.op;


import org.xvm.asm.Op;
import org.xvm.asm.Scope;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;


/**
 * FINALLY_END ; finish a "finally" handler (Implicit EXIT)
 */
public class FinallyEnd
        extends Op
    {
    /**
     * Construct an FINALLY_END op.
     */
    public FinallyEnd()
        {
        }

    @Override
    public int getOpCode()
        {
        return OP_FINALLY_END;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        // a possible exception sits in the first variable of this scope,
        // which is the same as the "next" variable in the previous scope
        // (see Frame.findGuard and FinallyStart.process)
        int nException = frame.f_anNextVar[frame.m_iScope - 1];

        ExceptionHandle hException = (ExceptionHandle) frame.f_ahVar[nException];
        if (hException == null)
            {
            frame.exitScope();
            return iPC + 1;
            }

        // re-throw
        return frame.raiseException(hException);
        }

    @Override
    public void simulate(Scope scope)
        {
        scope.exit();
        }
    }