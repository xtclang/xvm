package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.OpVar;
import org.xvm.asm.Register;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * VAR_I TYPE, rvalue-src ; (next register is an initialized anonymous variable)
 */
public class Var_I
        extends OpVar
    {
    /**
     * Construct a VAR_I op for the specified register and argument.
     *
     * @param reg       the register
     * @param argValue  the value argument
     */
    public Var_I(Register reg, Argument argValue)
        {
        super(reg);

        if (argValue == null)
            {
            throw new IllegalArgumentException("value required");
            }
        m_argValue = argValue;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public Var_I(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);

        m_nValueId = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        super.write(out, registry);

        if (m_argValue != null)
            {
            m_nValueId = encodeArgument(m_argValue, registry);
            }

        writePackedLong(out, m_nValueId);
        }

    @Override
    public int getOpCode()
        {
        return OP_VAR_I;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            ObjectHandle hArg = frame.getArgument(m_nValueId);

            if (isDeferred(hArg))
                {
                return hArg.proceed(frame, frameCaller ->
                    {
                    frameCaller.introduceVar(m_nVar, convertId(m_nType),
                            0, Frame.VAR_STANDARD, frameCaller.popStack());
                    return iPC + 1;
                    });
                }

            frame.introduceVar(m_nVar, convertId(m_nType), 0, Frame.VAR_STANDARD, hArg);
            return iPC + 1;
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    @Override
    public void registerConstants(ConstantRegistry registry)
        {
        super.registerConstants(registry);

        m_argValue = registerArgument(m_argValue, registry);
        }

    @Override
    public String toString()
        {
        return super.toString()
                + ' ' + Argument.toIdString(m_argValue, m_nValueId);
        }

    private int m_nValueId;

    private Argument m_argValue;
    }