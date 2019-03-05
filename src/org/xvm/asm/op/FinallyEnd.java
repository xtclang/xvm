package org.xvm.asm.op;


import org.xvm.asm.Op;
import org.xvm.asm.Scope;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;

import org.xvm.runtime.template.xNullable;


/**
 * FINALLY_END ; finish a "finally" handler (Implicit EXIT)
 * <p/>
 * Each FINALLY_END op must match up with a previous GUARD_ALL and FINALLY op.
 * <p/>
 * The FINALLY_END op either re-throws the exception that occurred within the GUARD_ALL block, or
 * if no exception had occurred, it exits the scope and proceeds to the next instruction.
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
    public boolean isExit()
        {
        return true;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        // a possible exception sits in the first variable of this scope,
        // which is the same as the "next" variable in the previous scope
        // (see Frame.findGuard and FinallyStart.process)
        int nException = frame.f_anNextVar[frame.m_iScope - 1];

        ObjectHandle hException = frame.f_ahVar[nException];
        if (hException == xNullable.NULL)
            {
            frame.exitScope();
            return iPC + 1;
            }
        else
            {
            // re-throw
            return frame.raiseException((ExceptionHandle) hException);
            }
        }

    @Override
    public void simulate(Scope scope)
        {
        scope.exit(this);
        }
    }