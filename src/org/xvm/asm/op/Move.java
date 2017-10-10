package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.Op;
import org.xvm.asm.Register;
import org.xvm.asm.Scope;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * MOV rvalue-src, lvalue-dest
 */
public class Move
        extends Op
    {
    /**
     * Construct a MOV op.
     *
     * @param nFrom  the source location
     * @param nTo    the destination location
     *
     * @deprecated
     */
    public Move(int nFrom, int nTo)
        {
        m_nToValue = nTo;
        m_nFromValue = nFrom;
        }

    /**
     * Construct a MOV op for the passed Registers.
     *
     * @param regFrom  the Register to move from
     * @param regTo  the Register to move to
     */
    public Move(Register regFrom, Register regTo)
        {
        if (regFrom == null || regTo == null)
            {
            throw new IllegalArgumentException("registers required");
            }
        m_regFrom = regFrom;
        m_regTo   = regTo;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public Move(DataInput in, Constant[] aconst)
            throws IOException
        {
        m_nFromValue = readPackedInt(in);
        m_nToValue = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        if (m_regFrom != null)
            {
            m_nFromValue = encodeArgument(m_regFrom.getType(), registry);
            m_nToValue   = encodeArgument(m_regTo.getType(), registry);
            }

        out.writeByte(OP_MOV);
        writePackedLong(out, m_nFromValue);
        writePackedLong(out, m_nToValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_MOV;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            ObjectHandle hValue = frame.getArgument(m_nFromValue);
            if (hValue == null)
                {
                return R_REPEAT;
                }

            if (frame.isNextRegister(m_nToValue))
                {
                frame.copyVarInfo(m_nFromValue);
                }
            return frame.assignValue(m_nToValue, hValue);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    @Override
    public void simulate(Scope scope)
        {
        if (scope.isNextRegister(m_nToValue))
            {
            scope.allocVar();
            }
        }

    private int m_nFromValue;
    private int m_nToValue;

    private Register m_regFrom;
    private Register m_regTo;
    }
