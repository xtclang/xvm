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
 * JUMP rel-addr
 */
public class Jump
        extends Op
    {
    private final int f_nRelAddr;

    public Jump(int nRelAddr)
        {
        f_nRelAddr = nRelAddr;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public Jump(DataInput in, Constant[] aconst)
            throws IOException
        {
        f_nRelAddr = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.writeByte(OP_JMP);
        writePackedLong(out, f_nRelAddr);
        }

    @Override
    public int getOpCode()
        {
        return OP_JMP;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        return iPC + f_nRelAddr;
        }
    }
