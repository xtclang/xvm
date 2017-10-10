package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.Op;
import org.xvm.asm.Scope;

import org.xvm.runtime.Frame;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * CATCH_END addr ; finish an exception handler with a jump
 */
public class CatchEnd
        extends Op
    {
    /**
     * Construct a CATCH_END op.
     *
     * @param iRelAddr  the relative address to jump to when the handler completes
     */
    public CatchEnd(int iRelAddr)
        {
        f_nRelAddr = iRelAddr;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public CatchEnd(DataInput in, Constant[] aconst)
            throws IOException
        {
        f_nRelAddr = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        out.writeByte(OP_CATCH_END);
        writePackedLong(out, f_nRelAddr);
        }

    @Override
    public int getOpCode()
        {
        return OP_CATCH_END;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        frame.exitScope();
        return iPC + f_nRelAddr;
        }

    @Override
    public void simulate(Scope scope)
        {
        scope.exit();
        }

    private final int f_nRelAddr;
    }