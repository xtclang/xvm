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
 * NOP - a "no op". This class also represents all LINE_ op-codes.
 *
 * Used by the debugger, stack trace generation, etc. to determine line numbers from the
 * current location within the op-code stream.
 */
public class Nop extends Op
    {
    /**
     * Construct a NOP op.
     */
    public Nop()
        {
        this(0);
        }

    /**
     * Construct a LINE_ op.
     *
     * @param cLines  the number of lines to advance
     */
    public Nop(int cLines)
        {
        int nOp;
        switch (cLines)
            {
            case 0:
                nOp = OP_NOP;
                break;
            case 1:
                nOp = OP_LINE_1;
                break;
            case 2:
                nOp = OP_LINE_2;
                break;
            case 3:
                nOp = OP_LINE_3;
                break;
            default:
                nOp = OP_LINE_N;
                break;
            }

        f_nOp = nOp;
        f_cLines = cLines;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public Nop(DataInput in, Constant[] aconst)
            throws IOException
        {
        f_nOp = OP_LINE_N;
        f_cLines = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        int nOp = getOpCode();
        out.writeByte(nOp);

        if (nOp == OP_LINE_N)
            {
            writePackedLong(out, f_cLines);
            }
        }

    @Override
    public int getOpCode()
        {
        return f_nOp;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        return iPC + 1;
        }

    private final int f_nOp;
    private final int f_cLines;
    }
