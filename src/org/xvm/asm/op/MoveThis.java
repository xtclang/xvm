package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.Op;
import org.xvm.asm.Scope;

import org.xvm.runtime.Frame;

import org.xvm.runtime.ObjectHandle.GenericHandle;

import org.xvm.runtime.template.xException;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * MOV_THIS #, lvalue-dest ; # (an inline unsigned byte) specifies the count of this-to-outer-this
 *                         ; steps (0=this, 1=ImmediatelyOuter.this, etc.)
 */
public class MoveThis
        extends Op
    {
    /**
     * Construct a MOV_THIS op for the passed arguments.
     *
     * @param cSteps   the count of this-to-outer-this steps (0=this, 1=ImmediatelyOuter.this, etc.)
     * @param argDest  the destination Argument
     */
    public MoveThis(int cSteps, Argument argDest)
        {
        assert cSteps >= 0;

        m_cSteps = cSteps;
        m_argTo = argDest;
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
        if (m_argTo != null)
            {
            m_nToValue = encodeArgument(m_argTo, registry);
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
        checkNextRegister(scope, m_argTo);
        }

    @Override
    public void registerConstants(ConstantRegistry registry)
        {
        m_argTo = registerArgument(m_argTo, registry);
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            GenericHandle hOuter = (GenericHandle) frame.getThis();
            for (int c = m_cSteps; c > 0; c--)
                {
                hOuter = (GenericHandle) hOuter.getField(GenericHandle.OUTER);
                }

            int nToValue = m_nToValue;
            if (frame.isNextRegister(nToValue))
                {
                frame.introduceResolvedVar(nToValue, hOuter.getType());
                }

            return frame.assignValue(nToValue, hOuter);
            }
        catch (ClassCastException | NullPointerException e)
            {
            return frame.raiseException(xException.makeHandle("Unknown outer"));
            }
        }

    @Override
    public String toString()
        {
        return super.toString()
                + " #" + m_cSteps
                + ", " + Argument.toIdString(m_argTo, m_nToValue);
        }

    protected int m_cSteps;
    protected int m_nToValue;

    private Argument m_argTo;
    }
