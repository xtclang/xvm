package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.Op;
import org.xvm.asm.Register;
import org.xvm.asm.Scope;

import org.xvm.runtime.Frame;

import org.xvm.runtime.template.xRef.RefHandle;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * <pre><code>
 * MOV_THIS #, lvalue-dest ; # (an inline unsigned byte) specifies the count of this-to-outer-this
 *                         ; steps (0=this, 1=ImmediatelyOuter.this, etc.)
 * </code></pre>
 */
public class MoveThis
        extends Op
    {
    /**
     * Construct a MOV_THIS op for the passed arguments.
     *
     * @param cSteps   the count of this-to-outer-this steps (0=this, 1=ImmediatelyOuter.this, etc.)
     * @param regDest  the destination Register
     */
    public MoveThis(int cSteps, Register regDest)
        {
        m_cSteps = cSteps;
        m_regTo  = regDest;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public MoveThis(DataInput in, Constant[] aconst)
            throws IOException
        {
        m_cSteps   = in.readUnsignedByte();
        m_nToValue = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        if (m_regTo != null)
            {
            m_nToValue = encodeArgument(m_regTo, registry);
            }

        out.writeByte(getOpCode());
        out.writeByte(m_cSteps);
        writePackedLong(out, m_nToValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_MOV_THIS;
        }

    @Override
    public void simulate(Scope scope)
        {
        checkNextRegister(scope, m_regTo);
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        RefHandle hRef = null; // TODO m_cSteps==0 -> this; m_cSteps==1 -> Outer.this; etc.

        if (frame.isNextRegister(m_nToValue))
            {
            // TODO frame.introduceResolvedVar(clzRef.getType());
            }

        // the destination type must be the same as the source
        frame.f_ahVar[m_nToValue] = hRef;

        return iPC + 1;
        }

    protected int m_cSteps;
    protected int m_nToValue;

    private Register m_regTo;
    }
