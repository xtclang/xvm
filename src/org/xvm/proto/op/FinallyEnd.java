package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.Op;

import java.io.DataOutput;
import java.io.IOException;

/**
 * END_FINALLY ; finish a "finally" handler // note: EXIT
 *
 * @author gg 2017.03.08
 */
public class FinallyEnd extends Op
    {
    public FinallyEnd()
        {
        }

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.write(OP_END_FINALLY);
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
    }