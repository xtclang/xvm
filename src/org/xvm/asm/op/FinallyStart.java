package org.xvm.asm.op;


import java.io.DataInput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.Op;

import org.xvm.runtime.Frame;


/**
 * FINALLY ; begin a "finally" handler (implicit EXIT/ENTER and an exception var)
 */
public class FinallyStart
        extends Op
    {
    public FinallyStart()
        {
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public FinallyStart(DataInput in, Constant[] aconst)
            throws IOException
        {
        }

    @Override
    public int getOpCode()
        {
        return OP_FINALLY;
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
