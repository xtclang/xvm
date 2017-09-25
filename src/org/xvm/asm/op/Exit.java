package org.xvm.asm.op;


import java.io.DataInput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.Op;
import org.xvm.asm.Scope;

import org.xvm.runtime.Frame;


/**
 * EXIT (variable scope end)
 */
public class Exit
        extends Op
    {
    /**
     * Constructor.
     */
    public Exit()
        {
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public Exit(DataInput in, Constant[] aconst)
            throws IOException
        {
        }

    @Override
    public int getOpCode()
        {
        return OP_EXIT;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        frame.exitScope();

        return iPC + 1;
        }

    @Override
    public void simulate(Scope scope)
        {
        scope.exit();
        }
    }
