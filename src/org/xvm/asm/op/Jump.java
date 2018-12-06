package org.xvm.asm.op;


import java.io.DataInput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.Op;

import org.xvm.asm.OpJump;
import org.xvm.runtime.Frame;


/**
 * JMP addr ; unconditional relative jump
 */
public class Jump
        extends OpJump
    {
    /**
     * Construct a JMP op.
     *
     * @param nRelAddr  the relative address to jump to
     *
     * @deprecated
     */
    public Jump(int nRelAddr)
        {
        super(null);

        m_ofJmp = nRelAddr;
        }

    /**
     * Construct a JMP op.
     *
     * @param op  the op to jump to
     */
    public Jump(Op op)
        {
        super(op);
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
        super(in, aconst);
        }

    @Override
    public int getOpCode()
        {
        return OP_JMP;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        return jump(frame, iPC + m_ofJmp, m_cExits);
        }
    }
