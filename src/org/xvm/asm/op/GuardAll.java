package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.Op;
import org.xvm.asm.Scope;

import org.xvm.runtime.Frame;
import org.xvm.runtime.Frame.AllGuard;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * GUARD_ALL rel_addr ; ENTER
 */
public class GuardAll
        extends Op
    {
    /**
     * Construct a GUARD_ALL op.
     *
     * @param nRelAddress  the relative address of the finally block
     */
    public GuardAll(int nRelAddress)
        {
        f_nFinallyRelAddress = nRelAddress;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public GuardAll(DataInput in, Constant[] aconst)
            throws IOException
        {
        f_nFinallyRelAddress = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.writeByte(OP_GUARD_ALL);
        writePackedLong(out, f_nFinallyRelAddress);
        }

    @Override
    public int getOpCode()
        {
        return OP_GUARD_ALL;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        int iScope = frame.enterScope();

        AllGuard guard = m_guard;
        if (guard == null)
            {
            m_guard = guard = new AllGuard(iPC, iScope, f_nFinallyRelAddress);
            }
        frame.pushGuard(guard);

        return iPC + 1;
        }

    @Override
    public void simulate(Scope scope)
        {
        scope.enter();
        }

    private final int f_nFinallyRelAddress;

    private transient AllGuard m_guard; // cached struct
    }
