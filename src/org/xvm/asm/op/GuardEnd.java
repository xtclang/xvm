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
 * END_GUARD rel_addr
 */
public class GuardEnd
        extends Op
    {
    public GuardEnd(int iRelAddr)
        {
        f_nRelAddr = iRelAddr;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public GuardEnd(DataInput in, Constant[] aconst)
            throws IOException
        {
        f_nRelAddr = readPackedInt(in);
        }

    @Override
    public int getOpCode()
        {
        return OP_END_GUARD;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        frame.popGuard();
        frame.exitScope();
        return iPC + f_nRelAddr;
        }

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.write(OP_END_GUARD);
        writePackedLong(out, f_nRelAddr);
        }

    private final int f_nRelAddr;
    }
