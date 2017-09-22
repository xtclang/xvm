package org.xvm.asm.op;


import java.io.DataInput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.Op;

import org.xvm.runtime.Frame;


/**
 * LINE_1 - a runtime "no-op" that indicates that the next op-code is from the next line of source code. Used by the
 * debugger, stack trace generation, etc. to determine line numbers from the current location within the op-code stream.
 */
public class Line_1
        extends Op
    {
    /**
     * Constructor.
     */
    public Line_1()
        {
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public Line_1(DataInput in, Constant[] aconst)
            throws IOException
        {
        }

    @Override
    public int getOpCode()
        {
        return OP_LINE_1;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        return iPC + 1;
        }
    }
