package org.xvm.asm.op;


import java.io.DataInput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.Op;

import org.xvm.runtime.Frame;


/**
 * ENTER ; (variable scope begin)
 */
public class Enter
        extends Op
    {
    /**
     * Constructor.
     */
    public Enter()
        {
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public Enter(DataInput in, Constant[] aconst)
            throws IOException
        {
        }

    @Override
    public int getOpCode()
        {
        return OP_ENTER;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        frame.enterScope();

        return iPC + 1;
        }
    }
