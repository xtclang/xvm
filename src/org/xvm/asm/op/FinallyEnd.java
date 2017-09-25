package org.xvm.asm.op;


import java.io.DataInput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.Op;
import org.xvm.asm.Scope;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;


/**
 * END_FINALLY ; finish a "finally" handler // note: EXIT
 */
public class FinallyEnd
        extends Op
    {
    /**
     * Construct an END_FINALLY op.
     */
    public FinallyEnd()
        {
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public FinallyEnd(DataInput in, Constant[] aconst)
            throws IOException
        {
        }

    @Override
    public int getOpCode()
        {
        return OP_END_FINALLY;
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