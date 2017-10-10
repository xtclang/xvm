package org.xvm.asm.op;


import java.io.DataInput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.Op;
import org.xvm.asm.Scope;

import org.xvm.runtime.Frame;


/**
 * CATCH ; begin an exception handler (implicit ENTER)
 */
public class CatchStart
        extends Op
    {
    /**
     * Construct a CATCH op.
     */
    public CatchStart()
        {
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public CatchStart(DataInput in, Constant[] aconst)
            throws IOException
        {
        }

    @Override
    public int getOpCode()
        {
        return OP_CATCH;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        // all the logic is actually implemented by Frame.findGuard()
        return iPC + 1;
        }

    @Override
    public void simulate(Scope scope)
        {
        scope.enter();
        }
    }
