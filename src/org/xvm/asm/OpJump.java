package org.xvm.asm;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.op.Label;

import org.xvm.runtime.Frame;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Base class for JUMP, GUARD_E, CATCH_E and GUARDALL op-codes.
 */
public abstract class OpJump
        extends Op
    {
    /**
     * Construct an op.
     *
     * @param op the op to jump to
     */
    protected OpJump(Op op)
        {
        m_opDest = op;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    protected OpJump(DataInput in, Constant[] aconst)
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
    public void resolveAddress(MethodStructure.Code code, int iPC)
        {
        if (m_opDest != null && m_ofJmp == 0)
            {
            m_ofJmp = resolveAddress(code, iPC, m_opDest);
            }
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        throw new UnsupportedOperationException();
        }

    /**
     * @return a String to use for debugging to denote the destination of the jump
     */
    protected String getLabelDesc()
        {
        return getLabelDesc(m_opDest, m_ofJmp);
        }

    public static String getLabelDesc(Op opDest, int ofJmp)
        {
        if (opDest instanceof Label)
            {
            return ((Label) opDest).getName();
            }
        else if (ofJmp != 0)
            {
            return (ofJmp > 0 ? "+" : "") + ofJmp;
            }
        else if (opDest != null)
            {
            return "-> " + opDest;
            }
        else
            {
            return "???";
            }
        }

    @Override
    public String toString()
        {
        return toName(getOpCode()) + ' ' + getLabelDesc();
        }

    protected int m_ofJmp;

    private Op m_opDest;
    }
