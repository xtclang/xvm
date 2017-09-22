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
 * GOTO abs-addr
 */
public class GoTo
        extends Op
    {
    private final int f_nAbsAddr;

    public GoTo(int nAbsAddr)
        {
        f_nAbsAddr = nAbsAddr;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public GoTo(DataInput in, Constant[] aconst)
            throws IOException
        {
        f_nAbsAddr = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out)
    throws IOException
        {
        out.write(OP_GOTO);
        writePackedLong(out, f_nAbsAddr);
        }


    @Override
    public int getOpCode()
        {
        return OP_GOTO;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        return f_nAbsAddr;
        }
    }
