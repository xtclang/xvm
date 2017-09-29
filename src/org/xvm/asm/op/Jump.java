package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.runtime.Frame;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * The unconditional "jump" op code: JMP. (Also assembles to the GOTO op code if the absolute
 * address value for GOTO is smaller than the relative address value for JMP.)
 * <p/>
 * {@code JMP rel-addr}
 */
public class Jump
        extends Op
    {
    /**
     * Construct a JMP op.
     *
     * @param nRelAddr  the relative address to jump to.
     */
    public Jump(int nRelAddr)
        {
        m_ofJmp = nRelAddr;
        }

    /**
     * Construct a JMP op.
     *
     * @param op  the op to jump to
     */
    public Jump(Op op)
        {
        m_opDest = op;
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
        m_ofJmp = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        out.writeByte(getOpCode());
        writePackedLong(out, m_ofJmp);
        }

    @Override
    public int getOpCode()
        {
        return m_fUseAbsolute
                ? OP_GOTO
                : OP_JMP;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        return m_fUseAbsolute
                ? m_ofJmp
                : iPC + m_ofJmp;
        }

    @Override
    public void resolveAddress(MethodStructure.Code code, int iPC)
        {
        if (m_opDest != null && m_ofJmp == 0)
            {
            int iPCThat = code.addressOf(m_opDest);
            if (iPCThat < 0)
                {
                throw new IllegalStateException("cannot find op: " + m_opDest);
                }

            // calculate relative offset
            m_ofJmp = iPCThat - iPC;
            if (m_ofJmp == 0)
                {
                throw new IllegalStateException("infinite loop: " + this);
                }

            // see if absolute offset is smaller (and if so, use GOTO)
            if (iPCThat < Math.abs(m_ofJmp))
                {
                m_ofJmp        = iPCThat;
                m_fUseAbsolute = true;
                }
            }
        }

    private Op      m_opDest;
    private int     m_ofJmp;
    private boolean m_fUseAbsolute;
    }
