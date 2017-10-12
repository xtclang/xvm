package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.Op;
import org.xvm.asm.Register;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.Type;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * CAST rvalue-src, lvalue-dest
 */
public class MoveCast
        extends Op
    {
    /**
     * Construct a CAST op for the passed arguments.
     *
     * @param argFrom  the Register to move from
     * @param regTo  the Register to move to
     */
    public MoveCast(Argument argFrom, Register regTo)
        {
        if (argFrom == null || regTo == null)
            {
            throw new IllegalArgumentException("arguments required");
            }
        m_argFrom = argFrom;
        m_regTo   = regTo;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public MoveCast(DataInput in, Constant[] aconst)
            throws IOException
        {
        m_nFromValue = readPackedInt(in);
        m_nToValue = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        if (m_argFrom != null)
            {
            m_nFromValue = encodeArgument(m_argFrom, registry);
            m_nToValue   = encodeArgument(m_regTo, registry);
            }

        out.writeByte(OP_CAST);
        writePackedLong(out, m_nFromValue);
        writePackedLong(out, m_nToValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_CAST;
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

            // TODO: cast implementation
            Type typeFrom = hValue.m_type;
            Type typeTo   = frame.getArgumentType(m_nToValue);

            return frame.assignValue(m_nToValue, hValue);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    @Override
    public void registerConstants(ConstantRegistry registry)
        {
        registerArgument(m_argFrom, registry);
        }

    private int m_nFromValue;
    private int m_nToValue;

    private Argument m_argFrom;
    private Register m_regTo;
    }
