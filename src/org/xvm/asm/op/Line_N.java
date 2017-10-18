package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.Op;

import org.xvm.runtime.Frame;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * LINE_N - a runtime "no-op" that indicates that the next op-code is from a later line of source
 * code. Used by the debugger, stack trace generation, etc. to determine line numbers from the
 * current location within the op-code stream.
 */
public class Line_N
        extends Op
    {
    /**
     * Construct a LINE_N op.
     *
     * @param cLines  the number of lines to advance
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
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        out.writeByte(OP_LINE_N);
        writePackedLong(out, f_cLines);
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

    private int f_cLines;
    }
