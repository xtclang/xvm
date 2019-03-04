package org.xvm.asm.op;


import java.io.DataInput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.Op;
import org.xvm.asm.OpJump;
import org.xvm.asm.Scope;

import org.xvm.runtime.Frame;


/**
 * CATCH_END rel_addr ; finish an exception handler with a jump
 * <p/>
 * Each CATCH_END op must match up with a previous CATCH op.
 * <p/>
 * The CATCH_END op exits the scope and proceeds to the instruction at the location specified by
 * "rel_addr".
 */
public class CatchEnd
        extends OpJump
    {
    /**
     * Construct a CATCH_END op based on the destination Op.
     *
     * @param op  the Op to jump to when the handler completes
     */
    public CatchEnd(Op op)
        {
        super(op);
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
        super(in, aconst);
        }

    @Override
    public int getOpCode()
        {
        return OP_CATCH_END;
        }

    @Override
    public boolean isExit()
        {
        return true;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        frame.exitScope();
        return iPC + m_ofJmp;
        }

    @Override
    public void simulate(Scope scope)
        {
        scope.exit();
        }
    }