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
 * END_HANDLER rel-addr ; finish an exception handler with a jump
 */
public class HandlerEnd
        extends Op
    {
    public HandlerEnd(int iRelAddr)
        {
        f_nRelAddr = iRelAddr;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public HandlerEnd(DataInput in, Constant[] aconst)
            throws IOException
        {
        f_nRelAddr = readPackedInt(in);
        }

    @Override
    public int getOpCode()
        {
        return OP_END_HANDLER;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        frame.exitScope();
        return iPC + f_nRelAddr;
        }

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.writeByte(OP_END_HANDLER);
        writePackedLong(out, f_nRelAddr);
        }

    private final int f_nRelAddr;
    }