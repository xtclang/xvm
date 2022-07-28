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
        m_nOp    = nopForLines(cLines);
        m_cLines = cLines;
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
        m_cLines = readPackedInt(in);
        m_nOp    = nopForLines(m_cLines);
        }

    static int nopForLines(int cLines)
        {
        switch (cLines)
            {
            case 0:
                return OP_NOP;
            case 1:
                return OP_LINE_1;
            case 2:
                return OP_LINE_2;
            case 3:
                return OP_LINE_3;
            default:
                return OP_LINE_N;
            }
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        super.write(out, registry);

        if (getOpCode() == OP_LINE_N)
            {
            writePackedLong(out, m_cLines);
            }
        }

    @Override
    public int getOpCode()
        {
        return m_nOp;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        return frame.f_context.isDebuggerActive()
                ? frame.f_context.getDebugger().checkBreakPoint(frame, iPC)
                : iPC + 1;
        }

    @Override
    public boolean isNecessary()
        {
        // LINE_* is considered to be necessary, because its effect exists whether or not the iPC
        // ever passes through it; multiple LINE_* ops in dead code will be merged together, though
        return m_cLines != 0;
        }

    @Override
    public boolean checkRedundant(Op[] aop)
        {
        // multiple NOP/LINE_* ops can be combined
        // trailing NOP/LINE_* ops can be erased
        for (int iOp = getAddress() + 1, cOps = aop.length; iOp < cOps; ++iOp)
            {
            Op op = aop[iOp].ensureOp();
            if (op instanceof Nop nop)
                {
                m_cLines    += nop.m_cLines;
                m_nOp        = nopForLines(m_cLines);
                nop.m_cLines = 0;
                nop.m_nOp    = OP_NOP;
                }
            else if (m_cLines != 0)
                {
                // a LINE_* op is not redundant
                return false;
                }
            else
                {
                // we netted out to zero lines, which is redundant
                break;
                }
            }

        // either this is a 0-line NOP or a NOP past the last "real" op
        markRedundant();
        return true;
        }

    /**
     * @return the line count for this op
     */
    public int getLineCount()
        {
        return m_cLines;
        }

    @Override
    public String toString()
        {
        return m_nOp == OP_LINE_N ? toName(m_nOp) + ' ' + m_cLines : toName(m_nOp);
        }

    private int m_nOp;
    private int m_cLines;
    }