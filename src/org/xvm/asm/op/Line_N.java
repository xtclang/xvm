package org.xvm.asm.op;


import java.io.DataInput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.Op;

import org.xvm.runtime.Frame;

import static org.xvm.util.Handy.readPackedInt;


/**
 * LINE_1 - a runtime "no-op" that indicates that the next op-code is from the next line of source code. Used by the
 * debugger, stack trace generation, etc. to determine line numbers from the current location within the op-code stream.
 */
public class Line_N
        extends Op
    {
    /**
     * Constructor.
     */
    public Line_N(int cLines)
        {
        f_cLines = cLines;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public Line_N(DataInput in, Constant[] aconst)
            throws IOException
        {
        f_cLines = readPackedInt(in);
        }

    @Override
    public int getOpCode()
        {
        return OP_LINE_N;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        return iPC + 1;
        }

    private final int f_cLines;
    }
