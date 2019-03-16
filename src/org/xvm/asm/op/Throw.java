package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.Op;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * THROW rvalue
 */
public class Throw
        extends Op
    {
    /**
     * Construct a THROW op for the passed argument.
     *
     * @param argValue  the throw value Argument
     */
    public Throw(Argument argValue)
        {
        if (argValue == null)
            {
            throw new IllegalArgumentException("argument required");
            }

        m_argValue = argValue;
        }
    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public Throw(DataInput in, Constant[] aconst)
            throws IOException
        {
        m_nArgValue = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        super.write(out, registry);

        if (m_argValue != null)
            {
            m_nArgValue = encodeArgument(m_argValue, registry);
            }

        writePackedLong(out, m_nArgValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_THROW;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            // there are no "const" exceptions
            ExceptionHandle hException = (ExceptionHandle) frame.getArgument(m_nArgValue);
            if (hException == null)
                {
                return R_REPEAT;
                }

            return frame.raiseException(hException);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    @Override
    public boolean advances()
        {
        return false;
        }

    @Override
    public String toString()
        {
        return super.toString() + " " + Argument.toIdString(m_argValue, m_nArgValue);
        }

    private int m_nArgValue;

    private Argument m_argValue; // never a Constant
    }
